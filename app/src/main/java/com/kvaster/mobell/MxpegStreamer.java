package com.kvaster.mobell;

import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
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

    private final String host;
    private final String login;
    private final String password;
    private final Listener listener;

    private volatile boolean keepRunning;
    private Thread thread;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024 * 2);

    public MxpegStreamer(String host, String login, String password, Listener listener) throws MalformedURLException
    {
        this.host = host;
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
                boolean connected = false;

                InetAddress ia = InetAddress.getByName(host);
                Socket socket = new Socket(ia, 80);
                socket.setSoTimeout(5000);
                socket.setReceiveBufferSize(1024 * 1024 * 8);
                socket.setTcpNoDelay(true);

                try (InputStream is = socket.getInputStream(); OutputStream os = socket.getOutputStream())
                {
                    os.write("POST /control/eventstream.jpg HTTP/1.1\r\n".getBytes());
                    //os.write("POST /control/faststream.jpg?stream=MxPEG HTTP/1.1\r\n".getBytes());
                    os.write(("Host: " + ia.getHostAddress() + "\r\n").getBytes());
                    String encoded = Base64.encodeToString((login + ":" + password).getBytes(), Base64.NO_WRAP);
                    os.write(("Authorization: Basic " + encoded + "\r\n\r\n").getBytes());

                    listener.onStreamStart();
                    connected = true;

                    String[] json = {
                            //"{\"id\":11,\"method\":\"size\",\"params\":[{\"h\":-1,\"w\":-1}]}",
                            "{\"id\":12,\"method\":\"mode\",\"params\":[\"mxpeg\"]}",
                            "{\"id\":13,\"method\":\"live\",\"params\":[false]}",
                            //"{\"id\":14,\"method\":\"subscription\",\"params\":[\"alarmupdate\",true,\"\"]}",
                            //"{\"id\":15,\"method\":\"list_addressees\"}",
                            //"{\"id\":16,\"method\":\"reference_image\",\"params\":[{\"command\":\"get\"}]}",
                            //"{\"id\":17,\"method\":\"preview_live\",\"params\":[0,0]}",
                            "{\"id\":18,\"method\":\"subscription\",\"params\":[\"door\",true,\"\"]}",
                            //"{\"id\":19,\"method\":\"subscription\",\"params\":[\"elight\",true,\"\"]}",
                            //"{\"id\":20,\"method\":\"subscription\",\"params\":[\"nearest_events\",true,\"\"]}",
                            //"{\"id\":21,\"method\":\"camerafeatures\"}",
                            //"{\"id\":22,\"method\":\"recinfo\"}",
                            //"{\"id\":23,\"method\":\"kurator\",\"params\":[{\"path\":\"productinfo\",\"read\":true}]}",
                            //"{\"id\":24,\"method\":\"rangeinfo\"}",
                            "{\"id\":25,\"method\":\"audiooutput\",\"params\":[\"pcm16\"]}",
                            //"{\"id\":26,\"method\":\"add_device\",\"params\":[\"00:00:00:00:00:00\",[32800],\"MoBell+00:00:00:00:00:00\"]}",
                    };

                    for (String s : json)
                    {
                        os.write(s.getBytes());
                        os.write(new byte[]{0x0a, 0x00});
                    }

                    RingBufferReader r = new RingBufferReader(is);
                    while (keepRunning)
                        readPacket(r);
                }
                finally
                {
                    if (connected)
                        listener.onStreamStop();

                    socket.close();
                }
            }
            catch (Exception e)
            {
                // error, do nothing
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
    private static final int APP12 = 0xEC;
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

            case APP12:
                readEvents(r);
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
        int len = (r.next() << 8) | r.next();
        r.move(len - 2);
        r.cut(r.pos());
    }

    private void readEvents(RingBufferReader r) throws IOException
    {
        int len = (r.next() << 8) | r.next();

        int start = r.pos();
        r.move(len - 2);

        int end = r.pos();
        byte[] jsonData = r.get(start, end);
        r.cut(end);

        try
        {
            JSONObject json = new JSONObject(new String(jsonData));
            // TODO
        }
        catch (JSONException e)
        {
            throw new IOException(e);
        }
    }
}
