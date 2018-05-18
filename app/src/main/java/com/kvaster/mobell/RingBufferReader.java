package com.kvaster.mobell;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class RingBufferReader
{
    private final int ringSize;
    private final int ringMask;

    private byte[] buffer;

    private int start = 0;
    private int end = 0;
    private int pos = 0;

    private final InputStream is;

    /**
     * @param is input stream
     * @param bufferSize minimal ring buffer size, real size will be closest 2^n size
     */
    public RingBufferReader(InputStream is, int bufferSize)
    {
        this.is = is;

        int s = 1;
        while (s < bufferSize)
            s <<= 1;
        ringSize = s;
        ringMask = ringSize - 1;

        buffer = new byte[ringSize];
    }

    private void read() throws IOException
    {
        if ((end - start) >= ringSize)
            throw new IOException(); // ring buffer overflow

        int size = Math.min(((ringSize - end - 1) & ringMask) + 1,
                ((start - end - 1) & ringMask) + 1);

        size = is.read(buffer, end & ringMask, size);

        if (size < 0)
            throw new EOFException();

        end += size;
    }

    private void readToPos(int p) throws IOException
    {
        while ((p - start) > (end - start))
            read();
    }

    public int get() throws IOException
    {
        readToPos(pos + 1);
        return buffer[pos & ringMask] & 0xff;
    }

    public int next() throws IOException
    {
        int r = get();
        move();
        return r;
    }

    public void move(int step) throws IOException
    {
        pos += step;
        if (pos < start)
            throw new IOException();
    }

    public void move() throws IOException
    {
        move(1);
    }

    public void cut(int pos) throws IOException
    {
        readToPos(pos);
        if (pos < start || pos > end)
            throw new IOException();
        start = pos;
    }

    public int pos()
    {
        return pos;
    }

    public void get(ByteBuffer packet, int from, int to) throws IOException
    {
        readToPos(to);

        if (from > to || (from - start) < 0 || (to - start) > (end - start))
            throw new IOException();

        int total = to - from;
        int size = ((ringSize - from - 1) & ringMask) + 1;
        if (size > total)
            size = total;
        packet.put(buffer, from & ringMask, size);
        if (total > size)
            packet.put(buffer, 0, total - size);
    }

    public byte[] get(int from, int to) throws IOException
    {
        readToPos(to);

        if (from > to || (from - start) < 0 || (to - start) > (end - start))
            throw new IOException();

        int total = to - from;
        int size = ((ringSize - from - 1) & ringMask) + 1;
        if (size > total)
            size = total;

        byte[] data = new byte[total];
        System.arraycopy(buffer, from & ringMask, data, 0, size);
        if (total > size)
            System.arraycopy(buffer, 0, data, size, total - size);

        return data;
    }
}
