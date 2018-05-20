// Native...

#include "com_kvaster_mobell_MxpegNative.h"

#include "mxpeg_renderer.h"
#include "audio_recorder.h"

static MxpegRenderer* renderer = nullptr;
static AudioRecorder* recorder = nullptr;

static JavaVM* globalJvm = nullptr;
static jclass recorderListenerClass;
static jmethodID methodOnAudioData;

static jobject recorderListener = nullptr;

static JNIEnv* getEnv()
{
    JNIEnv *env = nullptr;
    if (globalJvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
        globalJvm->AttachCurrentThread(&env, NULL);
    return env;
}

static void onAudioData(AudioBuffer* buffer)
{
    // TODO probably this is not optimal for Android's GC, but we can change it later
    if (recorderListener)
    {
        JNIEnv* env = getEnv();
        jbyteArray arr = env->NewByteArray(buffer->size);
        env->SetByteArrayRegion(arr, 0, buffer->size, (const jbyte*)buffer->buffer);
        env->CallVoidMethod(recorderListener, methodOnAudioData, arr);
        env->DeleteLocalRef(arr);
    }
}

////////////////////////////////////////////

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    globalJvm = vm;
    JNIEnv* env = getEnv();
    if (!env)
        return -1;

    recorderListenerClass = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("com/kvaster/mobell/AudioRecorderListener")));
    methodOnAudioData = env->GetMethodID(recorderListenerClass, "onAudioData", "([B)V");

    return JNI_VERSION_1_6;
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_start(JNIEnv *env, jclass c)
{
    renderer = new MxpegRenderer();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_stop(JNIEnv *env, jclass c)
{
    delete renderer;
    renderer = nullptr;
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_suspend(JNIEnv *env, jclass c)
{
    if (renderer)
        renderer->suspend();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_resume(JNIEnv *env, jclass c)
{
    if (renderer)
        renderer->resume();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_update(JNIEnv *env, jclass c)
{
    if (renderer)
        renderer->update();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_draw(JNIEnv *env, jclass c)
{
    if (renderer)
        renderer->draw();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_canvasSizeChanged(JNIEnv *env, jclass c, jint width, jint height)
{
    if (renderer)
        renderer->canvasSizeChanged(width, height);
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStart(JNIEnv *env, jclass c, jint audioType)
{
    if (renderer)
        renderer->onStreamStart(audioType);
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStop(JNIEnv *env, jclass c)
{
    if (renderer)
        renderer->onStreamStop();
}

jboolean JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamVideoPacket(JNIEnv *env, jclass c, jobject buffer, jint size)
{
    if (renderer)
        return (jboolean)renderer->onStreamVideoPacket((uint8_t *) env->GetDirectBufferAddress(buffer), (size_t) size);
    return JNI_FALSE;
}

jboolean JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamAudioPacket(JNIEnv *env, jclass c, jobject buffer, jint size)
{
    if (renderer)
        return (jboolean)renderer->onStreamAudioPacket((uint8_t *) env->GetDirectBufferAddress(buffer), (size_t) size);
    return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_startRecord(JNIEnv *env, jclass c, jobject listener)
{
    if (!recorder)
        recorder = new AudioRecorder(onAudioData);

    // in case already recording...
    recorder->stop();

    if (recorderListener)
        env->DeleteGlobalRef(recorderListener);
    recorderListener = env->NewGlobalRef(listener);

    recorder->start();
}

JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_stopRecord(JNIEnv *env, jclass c)
{
    if (recorder)
        recorder->stop();

    if (recorderListener)
    {
        env->DeleteGlobalRef(recorderListener);
        recorderListener = nullptr;
    }
}
