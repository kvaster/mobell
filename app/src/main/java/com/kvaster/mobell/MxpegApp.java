package com.kvaster.mobell;

import java.nio.ByteBuffer;

public class MxpegApp implements GlApp, MxpegStreamer.Listener
{
    private boolean needResume;
    private final MxpegStreamer streamer;

    public MxpegApp(String url, String login, String password)
    {
        streamer = new MxpegStreamer(url, login, password, this);
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
    public void onStreamStart(int audioType)
    {
        MxpegNative.onStreamStart(audioType);
    }

    @Override
    public void onStreamStop()
    {
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
}
