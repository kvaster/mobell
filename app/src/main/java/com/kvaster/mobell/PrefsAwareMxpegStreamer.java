package com.kvaster.mobell;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsAwareMxpegStreamer extends MxpegStreamer implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final SharedPreferences prefs;

    public PrefsAwareMxpegStreamer(
            Context ctx,
            Listener listener,
            int bufferSize,
            int ringBufferSize,
            int readTimeout,
            long reconnectDelay
    ) {
        this(AndroidUtils.getSharedPreferences(ctx), listener, bufferSize, ringBufferSize, readTimeout, reconnectDelay);
    }

    public PrefsAwareMxpegStreamer(
            SharedPreferences prefs,
            Listener listener,
            int bufferSize,
            int ringBufferSize,
            int readTimeout,
            long reconnectDelay
    ) {
        super(listener, bufferSize, ringBufferSize, readTimeout, reconnectDelay);

        this.prefs = prefs;
    }

    @Override
    public String getHost() {
        return prefs.getString(AppPreferences.HOST, "");
    }

    @Override
    public int getPort() {
        try {
            return Integer.parseInt(prefs.getString(AppPreferences.PORT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getLogin() {
        return prefs.getString(AppPreferences.LOGIN, "");
    }

    @Override
    public String getPassword() {
        return prefs.getString(AppPreferences.PASSWORD, "");
    }

    @Override
    public void start() {
        prefs.registerOnSharedPreferenceChangeListener(this);
        super.start();
    }

    @Override
    public void stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.stop();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AppPreferences.HOST.equals(key)
                || AppPreferences.PORT.equals(key)
                || AppPreferences.LOGIN.equals(key)
                || AppPreferences.PASSWORD.equals(key)) {
            forceReconnect();
        }
    }
}
