package com.kvaster.mobell;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

import static com.kvaster.mobell.AndroidUtils.TAG;
import static com.kvaster.mobell.JsonUtils.ja;

public class MobotixEventService extends Service implements MxpegStreamer.Listener, CallService, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final long RECONNECT_MIN_DELAY = TimeUnit.SECONDS.toMillis(1);

    private static final String WIFI_TAG = "mobell:wifi";
    private static final String WAKE_TASK_TAG = "mobell:wake-task";
    private static final String WAKE_CALL_TAG = "mobell:wake-call";

    private static final String STATE_CHECK_ACTION = "com.kvaster.mobell.STATE_CHECK";
    private static final String CALL_TIMEOUT_ACTION = "com.kvaster.mobell.CALL_TIMEOUT";

    private static final int NOTIF_ID_FG = 1;
    private static final String CHAN_ID_FG = "mobell-fg";

    private static final int NOTIF_ID_CALL = 2;
    private static final String CHAN_ID_CALL = "mobell-call";

    private final IBinder binder = new LocalBinder();

    private SharedPreferences prefs;

    private MxpegStreamer streamer;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock taskWakeLock;
    private PowerManager.WakeLock callWakeLock;

    private AlarmManager alarmManager;
    private NotificationManager notificationManager;
    private PendingIntent stateCheckAlarm; // used only with synchronized
    private PendingIntent callTimeoutAlarm; // used only with synchronized

    private Notification serviceNotification;
    private Notification callNotification;

    private final AtomicInteger actionCounter = new AtomicInteger();

    private Ringtone ringtone;

    // if zero -> we're connected
    private long reconnectDelay = RECONNECT_MIN_DELAY;

    private long keepaliveDelay;
    private long readTimeout;
    private long callTimeout;
    private long reconnectMaxDelay;

    private interface EventProcessor {
        boolean onEvent(JSONObject e) throws Exception;
    }

    private final Map<Integer, EventProcessor> events = new ConcurrentHashMap<>();

    public class LocalBinder extends Binder {
        MobotixEventService getService() {
            return MobotixEventService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public static void startServiceIfEnabled(Context ctx) {
        if (AndroidUtils.getSharedPreferences(ctx).getBoolean(AppPreferences.SERVICE_BACKGROUND, false)) {
            startService(ctx, true);
        }
    }

    public static void startService(Context ctx) {
        startService(ctx, false);
    }

    public static void startService(Context ctx, boolean forceForeground) {
        Intent i = new Intent(ctx, MobotixEventService.class);
        if (forceForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stopService(Context ctx) {
        ctx.stopService(new Intent(ctx, MobotixEventService.class));
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AppPreferences.SERVICE_FAST_WIFI.equals(key)) {
            lockWifi();
        } else if (AppPreferences.KEEPALIVE.equals(key)
                || AppPreferences.READ_TIMEOUT.equals(key)
                || AppPreferences.CALL_TIMEOUT.equals(key)) {
            readPrefs();
        }
    }

    private long safeLong(String key, long defValue, long minValue, long maxValue) {
        try {
            return Math.max(Math.min(Long.parseLong(prefs.getString(key, Long.toString(defValue))), maxValue), minValue);
        } catch (Exception e) {
            return defValue;
        }
    }

    private synchronized void readPrefs() {
        keepaliveDelay = TimeUnit.SECONDS.toMillis(safeLong(AppPreferences.KEEPALIVE, 60, 10, 120));
        readTimeout = TimeUnit.SECONDS.toMillis(keepaliveDelay + safeLong(AppPreferences.READ_TIMEOUT, 90, 30, 120));
        callTimeout = TimeUnit.SECONDS.toMillis(safeLong(AppPreferences.CALL_TIMEOUT, 30, 1, 120));
        reconnectMaxDelay = TimeUnit.SECONDS.toMillis(safeLong(AppPreferences.RECONNECT_DELAY, 60, 1, 120));

        if (streamer != null) {
            streamer.setReadTimeout((int)readTimeout);
        }
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        super.onCreate();

        prefs = AndroidUtils.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        readPrefs();

        // alarms
        alarmManager = getSystemService(AlarmManager.class);
        // notifications
        notificationManager = getSystemService(NotificationManager.class);

        // we need to create notification for foreground service
        serviceNotification = createServiceNofitication();
        // call notification
        callNotification = createCallNotification();


        // we need wifi lock to receive packets over wifi even in sleep mode
        lockWifi();

        PowerManager powerManager = getSystemService(PowerManager.class);

        // we will use lock only during connection initiation
        taskWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TASK_TAG);
        taskWakeLock.setReferenceCounted(false);

        // We need to use FULL_WAKE_LOCK for older (pre 8.x) devices like huawei media pad with 7.0 on board.
        //noinspection deprecation
        callWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, WAKE_CALL_TAG);
        callWakeLock.setReferenceCounted(false);

        streamer = new PrefsAwareMxpegStreamer(
                this,
                this,
                1024 * 4, // events packets are small - 4kb is enough
                1024 * 16, // whole event data is small - 16kb is enough
                (int) readTimeout,
                0 // we will take care of reconnections by ourselves in order to save battery life
        );
        streamer.start();

        forceScheduleStateCheck();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "MBE: service destroyed");

        // There will be no any events from streamer after stop
        // We should do it before destroying other stuff
        streamer.stop();

        cancelStateCheck();
        cancelCallTimeout();

        dismissCallNotification();
        stopRingtone();

        unlockWifi();
        taskWakeLock.release();
        callWakeLock.release();

        stopForeground(true);

        super.onDestroy();
    }

    private synchronized void lockWifi() {
        if (wifiLock != null) {
            wifiLock.release();
        }

        boolean highPerf = prefs.getBoolean(AppPreferences.SERVICE_FAST_WIFI, false);

        wifiLock = getApplicationContext().getSystemService(WifiManager.class)
                .createWifiLock(highPerf ? WifiManager.WIFI_MODE_FULL_HIGH_PERF : WifiManager.WIFI_MODE_FULL, WIFI_TAG);
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();
    }

    private synchronized void unlockWifi() {
        wifiLock.release();
    }

    private Notification createServiceNofitication() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHAN_ID_FG, getString(R.string.s_notif_service), NotificationManager.IMPORTANCE_NONE);
            notificationManager.createNotificationChannel(ch);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHAN_ID_FG);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText(getResources().getString(R.string.notification_text));

        return builder.build();
    }

    private synchronized void cancelStateCheck() {
        if (stateCheckAlarm != null) {
            alarmManager.cancel(stateCheckAlarm);
            stateCheckAlarm = null;

            Log.i(TAG, "MBE: alarm canceled: " + STATE_CHECK_ACTION);
        }
    }

    private synchronized void scheduleStateCheck(long stateCheckDelay) {
        cancelStateCheck();
        stateCheckAlarm = fireAlarm(STATE_CHECK_ACTION, stateCheckDelay);
    }

    private synchronized void scheduleConnectedStateCheck() {
        reconnectDelay = 0;
        scheduleStateCheck(keepaliveDelay);
    }

    private void forceScheduleStateCheck() {
        scheduleStateCheck(0);
    }

    private synchronized void cancelCallTimeout() {
        if (callTimeoutAlarm != null) {
            alarmManager.cancel(callTimeoutAlarm);
            callTimeoutAlarm = null;

            Log.i(TAG, "MBE: alarm canceled: " + CALL_TIMEOUT_ACTION);
        }
    }

    private synchronized void scheduleCallTimeout() {
        cancelCallTimeout();
        callTimeoutAlarm = fireAlarm(CALL_TIMEOUT_ACTION, callTimeout);
    }

    private PendingIntent fireAlarm(String action, long delayMillis) {
        Log.i(TAG, "MBE: alarm scheduled: " + action + " / " + delayMillis);

        Intent i = new Intent(this, MobotixEventService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(this, actionCounter.getAndIncrement(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMillis, pi);

        return pi;
    }

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent == null ? null : intent.getAction();

            if (STATE_CHECK_ACTION.equals(action)) {
                taskWakeLock.acquire(1000);
                if (reconnectDelay == 0) {
                    Log.i(TAG, "MBE: state check - ping");
                    scheduleConnectedStateCheck();

                    streamer.sendCmd("list_addressees");
                } else {
                    Log.i(TAG, "MBE: state check - reconnect");
                    reconnectDelay = Math.min(reconnectDelay * 2, reconnectMaxDelay);
                    scheduleStateCheck(reconnectDelay);

                    streamer.forceReconnectIfNeed();
                }
            } else if (CALL_TIMEOUT_ACTION.equals(action)) {
                Log.i(TAG, "MBE: call timeout");

                if (callStatus == CallStatus.UNACCEPTED || callStatus == CallStatus.SUPPRESSED) {
                    changeCallStatus(CallStatus.IDLE);
                }

                cancelCallTimeout();
            } else {
                Log.i(TAG, "MBE: service started, action: " + action);
                // service start (or restart) requested
                startForeground(NOTIF_ID_FG, serviceNotification);
            }
        } catch (Exception e) {
            Log.e(TAG, "MBE: ERR", e);
        }

        return Service.START_STICKY;
    }

    private int registerEvent(EventProcessor callback) {
        int id = streamer.nextId();
        events.put(id, callback);
        return id;
    }

    @Override
    public synchronized void onStreamStart() {
        Log.i(TAG, "MBE: stream start");

        taskWakeLock.acquire(1000);
        scheduleConnectedStateCheck();

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
            }), "add_device", ja(mac, ja(devId), "MoBell+" + mac));
            return true;
        }), "list_addressees");
    }

    @Override
    public synchronized void onStreamStop() {
        Log.i(TAG, "MBE: stream stop");

        reconnectDelay = RECONNECT_MIN_DELAY;
        scheduleStateCheck(reconnectDelay);

        changeCallStatus(CallStatus.DISCONNECTED);

        events.clear();
    }

    @Override
    public boolean onStreamVideoPacket(ByteBuffer packet, int size) {
        // we should not receive video packets in service
        throw new IllegalStateException();
    }

    @Override
    public boolean onStreamAudioPacket(ByteBuffer packet, int size) {
        // we should not receive audio packets in service
        throw new IllegalStateException();
    }

    private synchronized boolean onBell(JSONObject event) {
        String type;

        try {
            type = String.valueOf(event.getJSONArray("result").get(0));
        } catch (Exception e) {
            return false;
        }

        if ("bell".equals(type)) {
            boolean isRing;

            try {
                isRing = event.getJSONArray("result").getBoolean(1);
            } catch (Exception e) {
                return false;
            }

            Log.i(TAG, "MBE: bell received and ring is: " + isRing);

            if (isRing) {
                changeCallStatus(CallStatus.UNACCEPTED);
            } else {
                if (callStatus == CallStatus.SUPPRESSED || callStatus == CallStatus.UNACCEPTED) {
                    changeCallStatus(CallStatus.IDLE);
                }
            }
        } else if ("suppress".equals(type)) {
            Log.i(TAG, "MBE: suppress received");

            if (callStatus == CallStatus.UNACCEPTED) {
                changeCallStatus(CallStatus.SUPPRESSED);
            }
        }

        return false;
    }

    private Notification createCallNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHAN_ID_CALL, getString(R.string.s_notif_call), NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(ch);
        }

        // only for test purposes
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHAN_ID_CALL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(getString(R.string.s_ringing))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setAutoCancel(true)
                        .setSilent(true)
                        .setFullScreenIntent(pi, true);

        return builder.build();
    }

    private void fireCallNotification() {
        notificationManager.notify(NOTIF_ID_CALL, callNotification);
        scheduleCallTimeout();
    }

    private void dismissCallNotification() {
        notificationManager.cancel(NOTIF_ID_CALL);
    }

    @Override
    public synchronized void onMobotixEvent(JSONObject event) {
        Log.i(TAG, "MBE: event " + event);

        taskWakeLock.acquire(1000);
        scheduleConnectedStateCheck();

        try {
            int id = event.optInt("id");
            EventProcessor ep = events.get(id);
            if (ep != null) {
                if (ep.onEvent(event)) {
                    events.remove(id);
                }
            }

            if ("ping".equals(event.opt("method"))) {
                streamer.sendCmd("pong");
            }

        } catch (Exception e) {
            Log.e(TAG, "MBE: error on event", e);
        }
    }

    //////////////////////////////////////////////
    // Ringtone

    private synchronized void playRingtone() {
        try {
            stopRingtone();

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            String ringtoneUri = prefs.getString(AppPreferences.RINGTONE, Settings.System.DEFAULT_RINGTONE_URI.toString());

            if (TextUtils.isEmpty(ringtoneUri)) {
                ringtone = null;
            } else {
                ringtone = RingtoneManager.getRingtone(this, Uri.parse(ringtoneUri));
                if (ringtone != null) {
                    ringtone.setAudioAttributes(attrs);
                    ringtone.play();
                }
            }

            if (shouldVibrate()) {
                Vibrator v = getSystemService(Vibrator.class);

                long[] pattern = { 0, 250, 250, 250 };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs);
                } else {
                    v.vibrate(pattern, 0, attrs);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MBE: error sound and vibrate", e);
            // do nothing ?
            stopRingtone();
        }
    }

    private synchronized void stopRingtone() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }

        getSystemService(Vibrator.class).cancel();
    }

    private boolean shouldVibrate() {
        if (!prefs.getBoolean(AppPreferences.VIBRATION, true)) {
            return false;
        }

        if (!getSystemService(Vibrator.class).hasVibrator()) {
            return false;
        }

        int ringerMode = ((AudioManager) getSystemService(AUDIO_SERVICE)).getRingerMode();

        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING) > 0) {
                return ringerMode != AudioManager.RINGER_MODE_SILENT;
            }
        } catch (Exception e) {
            // do nothing
        }

        return ringerMode == AudioManager.RINGER_MODE_VIBRATE;
    }

    //////////////////////////////////////////////
    // Control calls

    private final Collection<Listener> listeners = new ArrayList<>();
    private CallStatus callStatus = CallStatus.DISCONNECTED;

    @SuppressLint("WakelockTimeout")
    private synchronized void changeCallStatus(CallStatus status) {
        if (callStatus == status) {
            return;
        }

        Log.i(TAG, "MBE: call status is " + status);

        if (status == CallStatus.UNACCEPTED) {
            // acquire lock only for call time with some gap
            callWakeLock.acquire(callTimeout + TimeUnit.SECONDS.toMillis(5));
            playRingtone();
            fireCallNotification();
        } else {
            callWakeLock.release();
            dismissCallNotification();
            stopRingtone();
        }

        for (Listener l : listeners) {
            l.onCallStatus(status);
        }

        callStatus = status;
    }

    @Override
    public synchronized void addListener(Listener listener) {
        Log.i(TAG, "MBE: add listener");
        listeners.add(listener);
        listener.onCallStatus(callStatus);

        // new 'app' connected - try to reconnect if necessary
        forceScheduleStateCheck();
    }

    @Override
    public synchronized void removeListener(Listener listener) {
        Log.i(TAG, "MBE: remove listener");
        listeners.remove(listener);
        listener.onCallStatus(CallStatus.DISCONNECTED);
    }

    @Override
    public synchronized void suppressCall() {
        Log.i(TAG, "MBE: suppress call");
        if (callStatus == CallStatus.UNACCEPTED) {
            changeCallStatus(CallStatus.SUPPRESSED);
            streamer.sendCmd("suppress");
        }
    }

    @Override
    public synchronized void acceptCall() {
        Log.i(TAG, "MBE: accept call");
        if (callStatus == CallStatus.UNACCEPTED) {
            changeCallStatus(CallStatus.ACCEPTED);
            streamer.sendCmd("bell_ack", ja(true));
        }
    }

    @Override
    public synchronized void stopCall() {
        Log.i(TAG, "MBE: stop call");
        if (callStatus == CallStatus.UNACCEPTED || callStatus == CallStatus.ACCEPTED || callStatus == CallStatus.SUPPRESSED) {
            changeCallStatus(CallStatus.IDLE);
            streamer.sendCmd("bell_ack", ja(false));
        }
    }

    @Override
    public void openDoor() {
        Log.i(TAG, "MBE: open door");
        streamer.sendCmd("trigger", ja("door"));
    }
}
