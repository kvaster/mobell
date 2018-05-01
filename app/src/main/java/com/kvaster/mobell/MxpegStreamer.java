package com.kvaster.mobell;

import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

public class MxpegStreamer
{
    public interface Listener
    {
        void onStreamStart();
        void onStreamStop();
        void onStreamVideoPacket(ByteBuffer packet, int size);
        void onStreamAudioPacket(ByteBuffer packet, int size);
    }

    private final URL url;
    private final String login;
    private final String password;
    private final Listener listener;

    private volatile boolean keepRunning;
    private Thread thread;

    private int keyframeStart = 0;
    private int packetStart = 0;
    private int packetEnd = 0;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024 * 2);

    public MxpegStreamer(String url, String login, String password, Listener listener) throws MalformedURLException
    {
        this.url = new URL(url);
        this.login = login;
        this.password = password;

        this.listener = listener;
    }

    public void start()
    {
        if (thread == null)
        {
            keepRunning = true;
            thread = new Thread(this::run);
            thread.start();
        }
    }

    public void stop()
    {
        if (thread != null)
        {
            keepRunning = false;
            thread.interrupt();

            try
            {
                thread.join();
            }
            catch (InterruptedException ie)
            {
                // do nothing
            }

            thread = null;
        }
    }

    private void run()
    {
        while (keepRunning)
        {
            try
            {
                keyframeStart = 0;
                packetStart = 0;
                packetEnd = 0;

                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                String encoded = Base64.encodeToString((login + ":" + password).getBytes(), Base64.NO_WRAP);
                conn.addRequestProperty("Authorization", "Basic " + encoded);

                conn.setReadTimeout(5000);
                conn.setConnectTimeout(5000);
                conn.setDoInput(true);

                boolean connected = false;

                try
                {
                    conn.connect();

                    int code = conn.getResponseCode();

                    if (code == HttpURLConnection.HTTP_OK)
                    {
                        listener.onStreamStart();
                        connected = true;

                        try (InputStream is = conn.getInputStream())
                        {
                            RingBufferReader r = new RingBufferReader(is);
                            while (true)
                                readPacket(r);
                        }
                    }
                }
                finally
                {
                    conn.disconnect();

                    if (connected)
                        listener.onStreamStop();
                }
            }
            catch (Exception e)
            {
                // error
            }

            try
            {
                if (keepRunning)
                    Thread.sleep(500);
            }
            catch (InterruptedException ie)
            {
                // do nothing here
            }
        }
    }

    private static final int SOI = 0xD8;
    //    private static final int APP0 = 0xE0;
//    private static final int COM = 0xFE;
//    private static final int DQT = 0xDB;
//    private static final int DHT = 0xC4;
//    private static final int DRI = 0xDD;
    private static final int SOF0 = 0xC0;
    private static final int SOS = 0xDA;
    private static final int EOI = 0xD9;
    private static final int APP13 = 0xED;

    private void readPacket(RingBufferReader r) throws IOException
    {
        boolean hasSof0 = false;
        boolean hasSos = false;

        while (true)
        {
            //noinspection StatementWithEmptyBody
            while (r.next() != 0xff);

            int marker = r.next();
            if (marker == EOI)
            {
                // end of image
                break;
            }
            else if (marker == SOI)
            {
                // start of image
                packetStart = r.pos() - 2;
            }
            else
            {
                if (marker == SOF0)
                    hasSof0 = true;

                int len = (r.next() << 8) | r.next();
                r.move(len - 2);

                if (marker == SOS)
                {
                    hasSos = true;

                    while (true)
                    {
                        //noinspection StatementWithEmptyBody
                        while (r.next() != 0xff);

                        marker = r.next();
                        if (marker != 0)
                        {
                            r.move(-2);
                            break;
                        }
                    }
                }
            }
        }

        if (!hasSos)
            throw new IOException();

        packetStart = packetEnd;
        packetEnd = r.pos();
        if (hasSof0)
        {
            keyframeStart = packetStart;
            r.cut(keyframeStart);
        }

        buffer.clear();
        r.get(buffer, packetStart, packetEnd);
        int size = buffer.position();
        buffer.rewind();
        listener.onStreamVideoPacket(buffer, size);
    }
}
