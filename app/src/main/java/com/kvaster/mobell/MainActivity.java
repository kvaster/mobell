package com.kvaster.mobell;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;

import static com.kvaster.mobell.AndroidUtils.TAG;

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
            app.onServiceBind(s);
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
        Log.i(TAG, "On create");

        super.onCreate(savedInstanceState);

        MobotixEventService.startServiceIfEnabled(this);

        // show activity over locked screen
        getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        //getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        app = new MxpegApp(this, displayMetrics);
        view = new GlView(app, this, displayMetrics);

        FrameLayout layout = new FrameLayout(this);
        layout.addView(view);
        setContentView(layout);
    }

    @Override
    protected void onDestroy()
    {
        Log.i(TAG, "On destroy");

        try
        {
            view.stop();

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
        Log.i(TAG, "On start");

        try
        {
            super.onStart();

            MobotixEventService.startServiceIfEnabled(this);

            Intent service = new Intent(this, MobotixEventService.class);
            bindService(service, connection, BIND_AUTO_CREATE);

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
        Log.i(TAG, "On stop");

        try
        {
            view.suspend();

            app.onServiceUnbind();
            unbindService(connection);

            if (!AndroidUtils.getSharedPreferences(this).getBoolean(AppPreferences.SERVICE_BACKGROUND, false))
                MobotixEventService.stopService(this);

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
        Log.i(TAG, "On resume");

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
        Log.i(TAG, "On pause");

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

//    @Override
//    public void onBackPressed()
//    {
//        view.onBackButtonPressed();
//    }

    private void onCatch(Throwable t)
    {
        // TODO process error
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
        int count = permissions.length;

        for (int i = 0; i < count; i++)
        {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i]))
            {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                    app.allowRecording();
            }
        }
    }
}
