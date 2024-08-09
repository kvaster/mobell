package com.kvaster.mobell;

import static com.kvaster.mobell.AndroidUtils.TAG;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private GlView view;
    private MxpegApp app;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MobotixEventService s = ((MobotixEventService.LocalBinder) service).getService();
            app.onServiceBind(s);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // do nothing
        }
    };

    public MainActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "On create");

        super.onCreate(savedInstanceState);

        MobotixEventService.startBackgroundService(this);

        turnScreenOnAndKeyguardOff();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        app = new MxpegApp(this, displayMetrics);
        view = new GlView(app, this, displayMetrics);

        FrameLayout layout = new FrameLayout(this);
        layout.addView(view);
        setContentView(layout);
    }

    private void turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | LayoutParams.FLAG_TURN_SCREEN_ON
                    | LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(KeyguardManager.class).requestDismissKeyguard(this, null);
        }
    }

    private void cleanScreenSettings() {
        getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "On destroy");

        try {
            view.stop();

            cleanScreenSettings();

            super.onDestroy();
        } catch (Throwable t) {
            onCatch(t);
        }
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "On start");

        try {
            super.onStart();

            checkBackgroundPermissions();

            MobotixEventService.startService(this);

            Intent service = new Intent(this, MobotixEventService.class);
            bindService(service, connection, BIND_AUTO_CREATE);

            checkPermissions();

            view.resume();
        } catch (Throwable t) {
            onCatch(t);
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "On stop");

        try {
            view.suspend();

            app.onServiceUnbind();
            unbindService(connection);

            MobotixEventService.stopBackgroundService(this);

            super.onStop();
        } catch (Throwable t) {
            onCatch(t);
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "On resume");

        try {
            super.onResume();

            view.unpause();
        } catch (Throwable t) {
            onCatch(t);
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "On pause");

        try {
            view.pause();

            super.onPause();
        } catch (Throwable t) {
            onCatch(t);
        }
    }

    private void onCatch(Throwable t) {
        // TODO process error
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            app.allowRecording();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        int count = permissions.length;

        for (int i = 0; i < count; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    app.allowRecording();
                }
            }
        }
    }

    private void checkBackgroundPermissions() {
        if (AndroidUtils.getSharedPreferences(this).getBoolean(AppPreferences.SERVICE_BACKGROUND, false)) {
            boolean canUseFullScreenIntent = (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    || getSystemService(NotificationManager.class).canUseFullScreenIntent();

            boolean postPermission = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

            if (!canUseFullScreenIntent || !postPermission) {
                AndroidUtils.getSharedPreferences(this).edit().putBoolean(AppPreferences.SERVICE_BACKGROUND, false).apply();

                new AlertDialog.Builder(this)
                        .setMessage(R.string.mobell_a_background_service_warning)
                        .setCancelable(false)
                        .setTitle(R.string.mobell_a_warning)
                        .setNeutralButton(R.string.mobell_a_ok, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .create().show();
            }
        }
    }
}
