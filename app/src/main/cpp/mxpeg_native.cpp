// Native...

#include "com_kvaster_mobell_MxpegNative.h"

#include "mxpeg_renderer.h"

static MxpegRenderer* renderer = nullptr;

void JNICALL Java_com_kvaster_mobell_MxpegNative_start
        (JNIEnv *env, jclass c) {
    renderer = new MxpegRenderer();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_stop
        (JNIEnv *env, jclass c) {
    delete renderer;
    renderer = nullptr;
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_suspend
        (JNIEnv *env, jclass c) {
    if (renderer)
        renderer->suspend();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_resume
        (JNIEnv *env, jclass c) {
    if (renderer)
        renderer->resume();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_update
        (JNIEnv *env, jclass c) {
    if (renderer)
        renderer->update();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_draw
        (JNIEnv *env, jclass c) {
    if (renderer)
        renderer->draw();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_canvasSizeChanged
        (JNIEnv *env, jclass c, jint width, jint height) {
    if (renderer)
        renderer->canvasSizeChanged(width, height);
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStart
        (JNIEnv *env, jclass c, jint audioType) {
    if (renderer)
        renderer->onStreamStart(audioType);
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStop
        (JNIEnv *env, jclass c) {
    if (renderer)
        renderer->onStreamStop();
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamVideoPacket
        (JNIEnv *env, jclass c, jobject buffer, jint size) {
    if (renderer)
        renderer->onStreamVideoPacket((uint8_t *) env->GetDirectBufferAddress(buffer), (size_t) size);
}

void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamAudioPacket
        (JNIEnv *env, jclass c, jobject buffer, jint size) {
    if (renderer)
        renderer->onStreamAudioPacket((uint8_t *) env->GetDirectBufferAddress(buffer), (size_t) size);
}
