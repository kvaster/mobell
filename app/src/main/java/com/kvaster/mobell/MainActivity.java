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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 0;

    private GlView view;
    private MxpegApp app;
    private boolean started;
    private boolean serviceBound;

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

        turnScreenOnAndKeyguardOff();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        app = new MxpegApp(this, displayMetrics);
        view = new GlView(app, this, displayMetrics);

        FrameLayout layout = new FrameLayout(this);
        layout.addView(view);

        layout.setOnApplyWindowInsetsListener((v, insets) -> {
            app.updateInsets(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
            return insets;
        });

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
            started = true;

            checkBackgroundPermissions();

            view.resume();
            updateLocalNetworkAccess();
            checkPermissions();
        } catch (Throwable t) {
            onCatch(t);
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "On stop");

        try {
            started = false;
            view.suspend();

            unbindCameraService();

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
            updateLocalNetworkAccess();
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
        Log.e(TAG, "Activity lifecycle error", t);
    }

    private void unbindCameraService() {
        if (serviceBound) {
            app.onServiceUnbind();
            unbindService(connection);
            serviceBound = false;
        }
    }

    private void updateLocalNetworkAccess() {
        if (!started) {
            return;
        }
        if (AndroidUtils.hasLocalNetworkPermission(this)) {
            if (!serviceBound) {
                MobotixEventService.startService(this);
                Intent service = new Intent(this, MobotixEventService.class);
                serviceBound = bindService(service, connection, BIND_AUTO_CREATE);
            }
            app.startStreaming();
        } else {
            app.stopStreaming();
            unbindCameraService();
            MobotixEventService.stopService(this);
        }
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (!AndroidUtils.hasLocalNetworkPermission(this)) {
            permissions.add(Manifest.permission.ACCESS_LOCAL_NETWORK);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            app.allowRecording();
        } else {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }

        int count = Math.min(permissions.length, grantResults.length);
        for (int i = 0; i < count; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    app.allowRecording();
                }
            } else if (Manifest.permission.ACCESS_LOCAL_NETWORK.equals(permissions[i])
                    && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.mobell_a_local_network_title)
                        .setMessage(R.string.mobell_a_local_network_permission)
                        .setNegativeButton(R.string.mobell_a_ok, null)
                        .setPositiveButton(R.string.mobell_settings, (dialog, which) ->
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.parse("package:" + getPackageName()))))
                        .show();
            }
        }
        updateLocalNetworkAccess();
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
