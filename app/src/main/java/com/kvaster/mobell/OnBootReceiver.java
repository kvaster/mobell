package com.kvaster.mobell;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import static android.content.Context.BIND_AUTO_CREATE;

public class OnBootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        // start background service only if requested via preferences
        if (AndroidUtils.getSharedPreferences(context).getBoolean(AppPreferences.SERVICE_BACKGROUND, false))
            MobotixEventService.startService(context, true);
    }
}
