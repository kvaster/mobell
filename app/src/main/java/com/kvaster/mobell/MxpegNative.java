package com.kvaster.mobell;

import java.nio.ByteBuffer;

public class MxpegNative
{
    public static native void start();
    public static native void stop();

    public static native void suspend();
    public static native void resume();

    public static native void update();
    public static native void draw(float scale, float panX, float panY);

    public static native void onStreamStart();
    public static native void onStreamStop();
    // 0 - OK, 1 - we have video frame, -1 - err
    public static native int onStreamVideoPacket(ByteBuffer packet, int size);
    public static native boolean onStreamAudioPacket(ByteBuffer packet, int size);

    public static native void canvasSizeChanged(int width, int height);

    public static native void startRecord(AudioRecorderListener listener);
    public static native void stopRecord();

    static
    {
        //System.loadLibrary("avutil");
        //System.loadLibrary("avcodec");
        System.loadLibrary("mobell");
    }
}
