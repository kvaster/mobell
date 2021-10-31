// Native...

#include "com_kvaster_mobell_MxpegNative.h"

#include "mxpeg_renderer.h"
#include "audio_recorder.h"

static MxpegRenderer *renderer = nullptr;
static AudioRecorder *recorder = nullptr;

static JavaVM *globalJvm = nullptr;
static jclass recorderListenerClass;
static jmethodID methodOnAudioData;

static jobject recorderListener = nullptr;

static JNIEnv *getEnv() {
    JNIEnv *env = nullptr;
    if (globalJvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK)
        globalJvm->AttachCurrentThread(&env, nullptr);
    return env;
}

static void onAudioData(AudioBuffer *buffer) {
    // TODO probably this is not optimal for Android's GC, but we can change it later
    if (recorderListener) {
        JNIEnv *env = getEnv();
        jbyteArray arr = env->NewByteArray(buffer->size);
        env->SetByteArrayRegion(arr, 0, buffer->size, (const jbyte *) buffer->buffer);
        env->CallVoidMethod(recorderListener, methodOnAudioData, arr);
        env->DeleteLocalRef(arr);
    }
}

////////////////////////////////////////////

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    globalJvm = vm;
    JNIEnv *env = getEnv();
    if (!env)
        return -1;

    recorderListenerClass = reinterpret_cast<jclass>(env->NewGlobalRef(
            env->FindClass("com/kvaster/mobell/AudioRecorderListener")));
    methodOnAudioData = env->GetMethodID(recorderListenerClass, "onAudioData", "([B)V");

    return JNI_VERSION_1_6;
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_start(JNIEnv *env, jclass c) {
    if (renderer == nullptr) {
        renderer = new MxpegRenderer();
    }
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_stop(JNIEnv *env, jclass c) {
    delete renderer;
    renderer = nullptr;
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_suspend(JNIEnv *env, jclass c) {
    if (renderer)
        renderer->suspend();
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_resume(JNIEnv *env, jclass c) {
    if (renderer)
        renderer->resume();
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_update(JNIEnv *env, jclass c) {
    if (renderer)
        renderer->update();
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_draw(
        JNIEnv *env, jclass c, jfloat scale, jfloat panX, jfloat panY
) {
    if (renderer)
        renderer->draw(scale, panX, panY);
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_canvasSizeChanged(
        JNIEnv *env, jclass c, jint width, jint height
) {
    if (renderer)
        renderer->canvasSizeChanged(width, height);
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStart(JNIEnv *env, jclass c) {
    if (renderer)
        renderer->onStreamStart(MxpegRenderer::AUDIO_PCM16);
}

extern "C" void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStop(JNIEnv *env, jclass c) {
    if (renderer)
        renderer->onStreamStop();
}

extern "C" jint JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamVideoPacket(
        JNIEnv *env, jclass c, jobject buffer, jint size
) {
    if (renderer)
        return (jint) renderer->onStreamVideoPacket((uint8_t *) env->GetDirectBufferAddress(buffer),
                                                    (size_t) size);
    return -1;
}

extern "C" jboolean JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamAudioPacket(
        JNIEnv *env, jclass c, jobject buffer, jint size
) {
    if (renderer)
        return (jboolean) renderer->onStreamAudioPacket(
                (uint8_t *) env->GetDirectBufferAddress(buffer), (size_t) size);
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_startRecord(
        JNIEnv *env, jclass c, jobject listener
) {
    if (!recorder)
        recorder = new AudioRecorder(onAudioData);

    // in case already recording...
    recorder->stop();

    if (recorderListener)
        env->DeleteGlobalRef(recorderListener);
    recorderListener = env->NewGlobalRef(listener);

    recorder->start();
}

extern "C" JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_stopRecord(
        JNIEnv *env, jclass c
) {
    if (recorder)
        recorder->stop();

    if (recorderListener) {
        env->DeleteGlobalRef(recorderListener);
        recorderListener = nullptr;
    }
}
