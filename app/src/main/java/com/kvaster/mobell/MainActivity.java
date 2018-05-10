package com.kvaster.mobell;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;

public class MainActivity extends Activity
{
    private GlView view;
    private MxpegApp app;

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

        app = new MxpegApp(BuildConfig.MOBOTIX_HOST, BuildConfig.MOBOTIX_LOGIN, BuildConfig.MOBOTIX_PASS);
        view = new GlView(app, this, displayMetrics);

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

            if (checkPermissions())
                app.allowRecording();

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

    private boolean checkPermissions()
    {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
        };

        boolean req = false;

        for (String p : permissions)
        {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
            {
                req = true;
                break;
            }
        }


        if (req)
        {
            ActivityCompat.requestPermissions(this, permissions, 0);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults)
    {
        app.allowRecording();
    }
}
