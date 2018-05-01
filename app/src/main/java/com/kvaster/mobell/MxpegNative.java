package com.kvaster.mobell;

import java.nio.ByteBuffer;

public class MxpegNative
{
    public static native void start();
    public static native void stop();

    public static native void suspend();
    public static native void resume();

    public static native void update();
    public static native void draw();

    public static native void onStreamStart();
    public static native void onStreamStop();
    public static native void onStreamVideoPacket(ByteBuffer packet, int size);
    public static native void onStreamAudioPacket(ByteBuffer packet, int size);

    public static native void canvasSizeChanged(int width, int height);

    static
    {
        System.loadLibrary("avutil");
        System.loadLibrary("avcodec");
        System.loadLibrary("mobell");
    }
}
