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

    public static native void onStreamStart(int audioType);
    public static native void onStreamStop();
    public static native boolean onStreamVideoPacket(ByteBuffer packet, int size);
    public static native boolean onStreamAudioPacket(ByteBuffer packet, int size);

    public static native void canvasSizeChanged(int width, int height);

    public static native void startRecord(AudioRecorderListener listener);
    public static native void stopRecord();

    static
    {
        System.loadLibrary("avutil");
        System.loadLibrary("avcodec");
        System.loadLibrary("mobell");
    }
}
