package com.kvaster.mobell;

import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

public class AppPreferenceActivity extends PreferenceActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener
    {
        private SharedPreferences prefs;

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            prefs = getPreferenceManager().getSharedPreferences();

            PreferenceScreen ps = getPreferenceScreen();
            final int count = ps.getPreferenceCount();
            for (int i = 0; i < count; i++)
            {
                Preference p = ps.getPreference(i);
                p.setOnPreferenceChangeListener(this);
                onPreferenceChange(ps.getPreference(i), prefs.getAll().get(p.getKey()));
            }
        }

        @Override
        public boolean onPreferenceChange(Preference pref, Object newValue)
        {
            if (pref instanceof EditTextPreference)
            {
                EditTextPreference p = (EditTextPreference)pref;
                String v = (String)newValue;

                if (TextUtils.isEmpty(v))
                    p.setSummary(p.getEditText().getHint());
                else if ((p.getEditText().getInputType() & EditorInfo.TYPE_MASK_VARIATION) == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
                    p.setSummary(R.string.p_pass_summary);
                else
                    p.setSummary(v);
            }
            else if (pref instanceof RingtonePreference)
            {
                RingtonePreference p = (RingtonePreference)pref;
                String v = (String)newValue;

                if (TextUtils.isEmpty(v))
                {
                    p.setSummary("none");
                }
                else
                {
                    Ringtone tone = RingtoneManager.getRingtone(getActivity(), Uri.parse(v));
                    p.setSummary(tone == null ? "?" : tone.getTitle(getActivity()));
                }
            }

            if (AppPreferences.SERVICE_BACKGROUND.equals(pref.getKey()))
            {
                //noinspection ConstantConditions
                boolean enabled = (Boolean)newValue;
                if (enabled)
                    MobotixEventService.startService(getActivity());
                else
                    MobotixEventService.stopService(getActivity());
            }

            return true;
        }
    }
}
