package com.kvaster.mobell;

import org.json.JSONObject;

import java.nio.ByteBuffer;

public class MxpegApp implements GlApp, MxpegStreamer.Listener, AudioRecorderListener
{
    private boolean needResume;
    private final MxpegStreamer streamer;

    private boolean recordingEnabled;
    private boolean started;

    public MxpegApp(String host, int port, String login, String password)
    {
        streamer = new MxpegStreamer(host, port, login, password, this,
                1024 * 1024 * 2, // 2mb packets - should be really enough even for 6mpx data
                1024 * 1024 * 8, // 8mb ring buffer to hold large amount of data
                1000 // on audio/video stream huge delays are really bad -> wait only 1s
        );
    }

    public synchronized void allowRecording()
    {
        if (!recordingEnabled)
        {
            recordingEnabled = true;
            if (started)
                startRecording();
        }
    }

    private void startRecording()
    {
        streamer.startAudio();
        MxpegNative.startRecord(this);
    }

    private void stopRecording()
    {
        MxpegNative.stopRecord();
        streamer.stopAudio();
    }

    @Override
    public void start()
    {
        MxpegNative.start();
        streamer.start();
    }

    @Override
    public void stop()
    {
        streamer.stop();
        MxpegNative.stop();
    }

    @Override
    public void suspend()
    {
        streamer.stop();
        MxpegNative.suspend();
    }

    @Override
    public void resume()
    {
        // реальный resume делаем только когда будет создан surface
        needResume = true;
    }

    @Override
    public void pause()
    {
        // TODO
    }

    @Override
    public void unpause()
    {
        // TODO
    }

    @Override
    public void canvasCreated(int width, int height, int dpi, float density)
    {
        // TODO
    }

    @Override
    public void canvasSizeChanged(int width, int height)
    {
        if (needResume)
        {
            needResume = false;
            realResume();
            streamer.start();
        }

        MxpegNative.canvasSizeChanged(width, height);
    }

    @Override
    public void update()
    {
        MxpegNative.update();
    }

    @Override
    public void draw()
    {
        MxpegNative.draw();
    }

    private void realResume()
    {
        MxpegNative.resume();
    }

    @Override
    public synchronized void onStreamStart(int audioType)
    {
        MxpegNative.onStreamStart(audioType);
        streamer.startVideo();

        if (recordingEnabled)
            startRecording();
    }

    @Override
    public synchronized void onStreamStop()
    {
        if (recordingEnabled)
            stopRecording();

        MxpegNative.onStreamStop();
    }

    @Override
    public void onStreamVideoPacket(ByteBuffer packet, int size)
    {
        MxpegNative.onStreamVideoPacket(packet, size);
    }

    @Override
    public void onStreamAudioPacket(ByteBuffer packet, int size)
    {
        MxpegNative.onStreamAudioPacket(packet, size);
    }

    @Override
    public void onMobotixEvent(JSONObject event)
    {
        // do nothing by default
    }

    @Override
    public void onAudioData(byte[] data)
    {
        streamer.sendAudio(data);
    }
}
