package com.kvaster.mobell;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

public class AppPreferenceActivity extends AppCompatActivity {
    private static final Map<String, Integer> hintsMap = new HashMap<>();

    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String LOGIN_KEY = "login";
    private static final String PASSWORD_KEY = "password";
    private static final String RINGTONE_KEY = "ringtone";
    private static final String DISABLE_OPTIMIZATION_KEY = "disable_optimization";

    static {
        hintsMap.put(HOST_KEY, R.string.p_host_hint);
        hintsMap.put(PORT_KEY, R.string.p_port_hint);
        hintsMap.put(LOGIN_KEY, R.string.p_login_hint);
        hintsMap.put(PASSWORD_KEY, R.string.p_pass_hint);
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
                                .putString(RINGTONE_KEY, ringtone)
                                .commit();
                        onPreferenceChange(findPreference(RINGTONE_KEY), ringtone);
                    }
                }
        );

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

            PreferenceScreen ps = getPreferenceScreen();
            final int count = ps.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                Preference p = ps.getPreference(i);
                p.setOnPreferenceChangeListener(this);
                onPreferenceChange(ps.getPreference(i), prefs.getAll().get(p.getKey()));

                Integer hint = hintsMap.get(p.getKey());
                if (hint != null) {
                    EditTextPreference ep = (EditTextPreference) p;
                    ep.setOnBindEditTextListener(et -> et.setHint(hint));
                }

                if (DISABLE_OPTIMIZATION_KEY.equals(p.getKey())) {
                    BattteryOptimizationPreference bp = (BattteryOptimizationPreference) p;
                    bp.setLauncher(registerForActivityResult(
                            new ActivityResultContracts.StartActivityForResult(),
                            result -> {
                                bp.updateChecked();
                            }));
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
                } else if (PASSWORD_KEY.equals(p.getKey())) {
                    p.setSummary(R.string.p_pass_summary);
                } else {
                    p.setSummary(v);
                }
            } else if (RINGTONE_KEY.equals(pref.getKey())) {
                String v = (String) newValue;

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
            if (RINGTONE_KEY.equals(p.getKey())) {
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
            } else if (DISABLE_OPTIMIZATION_KEY.equals(p.getKey())) {
                return true;
            } else {
                return super.onPreferenceTreeClick(p);
            }
        }
    }
}
