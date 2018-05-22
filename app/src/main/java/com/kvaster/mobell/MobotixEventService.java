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

public class MobotixEventService extends Service implements MxpegStreamer.Listener, CallService
{
    private static final long PING_MIN_DELAY = TimeUnit.SECONDS.toMillis(120);
    private static final long READ_TIMEOUT = PING_MIN_DELAY + TimeUnit.SECONDS.toMillis(120);
    private static final long RECONNECT_MIN_DELAY = TimeUnit.SECONDS.toMillis(1);
    private static final long RECONNECT_MAX_DELAY = TimeUnit.SECONDS.toMillis(60);

    private static final String LOCK_TAG = "com.kvaster.mobell.MobotixEventService";

    private static final String ACTION_TIMEOUT = "com.kvaster.mobell.TIMEOUT";
    private static final String ACTION_RECONNECT = "com.kvaster.mobell.RECONNECT";

    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new LocalBinder();

    private MxpegStreamer streamer;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    private AlarmManager alarmManager;
    private AtomicReference<PendingIntent> currentAlarm = new AtomicReference<>();

    private Notification serviceNotification;

    private long reconnectDelay = RECONNECT_MIN_DELAY;

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

    public static void startService(Context ctx)
    {
        ctx.startService(new Intent(ctx, MobotixEventService.class));
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
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();

        // we will use lock only during connection initiation
        wakeLock = ((PowerManager)Objects.requireNonNull(getSystemService(POWER_SERVICE)))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG);
        wakeLock.setReferenceCounted(false);

        streamer = new MxpegStreamer(
                BuildConfig.MOBOTIX_HOST,
                BuildConfig.MOBOTIX_PORT,
                BuildConfig.MOBOTIX_LOGIN,
                BuildConfig.MOBOTIX_PASS,
                this,
                1024 * 4, // events packets are small - 4kb is enough
                1024 * 16, // whole event data is small - 16kb is enough
                (int)READ_TIMEOUT,
                0 // we will take care of reconnections by ourselves in order to save battery life
        );
        streamer.start();

        scheduleReconnect();
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
        cancelScheduledAction();

        wifiLock.release();
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
            String action = intent == null ? null : intent.getAction();

            Log.i(TAG, "MBE: Start command - " + action);

            if (ACTION_TIMEOUT.equals(action))
            {
                Log.i(TAG, "MBE: timeout occured");
                streamer.sendCmd("list_addressees");
            }
            else if (ACTION_RECONNECT.equals(action))
            {
                Log.i(TAG, "MBE: forcing reconnect");
                wakeLock.acquire(1000); // 1s - for reconnect attempt
                scheduleReconnect();
                streamer.forceReconnectIfNeed();
            }
            else
            {
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

    private boolean onBell(JSONObject event) throws JSONException
    {
        String type = String.valueOf(event.getJSONArray("result").get(0));
        if ("bell".equals(type))
        {
            boolean isRing = event.getJSONArray("result").getBoolean(0);

            if (isRing)
            {
                changeCallStatus(CallStatus.UNACCEPTED);

                Intent i = new Intent(this, MainActivity.class);
                i.setAction(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                startActivity(i);
            }
            else
            {
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

    private synchronized void changeCallStatus(CallStatus status)
    {
        if (callStatus != status)
        {
            for (Listener l : listeners)
                l.onCallStatus(status);
            callStatus = status;
        }
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
        if (callStatus == CallStatus.UNACCEPTED)
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
