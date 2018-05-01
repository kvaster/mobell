package com.kvaster.mobell;

import android.app.Activity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity
{
    private static final String MOBOTIX_URL = "http://192.168.116.4/control/faststream.jpg?stream=MxPEG";
    private static final String MOBOTIX_LOGIN = "";
    private static final String MOBOTIX_PASSWORD = "";

    private GlView view;

    public MainActivity()
    {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        view = new GlView(new MxpegApp(MOBOTIX_URL, MOBOTIX_LOGIN, MOBOTIX_PASSWORD), this, displayMetrics);

        FrameLayout layout = new FrameLayout(this);
        layout.addView(view);
        setContentView(layout);
    }

    @Override
    protected void onDestroy()
    {
        try
        {
            view.stop();

            super.onDestroy();

            System.exit(0); // Very good hack. Need this to be sure application is killed completely.
        }
        catch (Throwable t)
        {
            onCatch(t);
        }
    }

    @Override
    protected void onStart()
    {
        try
        {
            super.onStart();

            view.resume();
        }
        catch (Throwable t)
        {
            onCatch(t);
        }
    }

    @Override
    protected void onStop()
    {
        try
        {
            view.suspend();

            super.onStop();
        }
        catch (Throwable t)
        {
            onCatch(t);
        }
    }

    @Override
    protected void onResume()
    {
        try
        {
            super.onResume();

            view.unpause();
        }
        catch (Throwable t)
        {
            onCatch(t);
        }
    }

    @Override
    protected void onPause()
    {
        try
        {
            view.pause();

            super.onPause();
        }
        catch (Throwable t)
        {
            onCatch(t);
        }
    }

    @Override
    public void onBackPressed()
    {
        view.onBackButtonPressed();
    }

    private void onCatch(Throwable t)
    {
        // TODO отработать ошибку
    }
}
