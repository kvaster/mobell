package com.kvaster.mobell;

import java.util.Objects;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.AttributeSet;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;
import androidx.preference.SwitchPreferenceCompat;

public class BattteryOptimizationPreference extends SwitchPreferenceCompat {
    private ActivityResultLauncher<Intent> launcher;

    public BattteryOptimizationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        updateChecked();
    }

    public void setLauncher(ActivityResultLauncher<Intent> launcher) {
        this.launcher = launcher;
    }

    public void updateChecked() {
        setChecked(isDisabled());
    }

    private boolean isDisabled() {
        Context context = Objects.requireNonNull(getContext());
        return context.getSystemService(PowerManager.class)
                .isIgnoringBatteryOptimizations(context.getPackageName());
    }

    @SuppressLint("BatteryLife")
    @Override
    protected void onClick() {
        Context context = Objects.requireNonNull(getContext());

        boolean allowRequire = ContextCompat.checkSelfPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) != PackageManager.PERMISSION_DENIED;
        Intent intent;
        if (isDisabled() || !allowRequire) {
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        } else {
            intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + context.getPackageName()));
        }

        launcher.launch(intent);
    }
}
