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

    std::atomic_bool gotFrame;
    pthread_mutex_t frameMutex;

    void resetGl();
    void updateTextures();

    // FFmpeg video
    AVCodec* codec;
    AVCodecContext* codecCtx;
    AVFrame* frame;
    AVFrame* workFrame;

    // FFmpeg audio
    AVCodec* audioCodec;
    AVCodecContext* audioCodecCtx;
    AVFrame* audioFrame;

    // sound
    SLObjectItf slEngineObj;
    SLEngineItf slEngine;
    SLObjectItf slOutputMix;
    SLObjectItf slPlayerObj;
    SLPlayItf slPlayer;
};

#endif //__MXPEG_RENDERER__
