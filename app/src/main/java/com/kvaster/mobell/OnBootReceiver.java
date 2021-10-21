package com.kvaster.mobell;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import static android.content.Context.BIND_AUTO_CREATE;

public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case "android.intent.action.BOOT_COMPLETED":
            case "android.intent.action.QUICKBOOT_POWERON":
            case "android.intent.action.REBOOT":
                MobotixEventService.startServiceIfEnabled(context);
        }
    }
}
