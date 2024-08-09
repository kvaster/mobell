package com.kvaster.mobell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case "android.intent.action.BOOT_COMPLETED":
            case "android.intent.action.QUICKBOOT_POWERON":
            case "android.intent.action.REBOOT":
                MobotixEventService.startBackgroundService(context);
        }
    }
}
