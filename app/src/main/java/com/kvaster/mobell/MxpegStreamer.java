package com.kvaster.mobell;

import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kvaster.mobell.JsonUtils.ja;
import static com.kvaster.mobell.JsonUtils.je;
import static com.kvaster.mobell.JsonUtils.jo;

public abstract class MxpegStreamer
{
    public interface Listener
    {
        void onStreamStart();
        void onStreamStop();
        boolean onStreamVideoPacket(ByteBuffer packet, int size);
        boolean onStreamAudioPacket(ByteBuffer packet, int size);
        void onMobotixEvent(JSONObject event);
    }

    private final Listener listener;

    private final BlockingQueue<byte[]> packets = new LinkedBlockingQueue<>();
    private final AtomicInteger idGenerator = new AtomicInteger(10);

    private static final byte[] END_MARKER = new byte[0];

    private static final byte[] AUDIO_START = AndroidUtils.fromHex("ffeb00144d585300018127c1803e0000205031360101");
    private static final byte[] AUDIO_STOP = AndroidUtils.fromHex("ffeb00144d58530001010000803e0000205031360101");
    private static final byte[] AUDIO_DATA = AndroidUtils.fromHex("ffeb00004d584100016162696f6e69785f61787669657765");

    private volatile boolean keepRunning;
    private Thread thread;

    private final ByteBuffer buffer;

    private final int ringBufferSize;
    private final int readTimeout;
    private final long reconnectDelay;

    private Socket socket;

    protected MxpegStreamer(Listener listener,
                            int bufferSize,
                            int ringBufferSize,
                            int readTimeout,
                            long reconnectDelay)
    {
        buffer = ByteBuffer.allocateDirect(bufferSize);

        this.listener = listener;

        this.ringBufferSize = ringBufferSize;
        this.readTimeout = readTimeout;
        this.reconnectDelay = reconnectDelay;
    }

    protected abstract String getHost();
    protected abstract int getPort();
    protected abstract String getLogin();
    protected abstract String getPassword();

    public synchronized void start()
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
        // ugly synchronization
        synchronized (this)
        {
            if (thread == null)
                return;

            keepRunning = false;
            notifyAll();
            closeSocket();
        }

        try
        {
            thread.join();
        }
        catch (InterruptedException ie)
        {
            // do nothing
        }

        synchronized (this)
        {
            thread = null;
        }
    }

    private void closeSocket()
    {
        try
        {
            if (socket != null)
                socket.close();
        }
        catch (IOException e)
        {
            // do nothing
        }
    }

    public synchronized void forceReconnectIfNeed()
    {
        notifyAll();
    }

    public synchronized void forceReconnect()
    {
        closeSocket();
    }

    private void write(byte[] data)
    {
        packets.add(data);
    }

    private void write(String data)
    {
        write(data.getBytes());
    }

    private synchronized Socket createSocket()
    {
        return socket = new Socket();
    }

    private void run()
    {
        while (keepRunning)
        {
            boolean connected = false;
            Thread wt = null;
            final Socket socket = createSocket();

            try
            {
                socket.setSoTimeout(readTimeout);
                socket.setTcpNoDelay(true);
                InetAddress ia = InetAddress.getByName(getHost());
                socket.connect(new InetSocketAddress(ia, getPort()), 1000);

                try (InputStream is = socket.getInputStream(); OutputStream os = socket.getOutputStream())
                {
                    packets.clear();

                    wt = new Thread(() -> {
                        try
                        {
                            while (keepRunning)
                            {
                                byte[] data = packets.take();

                                if (data == END_MARKER)
                                    break;

                                os.write(data);
                                os.flush();
                            }
                        }
                        catch (IOException | InterruptedException e)
                        {
                            // write error
                        }

                        try
                        {
                            socket.close();
                            packets.clear();
                        }
                        catch (IOException e)
                        {
                            // do nothing
                        }
                    });

                    wt.start();

                    write(String.format("POST /control/eventstream.jpg HTTP/1.1\r\nHost: %s\r\nAuthorization: Basic %s\r\n\r\n",
                            ia.getHostAddress(),
                            Base64.encodeToString((getLogin() + ":" + getPassword()).getBytes(), Base64.NO_WRAP)));

                    RingBufferReader r = new RingBufferReader(is, ringBufferSize);

                    int s = r.pos();
                    //noinspection StatementWithEmptyBody
                    while (r.next() != 0x0d);

                    String codeLine = new String(r.get(s, r.pos()));
                    if (Integer.parseInt(codeLine.split(" ")[1]) != 200)
                        throw new IOException();

                    idGenerator.set(10); // reset id generator
                    listener.onStreamStart();
                    connected = true;

                    while (keepRunning)
                        readPacket(r);
                }
            }
            catch (Exception e)
            {
                // error, do nothing
            }
            finally
            {
                if (connected)
                    listener.onStreamStop();

                try
                {
                    socket.close();
                }
                catch (IOException e)
                {
                    // do nothing
                }

                synchronized (this)
                {
                    this.socket = null;
                }

                packets.add(END_MARKER);
                try
                {
                    if (wt != null)
                        wt.join();
                }
                catch (InterruptedException e)
                {
                    // do nothing
                }
            }

            try
            {
                synchronized (this)
                {
                    if (keepRunning)
                        wait(reconnectDelay);
                }
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
        if (!listener.onStreamVideoPacket(buffer, size))
            throw new IOException("Error decoding video packet");
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
        if (!listener.onStreamAudioPacket(buffer, size))
            throw new IOException("Error decoding alaw audio packet");
    }

    private void readAudioPcm(RingBufferReader r) throws IOException
    {
        int len = (r.next() << 8) | r.next();

        int start = r.pos() + 20;

        if (r.next() != 'M')
            throw new IOException();
        if (r.next() != 'X')
            throw new IOException();

        int type = r.next();

        r.move(len - 2 - 3);

        if (type == 'A')
        {
            int size = getPacket(start, r, buffer);
            if (!listener.onStreamAudioPacket(buffer, size))
                throw new IOException("Error decoding pcm audio packet");
        }

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
            listener.onMobotixEvent(new JSONObject(new String(jsonData)));
        }
        catch (JSONException e)
        {
            throw new IOException(e);
        }
    }

    public void startAudio()
    {
        write(AUDIO_START);
    }

    public void stopAudio()
    {
        write(AUDIO_STOP);
    }

    public void sendAudio(byte[] data)
    {
        byte[] packet = new byte[AUDIO_DATA.length + data.length];
        System.arraycopy(AUDIO_DATA, 0, packet, 0, AUDIO_DATA.length);
        System.arraycopy(data, 0, packet, AUDIO_DATA.length, data.length);

        int len = packet.length - 2;
        packet[2] = (byte)(len >> 8);
        packet[3] = (byte)len;

        write(packet);
    }

    /*
        Some commands:
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
     */
    private void writeCmd(String cmd)
    {
        byte[] data = cmd.getBytes();
        data = Arrays.copyOf(data, data.length + 2);
        data[data.length - 2] = 0x0a;
        data[data.length - 1] = 0x00;
        write(data);
    }

    public int nextId()
    {
        return idGenerator.getAndIncrement();
    }

    public void sendCmd(String method)
    {
        sendCmd(nextId(), method);
    }

    public void sendCmd(int id, String method)
    {
        writeCmd(jo(je("id", id), je("method", method)).toString());
    }

    public void sendCmd(String method, Object params)
    {
        sendCmd(nextId(), method, params);
    }

    public void sendCmd(int id, String method, Object params)
    {
        writeCmd(jo(je("id", id), je("method", method), je("params", params)).toString());
    }

    public void startVideo()
    {
        sendCmd("mode", ja("mxpeg"));
        sendCmd("live", ja("false"));
        sendCmd("audiooutput", ja("pcm16"));
    }
}
