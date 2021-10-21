#ifndef __AUDIO_RECORDER__
#define __AUDIO_RECORDER__

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "audio_queue.h"

typedef void (AudioRecorderListener)(AudioBuffer *);

class AudioRecorder {
public:
    AudioRecorder(AudioRecorderListener *callback);

    ~AudioRecorder();

    void start();

    void stop();

private:
    static const int QUEUE_BUFFERS = 32;
    static const int QUEUE_BUFFER_SIZE = 512;

    AudioRecorderListener *callback;

    SLObjectItf audioEngineObj;
    SLEngineItf audioEngine;
    SLObjectItf audioRecorderObj;
    SLRecordItf audioRecorder;
    SLAndroidSimpleBufferQueueItf audioBufferQueue;

    AudioBufferQueue recordingQueue;
    AudioBufferStack idleQueue;

public:
    void playerCallback(SLAndroidSimpleBufferQueueItf bq);
};

#endif // __AUDIO_RECORDER__
