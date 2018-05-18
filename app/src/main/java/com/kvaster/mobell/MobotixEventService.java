package com.kvaster.mobell;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.kvaster.mobell.AndroidUtils.TAG;

public class MobotixEventService extends Service implements MxpegStreamer.Listener
{
    private static final long PING_MIN_DELAY = TimeUnit.SECONDS.toMillis(120);
    private static final long READ_TIMEOUT = PING_MIN_DELAY + TimeUnit.SECONDS.toMillis(120);

    private static final String LOCK_TAG = "com.kvaster.mobell.MobotixEventService";

    private static final String ACTION_TIMEOUT = "com.kvaster.mobell.TIMEOUT";
    private static final String ACTION_START_TIMEOUT = "com.kvaster.mobell.START_TIMEOUT";

    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new LocalBinder();

    private MxpegStreamer streamer;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;
    private AlarmManager alarmManager;

    private Notification serviceNotification;

    public class LocalBinder extends Binder
    {
        MobotixEventService getService()
        {
            return MobotixEventService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate()
    {
        super.onCreate();

        // we need to create notification for foreground service
        serviceNotification = createServiceNofitication();

        // alarms
        alarmManager = Objects.requireNonNull((AlarmManager)getSystemService(ALARM_SERVICE));

        // we need wifi lock to receive packest over wifi even in sleep mode
        wifiLock = ((WifiManager)Objects.requireNonNull(getApplicationContext().getSystemService(WIFI_SERVICE)))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, LOCK_TAG);
        wifiLock.acquire();

        // during connection we should acquire wake lock, we will release it only when we're connected
        wakeLock = ((PowerManager)Objects.requireNonNull(getSystemService(POWER_SERVICE)))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG);
        wakeLock.setReferenceCounted(true);
        wakeLock.acquire();

        streamer = new MxpegStreamer(
                BuildConfig.MOBOTIX_HOST,
                BuildConfig.MOBOTIX_PORT,
                BuildConfig.MOBOTIX_LOGIN,
                BuildConfig.MOBOTIX_PASS,
                this,
                1024 * 4, // events packets are small - 4kb is enough
                1024 * 16, // whole event data is small - 16kb is enough
                (int)READ_TIMEOUT
        );
        streamer.start();
    }

    private Notification createServiceNofitication()
    {
        String chid;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            chid = "mobell";

            NotificationManager nm = Objects.requireNonNull((NotificationManager)getSystemService(NOTIFICATION_SERVICE));
            NotificationChannel ch = new NotificationChannel(chid, "MoBell", NotificationManager.IMPORTANCE_NONE);
            nm.createNotificationChannel(ch);
        }
        else
        {
            chid = "";
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, chid);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE);
        return builder.build();
    }

    public static void startServiceTimeout(Context ctx)
    {
        try
        {
            Log.i(TAG, "Starting timeout");
            Intent i = new Intent(ctx, MobotixEventService.class)
                    .setAction(ACTION_TIMEOUT);
            ctx.startService(i);
        }
        catch (Exception e)
        {
            Log.e(TAG, "MBE: ERR", e);
        }
    }

    private void startTimeout()
    {
        try
        {
            wakeLock.acquire();

            Log.i(TAG, "MBE: start timeout requested");

            Intent i = new Intent(this, MobotixEventService.class)
                    .setAction(ACTION_TIMEOUT);
            PendingIntent pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + PING_MIN_DELAY, pi);

            wakeLock.release();
        }
        catch (Exception e)
        {
            Log.e(TAG, "MBE: ERROR", e);
        }
    }

    @Override
    public void onDestroy()
    {
        wifiLock.setReferenceCounted(false);
        if (wifiLock.isHeld())
            wifiLock.release();

        wakeLock.setReferenceCounted(false);
        if (wakeLock.isHeld())
            wakeLock.release();

        streamer.stop();

        NotificationManager nm = Objects.requireNonNull((NotificationManager)getSystemService(NOTIFICATION_SERVICE));
        nm.cancel(NOTIFICATION_ID);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        try
        {
            startForeground(NOTIFICATION_ID, serviceNotification);

            wakeLock.acquire();

            Log.i(TAG, "MBE: Start command");

            if (ACTION_TIMEOUT.equals(intent.getAction()))
            {
                Log.i(TAG, "MBE: timeout occured");
                streamer.sendCmd("list_addressees");
            }
            else if (ACTION_START_TIMEOUT.equals(intent.getAction()))
            {
                Log.i(TAG, "MBE: Start timeout");
            }

            wakeLock.release();
        }
        catch (Exception e)
        {
            Log.e(TAG, "MBE: ERR", e);
        }

        Log.i(TAG, "MBE: finished");

        return Service.START_STICKY;
    }

    @Override
    public void onStreamStart(int audioType)
    {
        Log.i(TAG, "MBE: " + new Date() + " | Stream start");

        streamer.subscribeToEvents();

        startTimeout();

        wakeLock.release();
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onStreamStop()
    {
        wakeLock.acquire();

        Log.i(TAG, "MBE: " + new Date() + " | Stream stop");
    }

    @Override
    public void onStreamVideoPacket(ByteBuffer packet, int size)
    {
        // we should not receive video packets in service
        throw new IllegalStateException();
    }

    @Override
    public void onStreamAudioPacket(ByteBuffer packet, int size)
    {
        // we should not receive audio packets in service
        throw new IllegalStateException();
    }

    @Override
    public void onMobotixEvent(JSONObject event)
    {
        wakeLock.acquire();

        Log.i(TAG, "MBE: " + new Date() + " | " + event);
        // TODO do something

        if ("ping".equals(event.opt("method")))
            streamer.sendCmd("pong");

        startTimeout();

        wakeLock.release();
    }
}
