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

class MxpegRenderer
{
public:
    MxpegRenderer();
    ~MxpegRenderer();

    void suspend();
    void resume();

    void update();
    void draw();

    void canvasSizeChanged(int width, int height);

    void onStreamStart();
    void onStreamStop();
    void onStreamVideoPacket(uint8_t* data, size_t size);
    void onStreamAudioPacket(uint8_t* data, size_t size);

private:
    GLuint program;
    GLuint yTex;
    GLuint uTex;
    GLuint vTex;
    GLuint vbo;

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
    AVFrame* audioFrame;
    AVFrame* audioWorkFrame;
    AVFrame* audioEnqueueFrame;

    // sound engine, player and buffer
    SLObjectItf audioEngineObj;
    SLEngineItf audioEngine;
    SLObjectItf audioOutputMix;
    SLObjectItf audioPlayerObj;
    SLPlayItf audioPlayer;
    SLAndroidSimpleBufferQueueItf audioBufferQueue;

    volatile bool gotAudio;
    volatile bool audioEnqueued;
    pthread_mutex_t audioMutex;

    void enqueueAudio();

public:
    void playerCallback(SLAndroidSimpleBufferQueueItf bq);
};

#endif //__MXPEG_RENDERER__
