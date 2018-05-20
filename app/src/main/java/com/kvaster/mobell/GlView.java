package com.kvaster.mobell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.util.concurrent.TimeUnit;

import static com.kvaster.mobell.AndroidUtils.TAG;

public class GlView extends GLSurfaceView implements GLSurfaceView.Renderer
{
    private boolean started = false;
    private boolean suspended = false;
    private boolean paused = false;

    private static final long TARGET_FPS = 60;
    private long drawTimestamp = System.nanoTime();

    private final DisplayMetrics displayMetrics;
    private final GlApp app;

    public GlView(GlApp app, Activity activity, DisplayMetrics displayMetrics)
    {
        super(activity);

        this.app = app;
        this.displayMetrics = displayMetrics;

        // Ask for OpenGLES version 2.0.
        setEGLContextClientVersion(2);
        setEGLConfigChooser(false);

        // Render ourselves always.
        setRenderer(this);
        // Pop up for canvas. Now canvas is rendered on top of background.
        setZOrderOnTop(true);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void stop()
    {
        queueEvent(app::stop);
    }

    public void resume()
    {
        // This function is called not from render thread.
        // Let's create event in render thread for simplicity.
        if (suspended)
        {
            onResume();

            suspended = false;

            Runnable r = new Runnable() {
                @Override
                public synchronized void run()
                {
                    try
                    {
                        app.resume();
                    }
                    catch (Throwable t)
                    {
                        Log.e(TAG, "Error on resume", t);
                    }

                    notifyAll();
                }
            };

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized(r)
            {
                queueEvent(r);
                try
                {
                    r.wait();
                }
                catch (InterruptedException e)
                {
                    // fatal error
                }
            }
        }
    }

    public void suspend()
    {
        // This function is called not from render thread.
        // Let's create event in render thread for simplicity.
        if (!suspended)
        {
            suspended = true;

            Runnable r = new Runnable() {
                @Override
                public synchronized void run()
                {
                    try
                    {
                        app.suspend();
                    }
                    catch (Throwable t)
                    {
                        Log.e(TAG, "Error on suspend", t);
                    }

                    notifyAll();
                }
            };

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized(r)
            {
                queueEvent(r);
                try
                {
                    r.wait();
                }
                catch (InterruptedException e)
                {
                    // fatal error
                }
            }

            onPause();
        }
    }

    public void pause()
    {
        // This function is called not from render thread.
        // Let's create event in render thread for simplicity.
        if (!paused)
        {
            paused = true;
            queueEvent(() -> {
                try
                {
                    app.pause();
                }
                catch (Throwable t)
                {
                    Log.e(TAG, "Error on pause");
                }
            });
        }
    }

    public void unpause()
    {
        // This function is called not from render thread.
        // Let's create event in render thread for simplicity.
        if (paused)
        {
            paused = false;
            queueEvent(() -> {
                try
                {
                    app.unpause();
                }
                catch (Throwable t)
                {
                    Log.e(TAG, "Error on unpause");
                }
            });
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        int w = getWidth();
        int h = getHeight();

        // IMPORTANT! This function is called in render thread!
        app.canvasCreated(w, h, displayMetrics.densityDpi, displayMetrics.density);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        if (!started)
        {
            app.start();
            started = true;
            // Destroy splash screen
            final Activity a = (Activity)getContext();
            a.runOnUiThread(() -> a.getWindow().setBackgroundDrawable(null));
        }

        app.canvasSizeChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl)
    {
        try
        {
            long now = System.nanoTime();
            long frameTime = TimeUnit.SECONDS.toNanos(1) / TARGET_FPS;
            long dt = now - drawTimestamp;
            if (dt >= frameTime)
            {
                drawTimestamp = now - (dt - frameTime);

                // processEvents();

                app.update();
                app.draw();
            }
        }
        catch (Throwable t)
        {
            Log.e(TAG, "Error in draw", t);
        }
    }

    // Processed separately from pointerQueue and it's not MotionEvent,
    // cause MotionEvent.BUTTON_BACK is accessible only from API 14.
    public void onBackButtonPressed()
    {
        app.onBackButtonPressed();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        return app.onTouchEvent(event);
    }
}
