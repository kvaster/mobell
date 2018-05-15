package com.kvaster.mobell;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Objects;

public class MobotixEventService extends Service implements MxpegStreamer.Listener
{
    private final IBinder binder = new LocalBinder();

    private MxpegStreamer streamer;
    private WifiManager.WifiLock wifiLock;

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

    @Override
    public void onCreate()
    {
        super.onCreate();

        wifiLock = ((WifiManager)Objects.requireNonNull(getApplicationContext().getSystemService(WIFI_SERVICE)))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "mobell");

        wifiLock.acquire();

        streamer = new MxpegStreamer(BuildConfig.MOBOTIX_HOST, BuildConfig.MOBOTIX_LOGIN, BuildConfig.MOBOTIX_PASS, this, 1024 * 4);
        streamer.start();
    }

    @Override
    public void onDestroy()
    {
        if (wifiLock.isHeld())
            wifiLock.release();

        streamer.stop();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return Service.START_STICKY;
    }

    @Override
    public void onStreamStart(int audioType)
    {

    }

    @Override
    public void onStreamStop()
    {

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
        // TODO do something
    }
}
