package com.kvaster.mobell;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

public class AppPreferenceActivity extends AppCompatActivity {
    private static final Map<String, Integer> hintsMap = new HashMap<>();

    static {
        hintsMap.put(AppPreferences.HOST, R.string.p_host_hint);
        hintsMap.put(AppPreferences.PORT, R.string.p_port_hint);
        hintsMap.put(AppPreferences.LOGIN, R.string.p_login_hint);
        hintsMap.put(AppPreferences.PASSWORD, R.string.p_pass_hint);
        hintsMap.put(AppPreferences.KEEPALIVE, R.string.p_keepalive_hint);
        hintsMap.put(AppPreferences.CALL_TIMEOUT, R.string.p_call_timeout_hint);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
                        getPreferenceManager().getSharedPreferences().edit()
                                .putString(AppPreferences.RINGTONE, ringtone)
                                .commit();
                        onPreferenceChange(findPreference(AppPreferences.RINGTONE), ringtone);
                    }
                }
        );

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            walkSettings(getPreferenceScreen(), getPreferenceManager().getSharedPreferences());
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

                    if (AppPreferences.DISABLE_OPTIMIZATION.equals(key)) {
                        BattteryOptimizationPreference bp = (BattteryOptimizationPreference) p;
                        bp.setLauncher(registerForActivityResult(
                                new ActivityResultContracts.StartActivityForResult(),
                                result -> {
                                    bp.updateChecked();
                                }));
                    } else if (AppPreferences.VIBRATION.equals(key)) {
                        if (!requireContext().getSystemService(Vibrator.class).hasVibrator()) {
                            ((SwitchPreferenceCompat)p).setChecked(false);
                            p.setEnabled(false);
                        }
                    }
                }
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // do nothing here
        }

        @Override
        public boolean onPreferenceChange(Preference pref, Object newValue) {
            if (pref instanceof EditTextPreference) {
                EditTextPreference p = (EditTextPreference) pref;
                String v = (String) newValue;

                if (TextUtils.isEmpty(v)) {
                    Integer hint = hintsMap.get(p.getKey());
                    if (hint != null) {
                        p.setSummary(hint);
                    }
                } else if (AppPreferences.PASSWORD.equals(p.getKey())) {
                    p.setSummary(R.string.p_pass_summary);
                } else {
                    p.setSummary(v);
                }
            } else if (AppPreferences.RINGTONE.equals(pref.getKey())) {
                String v = (String) newValue;

                if (v == null) {
                    v = Settings.System.DEFAULT_RINGTONE_URI.toString();
                }

                if (TextUtils.isEmpty(v)) {
                    pref.setSummary(R.string.p_ringtone_none);
                } else {
                    Ringtone tone = RingtoneManager.getRingtone(getActivity(), Uri.parse(v));
                    pref.setSummary(tone == null ? "?" : tone.getTitle(getActivity()));
                }
            }

            if (AppPreferences.SERVICE_BACKGROUND.equals(pref.getKey())) {
                boolean enabled = (Boolean) newValue;
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

                String existingValue = getPreferenceManager().getSharedPreferences().getString(p.getKey(), Settings.System.DEFAULT_RINGTONE_URI.toString());
                if (existingValue != null) {
                    if (existingValue.length() == 0) {
                        // Select "Silent"
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                    } else {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue));
                    }
                } else {
                    // No ringtone has been selected, set to the default
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
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
}
