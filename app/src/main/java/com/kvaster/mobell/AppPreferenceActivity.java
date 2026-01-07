package com.kvaster.mobell;

import static android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AppPreferenceActivity extends AppCompatActivity {
    private static final Map<String, Integer> hintsMap = new HashMap<>();

    static {
        hintsMap.put(AppPreferences.HOST, R.string.mobell_p_host_hint);
        hintsMap.put(AppPreferences.PORT, R.string.mobell_p_port_hint);
        hintsMap.put(AppPreferences.LOGIN, R.string.mobell_p_login_hint);
        hintsMap.put(AppPreferences.PASSWORD, R.string.mobell_p_pass_hint);
        hintsMap.put(AppPreferences.KEEPALIVE, R.string.mobell_p_keepalive_hint);
        hintsMap.put(AppPreferences.RECONNECT_DELAY, R.string.mobell_p_reconnect_delat_hint);
        hintsMap.put(AppPreferences.CALL_TIMEOUT, R.string.mobell_p_call_timeout_hint);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        );

        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
        private final ActivityResultLauncher<Intent> ringtoneSelector = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result != null && result.getData() != null) {
                        Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                        String ringtone = uri == null ? "" : uri.toString();
                        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).edit()
                                .putString(AppPreferences.RINGTONE, ringtone)
                                .commit();
                        onPreferenceChange(Objects.requireNonNull(findPreference(AppPreferences.RINGTONE)), ringtone);
                    }
                }
        );

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.mobell_preferences);

            walkSettings(getPreferenceScreen(), getPreferenceManager().getSharedPreferences());
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            RecyclerView list = getListView();
            list.setClipToPadding(false);

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                list.setPadding(insets.left, 0, insets.right, insets.bottom);
                view.setPadding(0, insets.top, 0, 0);
                return windowInsets;
            });
        }

        private void walkSettings(PreferenceGroup group, SharedPreferences prefs) {
            final int count = group.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                Preference p = group.getPreference(i);
                if (p instanceof PreferenceGroup) {
                    walkSettings((PreferenceGroup) p, prefs);
                } else {
                    String key = p.getKey();

                    p.setOnPreferenceChangeListener(this);
                    onPreferenceChange(p, prefs.getAll().get(key));

                    Integer hint = hintsMap.get(key);
                    if (hint != null) {
                        EditTextPreference ep = (EditTextPreference) p;
                        ep.setOnBindEditTextListener(et -> et.setHint(hint));
                    }

                    switch (key) {
                        case AppPreferences.DISABLE_OPTIMIZATION:
                            BattteryOptimizationPreference bp = (BattteryOptimizationPreference) p;
                            bp.setLauncher(registerForActivityResult(
                                    new ActivityResultContracts.StartActivityForResult(),
                                    result -> {
                                        bp.updateChecked();
                                    }));
                            break;

                        case AppPreferences.VIBRATION:
                            if (!requireContext().getSystemService(Vibrator.class).hasVibrator()) {
                                ((SwitchPreferenceCompat) p).setChecked(false);
                                p.setEnabled(false);
                            }
                            break;

                        case AppPreferences.SERVICE_BACKGROUND:
                            p.setOnPreferenceChangeListener((pref, newValue) -> {
                                boolean val = newValue instanceof Boolean && (Boolean) newValue;
                                return (!val || ((AppPreferenceActivity) requireActivity()).checkBackgroundServicePermissions()) && onPreferenceChange(pref, newValue);
                            });
                            break;
                    }
                }
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // do nothing here
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference pref, Object newValue) {
            if (pref instanceof EditTextPreference p) {
                String v = (String) newValue;

                if (TextUtils.isEmpty(v)) {
                    Integer hint = hintsMap.get(p.getKey());
                    if (hint != null) {
                        p.setSummary(hint);
                    }
                } else if (AppPreferences.PASSWORD.equals(p.getKey())) {
                    p.setSummary(R.string.mobell_p_pass_summary);
                } else {
                    p.setSummary(v);
                }
            } else if (AppPreferences.RINGTONE.equals(pref.getKey())) {
                String v = (String) newValue;

                if (v == null) {
                    v = Settings.System.DEFAULT_RINGTONE_URI.toString();
                }

                if (TextUtils.isEmpty(v)) {
                    pref.setSummary(R.string.mobell_p_ringtone_none);
                } else {
                    Ringtone tone = RingtoneManager.getRingtone(getActivity(), Uri.parse(v));
                    pref.setSummary(tone == null ? "?" : tone.getTitle(getActivity()));
                }
            }

            if (AppPreferences.SERVICE_BACKGROUND.equals(pref.getKey())) {
                boolean enabled = newValue instanceof Boolean && (Boolean) newValue;
                Context ctx = requireActivity();
                if (enabled) {
                    MobotixEventService.startService(ctx);
                } else {
                    MobotixEventService.stopService(ctx);
                }
            }

            return true;
        }

        @Override
        public boolean onPreferenceTreeClick(Preference p) {
            if (AppPreferences.RINGTONE.equals(p.getKey())) {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI);

                String existingValue = Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).getString(p.getKey(), Settings.System.DEFAULT_RINGTONE_URI.toString());
                if (existingValue.isEmpty()) {
                    // Select "Silent"
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue));
                }

                ringtoneSelector.launch(intent);

                return true;
            } else if (AppPreferences.DISABLE_OPTIMIZATION.equals(p.getKey())) {
                return true;
            } else {
                return super.onPreferenceTreeClick(p);
            }
        }
    }

    private boolean canUseFullScreenIntent() {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                || getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }

    private boolean checkPostPermission() {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkBackgroundServicePermissions() {
        boolean canUseFullScreenIntent = canUseFullScreenIntent();
        boolean postPermission = checkPostPermission();

        if (canUseFullScreenIntent && postPermission) {
            return true;
        }

        new AlertDialog.Builder(this)
                .setMessage(R.string.mobell_a_background_service_permissions)
                .setCancelable(false)
                .setTitle(R.string.mobell_a_warning)
                .setNegativeButton(R.string.mobell_a_no, (dialog, which) -> {
                    dialog.dismiss();
                })
                .setPositiveButton(R.string.mobell_a_yes, (dialog, which) -> {
                    if (!postPermission) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                    }
                    if (!canUseFullScreenIntent) {
                        startActivity(new Intent(ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:" + getPackageName())));
                    }
                })
                .create().show();


        return false;
    }
}
