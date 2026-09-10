package com.kvaster.mobell;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class AndroidUtils {
    public static final String TAG = "kvaster-mobell";

    public static boolean hasLocalNetworkPermission(Context ctx) {
        return Build.VERSION.SDK_INT < 37
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_LOCAL_NETWORK)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) {
                    continue;
                }

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }

                return res1.toString();
            }
        } catch (Exception ex) {
            // do nothing
        }

        return "02:00:00:00:00:00";
    }

    public static byte[] fromHex(String hex) {
        int sz = hex.length() / 2;
        byte[] data = new byte[sz];

        for (int i = 0; i < sz; i++) {
            data[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4) | Character.digit(hex.charAt(i * 2 + 1), 16));
        }

        return data;
    }

    public static SharedPreferences getSharedPreferences(Context ctx) {
        return ctx.getSharedPreferences(ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }
}
