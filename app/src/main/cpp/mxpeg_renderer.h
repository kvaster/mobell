#ifndef __MXPEG_RENDERER__
#define __MXPEG_RENDERER__

extern "C"
{
    #include <libavcodec/avcodec.h>
}

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include <pthread.h>
#include <atomic>

#include "audio_queue.h"

class MxpegRenderer
{
public:
    MxpegRenderer();
    ~MxpegRenderer();

    void suspend();
    void resume();

    void update();
    void draw(float scale, float panX, float panY);

    void canvasSizeChanged(int width, int height);

    void onStreamStart(int audioType);
    void onStreamStop();
    bool onStreamVideoPacket(uint8_t* data, size_t size);
    bool onStreamAudioPacket(uint8_t* data, size_t size);

    static const int AUDIO_ALAW = 0;
    static const int AUDIO_PCM16 = 1;

private:

    static const int QUEUE_BUFFERS = 8;

    GLuint program;
    GLuint yTex;
    GLuint uTex;
    GLuint vTex;
    GLuint vbo;

    GLuint scaleAttr;
    GLuint posAttr;

    int canvasWidth;
    int canvasHeight;

    int width;
    int height;

    std::atomic_bool gotVideo;
    pthread_mutex_t videoMutex;

    void resetGl();
    void updateTextures();

    // FFmpeg video
    AVCodec* videoCodec;
    AVCodecContext* videoCodecCtx;
    AVFrame* videoFrame;
    AVFrame* videoWorkFrame;

    // FFmpeg audio
    AVCodec* audioCodec;
    AVCodecContext* audioCodecCtx;
    AVFrame* audioWorkFrame;

    AudioBufferStack audioBuffers;
    AudioBufferQueue playingAudioBuffers;

    // sound engine, player and buffer
    SLObjectItf audioEngineObj;
    SLEngineItf audioEngine;
    SLObjectItf audioOutputMix;
    SLObjectItf audioPlayerObj;
    SLPlayItf audioPlayer;
    SLAndroidSimpleBufferQueueItf audioBufferQueue;

    int audioType;

    void enqueueAudio(void *buf, SLuint32 size);

public:
    void playerCallback(SLAndroidSimpleBufferQueueItf bq);
};

#endif //__MXPEG_RENDERER__
