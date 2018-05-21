package com.kvaster.mobell;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import org.json.JSONObject;

import java.nio.ByteBuffer;

public class MxpegApp implements GlApp, MxpegStreamer.Listener, AudioRecorderListener
{
    private boolean needResume;
    private final MxpegStreamer streamer;

    private boolean recordingEnabled;
    private boolean started;

    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private volatile float scale;
    private volatile float panX;
    private volatile float panY;

    public MxpegApp(Context ctx, String host, int port, String login, String password)
    {
        streamer = new MxpegStreamer(host, port, login, password, this,
                1024 * 1024 * 2, // 2mb packets - should be really enough even for 6mpx data
                1024 * 1024 * 8, // 8mb ring buffer to hold large amount of data
                1000, // on audio/video stream huge delays are really bad -> wait only 1s
                500 // reconnect almost immediatelly with live streams
        );

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
            {
                if (scaleGestureDetector.isInProgress())
                    return false;

                panX -= distanceX;
                panY -= distanceY;
                return true;
            }

            public boolean onDoubleTap(MotionEvent e)
            {
                scale = 1;
                panX = 0;
                panY = 0;
                return true;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            float startPanX;
            float startPanY;
            float startFocusX;
            float startFocusY;

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector)
            {
                startFocusX = detector.getFocusX();
                startFocusY = detector.getFocusY();
                startPanX = panX;
                startPanY = panY;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector)
            {
                scale = Math.max(0.5f, Math.min(2.0f, scale * detector.getScaleFactor()));
                panX = startPanX + detector.getFocusX() - startFocusX;
                panY = startPanY + detector.getFocusY() - startFocusY;
                return true;
            }
        });
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
        needResume = true; // sometimes we have unpause without resume...
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

        scale = 1;
        panX = 0;
        panY = 0;

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
        MxpegNative.draw(scale, panX, panY);
    }

    private void realResume()
    {
        MxpegNative.resume();
    }

    @Override
    public synchronized void onStreamStart()
    {
        MxpegNative.onStreamStart();
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
    public boolean onStreamVideoPacket(ByteBuffer packet, int size)
    {
        return MxpegNative.onStreamVideoPacket(packet, size);
    }

    @Override
    public boolean onStreamAudioPacket(ByteBuffer packet, int size)
    {
        return MxpegNative.onStreamAudioPacket(packet, size);
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

    public void onBackButtonPressed()
    {
        // TODO
    }

    public boolean onTouchEvent(MotionEvent event)
    {
        boolean ok = scaleGestureDetector.onTouchEvent(event);
        ok |= gestureDetector.onTouchEvent(event);
        ok |= canProcess(event);
        return ok;
    }

    private static boolean canProcess(MotionEvent event)
    {
        switch (event.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }

        return false;
    }
}
