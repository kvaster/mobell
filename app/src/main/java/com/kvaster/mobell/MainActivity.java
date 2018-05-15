package com.kvaster.mobell;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;

public class MainActivity extends Activity
{
    private GlView view;
    private MxpegApp app;

    private ServiceConnection connection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            MobotixEventService s = ((MobotixEventService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            // do nothing
        }
    };

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

        Intent service = new Intent(this, MobotixEventService.class);
        startService(service);
        bindService(service, connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy()
    {
        try
        {
            view.stop();

            unbindService(connection);

            super.onDestroy();
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
                Manifest.permission.RECORD_AUDIO,
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
