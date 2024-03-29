/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_kvaster_mobell_MxpegNative */

#ifndef _Included_com_kvaster_mobell_MxpegNative
#define _Included_com_kvaster_mobell_MxpegNative
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    start
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_start
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    stop
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_stop
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    suspend
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_suspend
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    resume
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_resume
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    update
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_update
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    draw
 * Signature: (FFF)V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_draw
        (JNIEnv *, jclass, jfloat, jfloat, jfloat);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    onStreamStart
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStart
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    onStreamStop
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamStop
        (JNIEnv *, jclass);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    onStreamVideoPacket
 * Signature: (Ljava/nio/ByteBuffer;I)Z
 */
JNIEXPORT jint JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamVideoPacket
        (JNIEnv *, jclass, jobject, jint);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    onStreamAudioPacket
 * Signature: (Ljava/nio/ByteBuffer;I)Z
 */
JNIEXPORT jboolean JNICALL Java_com_kvaster_mobell_MxpegNative_onStreamAudioPacket
        (JNIEnv *, jclass, jobject, jint);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    canvasSizeChanged
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_canvasSizeChanged
        (JNIEnv *, jclass, jint, jint);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    startRecord
 * Signature: (Lcom/kvaster/mobell/AudioRecorderListener;)V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_startRecord
        (JNIEnv *, jclass, jobject);

/*
 * Class:     com_kvaster_mobell_MxpegNative
 * Method:    stopRecord
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_kvaster_mobell_MxpegNative_stopRecord
        (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
