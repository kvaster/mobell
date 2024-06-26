#include "mxpeg_renderer.h"

static GLuint compileShader(const char *source, GLenum type) {
    GLuint handle = glCreateShader(type);
    glShaderSource(handle, 1, &source, nullptr);
    glCompileShader(handle);

//    GLchar log[512];
//    GLsizei len = -1;
//    glGetShaderInfoLog(handle, sizeof(log), &len, log);
//    const char* l = (const char*)&log;

    return handle;
}

static GLuint compileVertexShader(const char *source) {
    return compileShader(source, GL_VERTEX_SHADER);
}

static GLuint compileFragmentShader(const char *source) {
    return compileShader(source, GL_FRAGMENT_SHADER);
}

static void slesPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    ((MxpegRenderer *) context)->playerCallback(bq);
}

/////////////////////////////////////////////////

MxpegRenderer::MxpegRenderer() {
    // Video
    pthread_mutex_init(&videoMutex, nullptr);
    gotVideo = false;

    // FFmpeg video
    videoCodec = avcodec_find_decoder(AV_CODEC_ID_MXPEG);
    videoCodecCtx = nullptr;
    videoFrame = av_frame_alloc();
    videoWorkFrame = av_frame_alloc();
    videoPkt = av_packet_alloc();

    // Audio
    audioCodec = avcodec_find_decoder(AV_CODEC_ID_PCM_ALAW);
    audioCodecCtx = nullptr;
    audioWorkFrame = av_frame_alloc();
    audioPkt = av_packet_alloc();

    for (int i = 0; i < QUEUE_BUFFERS; i++) {
        auto *b = new AudioBuffer(16 * 1024);
        audioBuffers.put(b);
    }

    audioEngineObj = nullptr;
    audioEngine = nullptr;
    audioOutputMix = nullptr;
    audioPlayerObj = nullptr;
    audioPlayer = nullptr;
    audioBufferQueue = nullptr;

    resume();
}

MxpegRenderer::~MxpegRenderer() {
    onStreamStop();

    av_frame_free(&audioWorkFrame);
    av_packet_free(&audioPkt);

    // video
    av_frame_free(&videoFrame);
    av_frame_free(&videoWorkFrame);
    av_packet_free(&videoPkt);

    pthread_mutex_destroy(&videoMutex);
}

void MxpegRenderer::resetGl() {
    program = GL_NONE;
    yTex = GL_NONE;
    uTex = GL_NONE;
    vTex = GL_NONE;
    vbo = GL_NONE;
}

void MxpegRenderer::suspend() {
    glDeleteProgram(program);
    glDeleteTextures(1, &yTex);
    glDeleteTextures(1, &uTex);
    glDeleteTextures(1, &vTex);
    glDeleteBuffers(1, &vbo);

    resetGl();
}

void MxpegRenderer::resume() {
    /// буферы
    glGenBuffers(1, &vbo);

    GLfloat data[] = {
            -1, 1, 0, 0, // top left
            1, 1, 1, 0, // top right
            -1, -1, 0, 1, // bottom left
            1, 1, 1, 0, // top right
            -1, -1, 0, 1, // bottom left
            1, -1, 1, 1  // bottom right
    };

    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(data), data, GL_STATIC_DRAW);

    /// shaders

    static const char *FRAG_SHADER_SOURCE = R"glsl(
            #ifdef GL_ES
            precision mediump float;
            #endif

            uniform sampler2D yTex;
            uniform sampler2D uTex;
            uniform sampler2D vTex;

            varying vec2 texCoord;

            void main()
            {
                vec3 yuv, rgb;

                yuv.x = texture2D(yTex, texCoord).r;
                yuv.y = texture2D(uTex, texCoord).r - 0.5;
                yuv.z = texture2D(vTex, texCoord).r - 0.5;

                rgb = mat3(    1.0,     1.0,     1.0,
                                .0, -.34414, 1.77200,
                           1.40200, -.71414,      .0) * yuv;

                gl_FragColor = vec4(rgb, 1.0);
            }
    )glsl";

    static const char *VERT_SHADER_SOURCE = R"glsl(
            #ifdef GL_ES
            precision mediump float;
            #endif

            attribute vec2 a_position_0;
            attribute vec2 a_texcoord_0;
            attribute vec2 p_scale;
            attribute vec2 p_pos;

            varying vec2 texCoord;

            void main()
            {
                texCoord = a_texcoord_0;
                gl_Position = vec4(a_position_0 * p_scale + p_pos, 0.0, 1.0);
            }
    )glsl";

    program = glCreateProgram();

    GLuint vertShader = compileVertexShader(VERT_SHADER_SOURCE);
    GLuint fragShader = compileFragmentShader(FRAG_SHADER_SOURCE);

    glAttachShader(program, vertShader);
    glAttachShader(program, fragShader);

    glLinkProgram(program);

    glDetachShader(program, vertShader);
    glDetachShader(program, fragShader);

    glDeleteShader(vertShader);
    glDeleteShader(fragShader);

//    GLchar log[512];
//    GLsizei len = -1;
//    glGetProgramInfoLog(program, sizeof(log), &len, log);

    glUseProgram(program);

    vertAttr = (GLuint) glGetAttribLocation(program, "a_position_0");
    texAttr = (GLuint) glGetAttribLocation(program, "a_texcoord_0");
    scaleAttr = (GLuint) glGetAttribLocation(program, "p_scale");
    posAttr = (GLuint) glGetAttribLocation(program, "p_pos");

    /// textures
    glGenTextures(1, &yTex);
    glGenTextures(1, &uTex);
    glGenTextures(1, &vTex);

    GLint yTexIdx = glGetUniformLocation(program, "yTex");
    GLint uTexIdx = glGetUniformLocation(program, "uTex");
    GLint vTexIdx = glGetUniformLocation(program, "vTex");

    glUniform1i(yTexIdx, 0);
    glUniform1i(uTexIdx, 1);
    glUniform1i(vTexIdx, 2);

    updateTextures();
}

void MxpegRenderer::canvasSizeChanged(int width, int height) {
    canvasWidth = width;
    canvasHeight = height;
}

void MxpegRenderer::onStreamStart(int audioType) {
    pthread_mutex_lock(&videoMutex);
    width = height = 0;
    pthread_mutex_unlock(&videoMutex);

    videoCodecCtx = avcodec_alloc_context3(videoCodec);
    avcodec_open2(videoCodecCtx, videoCodec, nullptr);
    gotVideo = false;

    audioCodecCtx = avcodec_alloc_context3(audioCodec);
    // mono, 8khz
    audioCodecCtx->sample_rate = 8000;
    audioCodecCtx->channels = 1;
    avcodec_open2(audioCodecCtx, audioCodec, nullptr);

    // audio engine
    this->audioType = audioType;

    slCreateEngine(&audioEngineObj, 0, nullptr, 0, nullptr, nullptr);
    (*audioEngineObj)->Realize(audioEngineObj, SL_BOOLEAN_FALSE);
    (*audioEngineObj)->GetInterface(audioEngineObj, SL_IID_ENGINE, &audioEngine);

    (*audioEngine)->CreateOutputMix(audioEngine, &audioOutputMix, 0, nullptr, nullptr);
    (*audioOutputMix)->Realize(audioOutputMix, SL_BOOLEAN_FALSE);

    // Audio player & sound buffer

    SLDataLocator_AndroidSimpleBufferQueue locBufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                      QUEUE_BUFFERS};

    SLDataFormat_PCM formatPcm = {
            SL_DATAFORMAT_PCM,
            1,
            audioType == AUDIO_ALAW ? SL_SAMPLINGRATE_8 : SL_SAMPLINGRATE_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_ANDROID_SPEAKER_USE_DEFAULT,
            SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource audioSrc = {&locBufq, &formatPcm};
    SLDataLocator_OutputMix locOutmix = {SL_DATALOCATOR_OUTPUTMIX, audioOutputMix};
    SLDataSink audioSnk = {&locOutmix, nullptr};

    const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    (*audioEngine)->CreateAudioPlayer(audioEngine, &audioPlayerObj, &audioSrc, &audioSnk, 2, ids,
                                      req);
    (*audioPlayerObj)->Realize(audioPlayerObj, SL_BOOLEAN_FALSE);
    (*audioPlayerObj)->GetInterface(audioPlayerObj, SL_IID_PLAY, &audioPlayer);
    (*audioPlayerObj)->GetInterface(audioPlayerObj, SL_IID_BUFFERQUEUE, &audioBufferQueue);
    (*audioBufferQueue)->RegisterCallback(audioBufferQueue, slesPlayerCallback, this);

    // and play!
    (*audioBufferQueue)->Clear(audioBufferQueue);
    (*audioPlayer)->SetPlayState(audioPlayer, SL_PLAYSTATE_PLAYING);
}

void MxpegRenderer::onStreamStop() {
    pthread_mutex_lock(&videoMutex);
    width = height = 0;
    av_frame_unref(videoFrame);
    pthread_mutex_unlock(&videoMutex);

    if (videoCodecCtx)
        avcodec_free_context(&videoCodecCtx);
    if (audioCodecCtx)
        avcodec_free_context(&audioCodecCtx);

    // audio
    if (audioPlayer)
        (*audioPlayer)->SetPlayState(audioPlayer, SL_PLAYSTATE_STOPPED);
    if (audioBufferQueue)
        (*audioBufferQueue)->Clear(audioBufferQueue);

    AudioBuffer *b;
    while ((b = playingAudioBuffers.get()) != nullptr)
        audioBuffers.put(b);

    if (audioPlayerObj) {
        (*audioPlayerObj)->Destroy(audioPlayerObj);
        audioPlayerObj = nullptr;
        audioPlayer = nullptr;
        audioBufferQueue = nullptr;
    }

    if (audioOutputMix) {
        (*audioOutputMix)->Destroy(audioOutputMix);
        audioOutputMix = nullptr;
    }

    if (audioEngineObj) {
        (*audioEngineObj)->Destroy(audioEngineObj);
        audioEngineObj = nullptr;
        audioEngine = nullptr;
    }
}

int MxpegRenderer::onStreamVideoPacket(uint8_t *data, size_t size) {
    videoPkt->data = data;
    videoPkt->size = size;

    avcodec_send_packet(videoCodecCtx, videoPkt);

    pthread_mutex_lock(&videoMutex);

    bool ok = true;
    bool got = false;
    int ret = 0;
    while (true) {
        ret = avcodec_receive_frame(videoCodecCtx, videoWorkFrame);

        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            break;

        if (ret < 0) // error
        {
            ok = false;
            break;
        }

        av_frame_unref(videoFrame);
        av_frame_ref(videoFrame, videoWorkFrame);
        got = true;
    }

    pthread_mutex_unlock(&videoMutex);

    if (got)
        gotVideo = true;

    return ok ? (got ? 1 : 0) : -1;
}

void MxpegRenderer::update() {
    bool got = true;
    if (gotVideo.compare_exchange_strong(got, false))
        updateTextures();
}

void MxpegRenderer::draw(float scale, float panX, float panY) {
    if (width > 0 && height > 0 && canvasWidth > 0 && canvasHeight > 0) {
        glViewport(0, 0, canvasWidth, canvasHeight);

        glClearColor(0.f, 0.f, 0.f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(program);
        glEnable(GL_TEXTURE_2D);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, yTex);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, uTex);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, vTex);

        float scaleX;
        float scaleY;

        float canvasRatio = (float) canvasWidth / canvasHeight;
        float imgRatio = (float) width / height;

        if (imgRatio > canvasRatio) {
            scaleX = 1.0f / canvasRatio * imgRatio;
            scaleY = 1;
        } else {
            scaleX = 1;
            scaleY = 1.0f / imgRatio * canvasRatio;
        }

        glVertexAttrib2f(scaleAttr, scaleX * scale, scaleY * scale);
        glVertexAttrib2f(posAttr, panX * 2 / canvasWidth, -panY * 2 / canvasHeight);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glEnableVertexAttribArray(vertAttr);
        glVertexAttribPointer(vertAttr, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat),
                              (void *) (0 * sizeof(GLfloat)));
        glEnableVertexAttribArray(texAttr);
        glVertexAttribPointer(texAttr, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat),
                              (void *) (2 * sizeof(GLfloat)));

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(vertAttr);
        glDisableVertexAttribArray(texAttr);
        glDisable(GL_TEXTURE_2D);
    } else {
        glViewport(0, 0, canvasWidth, canvasHeight);

        glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}

void MxpegRenderer::updateTextures() {
    pthread_mutex_lock(&videoMutex);

    if (videoFrame->width > 0 && videoFrame->height > 0) {
        width = videoFrame->width;
        height = videoFrame->height;

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, yTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width, height, 0, GL_LUMINANCE,
                     GL_UNSIGNED_BYTE, videoFrame->data[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, uTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width / 2, height / 2, 0, GL_LUMINANCE,
                     GL_UNSIGNED_BYTE, videoFrame->data[1]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, vTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width / 2, height / 2, 0, GL_LUMINANCE,
                     GL_UNSIGNED_BYTE, videoFrame->data[2]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    } else {
        width = height = 0;
    }

    pthread_mutex_unlock(&videoMutex);
}

bool MxpegRenderer::onStreamAudioPacket(uint8_t *data, size_t size) {
    if (audioType == AUDIO_ALAW) {
        audioPkt->data = data;
        audioPkt->size = size;

        avcodec_send_packet(audioCodecCtx, audioPkt);

        bool ok = true;
        int ret = 0;
        while (ret >= 0) {
            ret = avcodec_receive_frame(audioCodecCtx, audioWorkFrame);

            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
                break;

            if (ret < 0) // error
            {
                ok = false;
                break;
            }

            enqueueAudio(audioWorkFrame->data[0], (SLuint32) audioWorkFrame->nb_samples * 2);
        }

        return ok;
    } else {
        enqueueAudio(data, (SLuint32) size);
        return true;
    }
}

void MxpegRenderer::enqueueAudio(void *buf, SLuint32 size) {
    AudioBuffer *b = audioBuffers.get();
    if (b) {
        playingAudioBuffers.put(b);
        b->set(buf, size);

        (*audioBufferQueue)->Enqueue(audioBufferQueue, b->buffer, b->size);
    }
}

void MxpegRenderer::playerCallback(SLAndroidSimpleBufferQueueItf bq) {
    AudioBuffer *b = playingAudioBuffers.get();
    if (b) {
        audioBuffers.put(b);
    } else {
        // should NOT happen
    }
}
