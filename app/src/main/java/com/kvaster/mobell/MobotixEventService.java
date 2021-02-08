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
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.kvaster.mobell.AndroidUtils.TAG;
import static com.kvaster.mobell.JsonUtils.ja;

public class MobotixEventService extends Service implements MxpegStreamer.Listener, CallService, SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final long PING_MIN_DELAY = TimeUnit.SECONDS.toMillis(60);
    private static final long READ_TIMEOUT = PING_MIN_DELAY + TimeUnit.SECONDS.toMillis(90);
    private static final long RECONNECT_MIN_DELAY = TimeUnit.SECONDS.toMillis(1);
    private static final long RECONNECT_MAX_DELAY = TimeUnit.SECONDS.toMillis(60);

    private static final String WIFI_TAG = "mobell:wifi";
    private static final String WAKE_TASK_TAG = "mobell:wake-task";
    private static final String WAKE_CALL_TAG = "mobell:wake-call";

    private static final String ACTION_TIMEOUT = "com.kvaster.mobell.TIMEOUT";
    private static final String ACTION_RECONNECT = "com.kvaster.mobell.RECONNECT";

    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new LocalBinder();

    private SharedPreferences prefs;

    private MxpegStreamer streamer;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock taskWakeLock;
    private PowerManager.WakeLock callWakeLock;

    private AlarmManager alarmManager;
    private AtomicReference<PendingIntent> currentAlarm = new AtomicReference<>();

    private Notification serviceNotification;

    private long reconnectDelay = RECONNECT_MIN_DELAY;

    private volatile boolean isRunning = false;

    private interface EventProcessor
    {
        boolean onEvent(JSONObject e) throws Exception;
    }

    private Map<Integer,EventProcessor> events = new ConcurrentHashMap<>();

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

    public static void startServiceIfEnabled(Context ctx)
    {
        if (AndroidUtils.getSharedPreferences(ctx).getBoolean(AppPreferences.SERVICE_BACKGROUND, false))
            startService(ctx, true);
    }

    public static void startService(Context ctx)
    {
        startService(ctx, false);
    }

    public static void startService(Context ctx, boolean forceForeground)
    {
        Intent i = new Intent(ctx, MobotixEventService.class);
        if (forceForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startForegroundService(i);
        else
            ctx.startService(i);
    }

    public static void stopService(Context ctx)
    {
        ctx.stopService(new Intent(ctx, MobotixEventService.class));
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (AppPreferences.SERVICE_FAST_WIFI.equals(key))
        {
            lockWifi();
        }
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate()
    {
        isRunning = true;

        super.onCreate();

        prefs = AndroidUtils.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // we need to create notification for foreground service
        serviceNotification = createServiceNofitication();

        // alarms
        alarmManager = Objects.requireNonNull((AlarmManager)getSystemService(ALARM_SERVICE));

        // we need wifi lock to receive packest over wifi even in sleep mode
        lockWifi();

        // we will use lock only during connection initiation
        taskWakeLock = ((PowerManager)Objects.requireNonNull(getSystemService(POWER_SERVICE)))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TASK_TAG);
        taskWakeLock.setReferenceCounted(false);

        // We need to use FULL_WAKE_LOCK for older (pre 8.x) devices like huawei media pad with 7.0 on board.
        //noinspection deprecation
        callWakeLock = ((PowerManager) Objects.requireNonNull(getSystemService(POWER_SERVICE)))
                .newWakeLock(PowerManager.FULL_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, WAKE_CALL_TAG);
        callWakeLock.setReferenceCounted(false);

        streamer = new PrefsAwareMxpegStreamer(
                this,
                this,
                1024 * 4, // events packets are small - 4kb is enough
                1024 * 16, // whole event data is small - 16kb is enough
                (int)READ_TIMEOUT,
                0 // we will take care of reconnections by ourselves in order to save battery life
        );
        streamer.start();

        scheduleReconnect();
    }

    private synchronized void lockWifi()
    {
        if (wifiLock != null)
            wifiLock.release();

        boolean highPerf = prefs.getBoolean(AppPreferences.SERVICE_FAST_WIFI, false);

        wifiLock = ((WifiManager)Objects.requireNonNull(getApplicationContext().getSystemService(WIFI_SERVICE)))
                .createWifiLock(highPerf ? WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL, WIFI_TAG);
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();
    }

    private synchronized void unlockWifi()
    {
        wifiLock.release();
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
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText(getResources().getString(R.string.notification_text));

        return builder.build();
    }

    private void scheduleReconnect()
    {
        scheduleAction(ACTION_RECONNECT, reconnectDelay);

        reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_DELAY);
    }

    private void scheduleTimeout()
    {
        scheduleAction(ACTION_TIMEOUT, PING_MIN_DELAY);
    }

    private void scheduleAction(String action, long delayMillis)
    {
        if (!isRunning)
            return;

        Log.i(TAG, "MBE: action scheduled: " + action + " / " + delayMillis);

        cancelScheduledAction();

        Intent i = new Intent(this, MobotixEventService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMillis, pi);

        pi = currentAlarm.getAndSet(pi);
        if (pi != null)
            alarmManager.cancel(pi);
    }

    private void cancelScheduledAction()
    {
        PendingIntent pi = currentAlarm.getAndSet(null);
        if (pi != null)
            alarmManager.cancel(pi);
    }

    @Override
    public void onDestroy()
    {
        isRunning = false;

        Log.i(TAG, "MBE: service destroyed");

        cancelScheduledAction();

        unlockWifi();
        taskWakeLock.release();
        callWakeLock.release();

        streamer.stop();

        stopForeground(true);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        try
        {
            String action = intent == null ? null : intent.getAction();

            Log.i(TAG, "MBE: Start command - " + action);

            if (ACTION_TIMEOUT.equals(action))
            {
                Log.i(TAG, "MBE: timeout occured");
                taskWakeLock.acquire(1000);
                streamer.sendCmd("list_addressees");

                synchronized (this)
                {
                    if (callStatus == CallStatus.UNACCEPTED)
                        changeCallStatus(CallStatus.IDLE);
                }
            }
            else if (ACTION_RECONNECT.equals(action))
            {
                Log.i(TAG, "MBE: forcing reconnect");
                taskWakeLock.acquire(1000); // 1s - for reconnect attempt
                scheduleReconnect();
                streamer.forceReconnectIfNeed();
            }
            else
            {
                Log.i(TAG, "MBE: service started");
                // service start (or restart) requested
                startForeground(NOTIFICATION_ID, serviceNotification);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "MBE: ERR", e);
        }

        Log.i(TAG, "MBE: finished");

        return Service.START_STICKY;
    }

    private int registerEvent(EventProcessor callback)
    {
        int id = streamer.nextId();
        events.put(id, callback);
        return id;
    }

    @Override
    public void onStreamStart()
    {
        Log.i(TAG, "MBE: " + new Date() + " | Stream start");

        changeCallStatus(CallStatus.IDLE);

        events.clear();

        // Some subscriptions. Generally we do not need this at all for our app.
        //streamer.sendCmd("subscription", ja("alarmupdate", true));
        //streamer.sendCmd("subscription", ja("door", true));
        //streamer.sendCmd("subscription", ja("elight", true));
        //streamer.sendCmd("subscription", ja("nearest_events", true));

        // REGISTER DEVICE
        // {"result":[[32800,"Main Bell",""]],"error":null,"id":15}
        // {"id":26,"method":"add_device","params":["B4:F1:DA:E8:C5:8A",[32800],"AxAPP+B4:F1:DA:E8:C5:8A"]}
        // {"result":null,"error":[17,"device is already registered"],"id":26}
        // {"id":27,"method":"register_device","params":["B4:F1:DA:E8:C5:8A"]}

        // BELL
        // {"result":["bell",true,false,[32800,"Main Bell",""]],"type":"cont","error":null,"id":27}
        // {"result":["bell",false,true],"type":"cont","error":null,"id":27}
        // {"id":47,"method":"bell_ack","params":[false]}
        // {"id":83,"method":"stop"}
        // {"id":88,"method":"trigger","params":["door"]}
        // {"result":["door_open",true],"type":"cont","error":null,"id":73} - на trigger

        streamer.sendCmd(registerEvent((e) -> {
            int devId = e.getJSONArray("result").getJSONArray(0).getInt(0);
            String mac = AndroidUtils.getMacAddr();
            streamer.sendCmd(registerEvent((e2) -> {
                streamer.sendCmd(registerEvent(this::onBell), "register_device", ja(mac));
                return true;
            }),"add_device", ja(mac, ja(devId), "MoBell+" + mac));
            return true;
        }), "list_addressees");

        scheduleTimeout();
    }

    @Override
    public void onStreamStop()
    {
        Log.i(TAG, "MBE: " + new Date() + " | Stream stop");

        changeCallStatus(CallStatus.DISCONNECTED);

        events.clear();

        reconnectDelay = RECONNECT_MIN_DELAY;
        scheduleReconnect();
    }

    @Override
    public boolean onStreamVideoPacket(ByteBuffer packet, int size)
    {
        // we should not receive video packets in service
        throw new IllegalStateException();
    }

    @Override
    public boolean onStreamAudioPacket(ByteBuffer packet, int size)
    {
        // we should not receive audio packets in service
        throw new IllegalStateException();
    }

    private synchronized boolean onBell(JSONObject event) throws JSONException
    {
        String type;

        try
        {
            type = String.valueOf(event.getJSONArray("result").get(0));
        }
        catch (Exception e)
        {
            return false;
        }

        if ("bell".equals(type))
        {
            boolean isRing;

            try
            {
                isRing = event.getJSONArray("result").getBoolean(1);
            }
            catch (Exception e)
            {
                return false;
            }

            if (isRing)
            {
                if (changeCallStatus(CallStatus.UNACCEPTED))
                {
                    // only for test purposes
//                    streamer.sendCmd("bell_pong");

                    Intent i = new Intent(this, MainActivity.class);
                    i.setAction(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                }
            }
            else
            {
                if (callStatus == CallStatus.SUPPRESSED || callStatus == CallStatus.UNACCEPTED)
                    changeCallStatus(CallStatus.IDLE);
            }
        }

        return false;
    }

    @Override
    public void onMobotixEvent(JSONObject event)
    {
        Log.i(TAG, "MBE: " + new Date() + " | " + event);

        try
        {
            int id = event.optInt("id");
            EventProcessor ep = events.get(id);
            if (ep != null)
            {
                if (ep.onEvent(event))
                    events.remove(id);
            }

//            if ("ping".equals(event.opt("method")))
//                streamer.sendCmd("pong");
//
//            if ("awake".equals(event.opt("method")))
//            {
//                Intent i = new Intent(this, MainActivity.class);
//                i.setAction(Intent.ACTION_MAIN);
//                i.addCategory(Intent.CATEGORY_LAUNCHER);
//                startActivity(i);
//                streamer.sendCmd("awake");
//            }

            scheduleTimeout();
        }
        catch (Exception e)
        {
            Log.e(TAG, "MBE: ERR", e);
        }
    }

    //////////////////////////////////////////////
    // Control calls

    private Collection<Listener> listeners = new ArrayList<>();
    private CallStatus callStatus = CallStatus.DISCONNECTED;

    @SuppressLint("WakelockTimeout")
    private synchronized boolean changeCallStatus(CallStatus status)
    {
        if (callStatus == status)
            return false;

        if (status == CallStatus.UNACCEPTED)
            callWakeLock.acquire();
        else
            callWakeLock.release();

        for (Listener l : listeners)
            l.onCallStatus(status);
        callStatus = status;
        return true;
    }

    @Override
    public synchronized void addListener(Listener listener)
    {
        listeners.add(listener);
        listener.onCallStatus(callStatus);
    }

    @Override
    public synchronized void removeListener(Listener listener)
    {
        listeners.remove(listener);
        listener.onCallStatus(CallStatus.DISCONNECTED);
    }

    @Override
    public synchronized void suppressCall()
    {
        if (callStatus == CallStatus.UNACCEPTED)
        {
            changeCallStatus(CallStatus.SUPPRESSED);
            streamer.sendCmd("stop");
        }
    }

    @Override
    public void acceptCall()
    {
        if (callStatus == CallStatus.UNACCEPTED)
        {
            changeCallStatus(CallStatus.ACCEPTED);
            streamer.sendCmd("bell_ack", ja(true));
        }
    }

    @Override
    public void stopCall()
    {
        if (callStatus == CallStatus.UNACCEPTED || callStatus == CallStatus.ACCEPTED || callStatus == CallStatus.SUPPRESSED)
        {
            changeCallStatus(CallStatus.IDLE);
            streamer.sendCmd("bell_ack", ja(false));
        }
    }

    @Override
    public void openDoor()
    {
        streamer.sendCmd("trigger", ja("door"));
    }
}
