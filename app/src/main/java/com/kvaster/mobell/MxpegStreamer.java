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
                            while (keepRunning)
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
    private static final int APP0 = 0xE0;
    private static final int COM = 0xFE;
    private static final int DQT = 0xDB;
    private static final int DHT = 0xC4;
    //private static final int DRI = 0xDD;
    private static final int SOF0 = 0xC0;
    private static final int SOS = 0xDA;
    private static final int EOI = 0xD9;
    private static final int APP11 = 0xEB;
    private static final int APP13 = 0xED;

    private void readPacket(RingBufferReader r) throws IOException
    {
        //noinspection StatementWithEmptyBody
        while (r.next() != 0xff) ;

        switch (r.next())
        {
            case SOI:
                readVideo(r);
                break;

            case APP13:
                readAudioAlaw(r);
                break;

            case APP11:
                readAudioPcm(r);
                break;

            default:
                throw new IOException();
        }
    }

    private int getPacket(int start, RingBufferReader r, ByteBuffer b) throws IOException
    {
        int end = r.pos();

        b.clear();
        r.get(b, start, end);
        int size = buffer.position();
        buffer.rewind();

        r.cut(end);

        return size;
    }

    private void readVideo(RingBufferReader r) throws IOException
    {
        // include SOI marker
        int start = r.pos() - 2;

        while (true)
        {
            //noinspection StatementWithEmptyBody
            while (r.next() != 0xff);

            int marker = r.next();

            if (marker == EOI)
                break;

            if (marker != SOF0
                    && marker != SOS
                    && marker != APP0
                    && marker != COM
                    && marker != DQT
                    && marker != DHT)
            {
                throw new IOException();
            }

            int len = (r.next() << 8) | r.next();
            r.move(len - 2);

            if (marker == SOS)
            {
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

        int size = getPacket(start, r, buffer);
        listener.onStreamVideoPacket(buffer, size);
    }

    private void readAudioAlaw(RingBufferReader r) throws IOException
    {
        // this is sound block
        int len = (r.next() << 8) | r.next();

        // just skip this, we're playing ALL bytes we have and we're plating them right NOW
        int duration = r.next() | (r.next() << 8) | (r.next() << 16) | (r.next() << 24);
        long timestamp = 0;
        for (int i = 0; i < 8; i++)
            timestamp |= ((long)r.next()) << (i * 8);

        int start = r.pos();

        r.move(len - 2 - 12);

        int size = getPacket(start, r, buffer);
        listener.onStreamAudioPacket(buffer, size);
    }

    private void readAudioPcm(RingBufferReader r) throws IOException
    {
        throw new IOException();
    }
}
