#include "mxpeg_renderer.h"

static GLuint compileShader(const char* source, GLenum type)
{
    GLuint handle = glCreateShader(type);
    glShaderSource(handle, 1, &source, nullptr);
    glCompileShader(handle);

//    GLchar log[512];
//    GLsizei len = -1;
//    glGetShaderInfoLog(handle, sizeof(log), &len, log);
//    const char* l = (const char*)&log;

    return handle;
}

static GLuint compileVertexShader(const char* source)
{
    return compileShader(source, GL_VERTEX_SHADER);
}

static GLuint compileFragmentShader(const char* source)
{
    return compileShader(source, GL_FRAGMENT_SHADER);
}

/////////////////////////////////////////////////

MxpegRenderer::MxpegRenderer()
{
    pthread_mutex_init(&frameMutex, nullptr);
    gotFrame = false;

    avcodec_register_all();

    codec = avcodec_find_decoder(AV_CODEC_ID_MXPEG);
    codecCtx = nullptr;
    frame = av_frame_alloc();
    workFrame = av_frame_alloc();

//    slCreateEngine(&slEngineObj, 0, nullptr, 0, nullptr, nullptr);
//    (*slEngineObj)->Realize(slEngineObj, SL_BOOLEAN_FALSE);
//    (*slEngineObj)->GetInterface(slEngineObj, SL_IID_ENGINE, &slEngineObj);
//
//    (*slEngine)->CreateOutputMix(slEngine, &slOutputMix, 0, nullptr, nullptr);
//    (*slOutputMix)->Realize(slOutputMix, SL_BOOLEAN_FALSE);

    resume();
}

MxpegRenderer::~MxpegRenderer()
{
    avcodec_free_context(&codecCtx);
    av_frame_free(&frame);
    av_frame_free(&workFrame);

    pthread_mutex_destroy(&frameMutex);
}

void MxpegRenderer::resetGl()
{
    program = GL_NONE;
    yTex = GL_NONE;
    uTex = GL_NONE;
    vTex = GL_NONE;
    vbo = GL_NONE;
}

void MxpegRenderer::suspend()
{
    glDeleteProgram(program);
    glDeleteTextures(1, &yTex);
    glDeleteTextures(1, &uTex);
    glDeleteTextures(1, &vTex);
    glDeleteBuffers(1, &vbo);

    resetGl();
}

void MxpegRenderer::resume()
{
    /// буферы
    glGenBuffers(1, &vbo);

    GLfloat data[] = {
            -1,  1,  0, 0, // top left
            1,  1,  1, 0, // top right
            -1, -1,  0, 1, // bottom left
            1,  1,  1, 0, // top right
            -1, -1,  0, 1, // bottom left
            1, -1,  1, 1  // bottom right
    };

    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(data), data, GL_STATIC_DRAW);

    /// shaders

    static const char* FRAG_SHADER_SOURCE = R"glsl(
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

    static const char* VERT_SHADER_SOURCE = R"glsl(
            #ifdef GL_ES
            precision mediump float;
            #endif

            attribute vec2 a_position_0;
            attribute vec2 a_texcoord_0;

            varying vec2 texCoord;

            void main()
            {
                texCoord = a_texcoord_0;
                gl_Position = vec4(a_position_0, 0.0, 1.0);
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

    GLint vertAttr = glGetAttribLocation(program, "a_position_0");
    glEnableVertexAttribArray((GLuint)vertAttr);
    glVertexAttribPointer((GLuint)vertAttr, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), (void*)(0 * sizeof(GLfloat)));

    GLint texAttr = glGetAttribLocation(program, "a_texcoord_0");
    glEnableVertexAttribArray((GLuint)texAttr);
    glVertexAttribPointer((GLuint)texAttr, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), (void*)(2 * sizeof(GLfloat)));

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

void MxpegRenderer::canvasSizeChanged(int width, int height)
{
    glViewport(0, 0, width, height);
}

void MxpegRenderer::onStreamStart()
{
    codecCtx = avcodec_alloc_context3(codec);
    avcodec_open2(codecCtx, codec, nullptr);
}

void MxpegRenderer::onStreamStop()
{
    avcodec_free_context(&codecCtx);
}

void MxpegRenderer::onStreamVideoPacket(uint8_t* data, size_t size)
{
    AVPacket pkt;
    av_init_packet(&pkt);

    pkt.data = data;
    pkt.size = size;

    avcodec_send_packet(codecCtx, &pkt);

    pthread_mutex_lock(&frameMutex);

    bool got = false;
    int ret = 0;
    while (ret >= 0)
    {
        ret = avcodec_receive_frame(codecCtx, workFrame);

        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            break;

        if (ret < 0) // error
            break;

        av_frame_unref(frame);
        av_frame_ref(frame, workFrame);
        got = true;
    }

    pthread_mutex_unlock(&frameMutex);

    if (got)
        gotFrame = true;
}

void MxpegRenderer::update()
{
    bool got = true;
    if (gotFrame.compare_exchange_strong(got, false))
        updateTextures();
}

void MxpegRenderer::draw()
{
    glClearColor(0.5f, 0.5f, 0.5f, 0);
    glClear(GL_COLOR_BUFFER_BIT);

    if (width > 0 && height > 0)
    {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, yTex);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, uTex);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, vTex);

        glEnable(GL_TEXTURE_2D);
        glUseProgram(program);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
}

void MxpegRenderer::updateTextures()
{
    pthread_mutex_lock(&frameMutex);

    if (frame->width > 0 && frame->height > 0)
    {
        width = frame->width;
        height = frame->height;

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, yTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width, height, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, frame->data[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, uTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width / 2, height / 2, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, frame->data[1]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, vTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width / 2, height / 2, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, frame->data[2]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    else
    {
        width = height = 0;
    }

    pthread_mutex_unlock(&frameMutex);
}

void MxpegRenderer::onStreamAudioPacket(uint8_t *data, size_t size)
{
    // TODO
}
