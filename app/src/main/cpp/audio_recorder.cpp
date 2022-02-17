#include "audio_recorder.h"

static void slesPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    ((AudioRecorder *) context)->playerCallback(bq);
}

AudioRecorder::AudioRecorder(AudioRecorderListener *callback) {
    this->callback = callback;

    SLresult result;

    audioEngineObj = nullptr;
    audioEngine = nullptr;

    audioRecorderObj = nullptr;
    audioRecorder = nullptr;
    audioBufferQueue = nullptr;

    slCreateEngine(&audioEngineObj, 0, nullptr, 0, nullptr, nullptr);
    (*audioEngineObj)->Realize(audioEngineObj, SL_BOOLEAN_FALSE);
    (*audioEngineObj)->GetInterface(audioEngineObj, SL_IID_ENGINE, &audioEngine);

    SLDataLocator_IODevice locDev = {
            SL_DATALOCATOR_IODEVICE,
            SL_IODEVICE_AUDIOINPUT,
            SL_DEFAULTDEVICEID_AUDIOINPUT,
            nullptr
    };

    SLDataSource audioSrc = {&locDev, nullptr};

    SLDataLocator_AndroidSimpleBufferQueue locBufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                      QUEUE_BUFFERS};

    SLAndroidDataFormat_PCM_EX formatPcm = {
            SL_DATAFORMAT_PCM,
            1,
            SL_SAMPLINGRATE_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_CENTER,
            SL_BYTEORDER_LITTLEENDIAN,
            SL_ANDROID_PCM_REPRESENTATION_UNSIGNED_INT
    };

    SLDataSink audioSnk = {&locBufq, &formatPcm};

    const SLInterfaceID ids[2] = {
            SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
            SL_IID_ANDROIDCONFIGURATION
    };

    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    (*audioEngine)->CreateAudioRecorder(audioEngine,
                                        &audioRecorderObj,
                                        &audioSrc,
                                        &audioSnk,
                                        sizeof(ids) / sizeof(ids[0]),
                                        ids, req);

    // Configure voice communication preset
    SLAndroidConfigurationItf inputConfig;
    result = (*audioRecorderObj)->GetInterface(audioRecorderObj, SL_IID_ANDROIDCONFIGURATION,
                                               &inputConfig);
    if (SL_RESULT_SUCCESS == result) {
        SLuint32 presetValue = SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION;
        (*inputConfig)->SetConfiguration(inputConfig,
                                         SL_ANDROID_KEY_RECORDING_PRESET,
                                         &presetValue,
                                         sizeof(SLuint32));
    }

    (*audioRecorderObj)->Realize(audioRecorderObj, SL_BOOLEAN_FALSE);
    (*audioRecorderObj)->GetInterface(audioRecorderObj, SL_IID_RECORD, &audioRecorder);
    (*audioRecorderObj)->GetInterface(audioRecorderObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                      &audioBufferQueue);
    (*audioBufferQueue)->RegisterCallback(audioBufferQueue, slesPlayerCallback, this);

    for (int i = 0; i < QUEUE_BUFFERS; i++) {
        auto *b = new AudioBuffer(QUEUE_BUFFER_SIZE);
        idleQueue.put(b);
    }
}

AudioRecorder::~AudioRecorder() {
    stop();

    if (audioEngineObj) {
        (*audioEngineObj)->Destroy(audioEngineObj);
        audioEngineObj = nullptr;
        audioEngineObj = nullptr;
    }

    if (audioRecorderObj) {
        (*audioRecorderObj)->Destroy(audioRecorderObj);
        audioRecorderObj = nullptr;
        audioRecorder = nullptr;
        audioBufferQueue = nullptr;
    }
}

void AudioRecorder::playerCallback(SLAndroidSimpleBufferQueueItf bq) {
    AudioBuffer *b = recordingQueue.get();
    // Buffer should be always available. If no buffer -> fatal error.
    if (b) {
        b->size = b->capacity;
        (*callback)(b);
        recordingQueue.put(b);
        (*audioBufferQueue)->Enqueue(audioBufferQueue, b->buffer, b->capacity);
    }
}

void AudioRecorder::start() {
    stop();

    AudioBuffer *b;
    while ((b = idleQueue.get()) != nullptr) {
        recordingQueue.put(b);
        (*audioBufferQueue)->Enqueue(audioBufferQueue, b->buffer, b->capacity);
    }

    (*audioRecorder)->SetRecordState(audioRecorder, SL_RECORDSTATE_RECORDING);
}

void AudioRecorder::stop() {
    (*audioRecorder)->SetRecordState(audioRecorder, SL_RECORDSTATE_STOPPED);
    (*audioBufferQueue)->Clear(audioBufferQueue);

    AudioBuffer *b;
    while ((b = recordingQueue.get()) != nullptr)
        idleQueue.put(b);
}
