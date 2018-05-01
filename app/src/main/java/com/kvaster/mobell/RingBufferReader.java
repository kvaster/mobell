package com.kvaster.mobell;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class RingBufferReader
{
    private static final int SIZE = 1024 * 1024 * 8; // 8mb ring buffer for incoming packets
    private static final int MASK = SIZE - 1; // ring buffer size is 2^n -> we can use mask for positioning

    private byte[] buffer = new byte[SIZE];

    private int start = 0;
    private int end = 0;
    private int pos = 0;

    private final InputStream is;

    public RingBufferReader(InputStream is)
    {
        this.is = is;
    }

    private void read() throws IOException
    {
        if ((end - start) >= SIZE)
            throw new IOException(); // ring buffer overflow

        int size = Math.min(((SIZE - end - 1) & MASK) + 1,
                ((start - end - 1) & MASK) + 1);

        size = is.read(buffer, end & MASK, size);
        if (size < 0)
            throw new EOFException();

        end += size;
    }

    private void readToPos() throws IOException
    {
        while ((pos - start) >= (end - start))
            read();
    }

    public int get() throws IOException
    {
        readToPos();
        return buffer[pos & MASK] & 0xff;
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
        readToPos();

        if (from > to || (from - start) < 0 || (to - start) > (end - start))
            throw new IOException();

        int total = to - from;
        int size = ((SIZE - from - 1) & MASK) + 1;
        if (size > total)
            size = total;
        packet.put(buffer, from & MASK, size);
        if (total > size)
            packet.put(buffer, 0, total - size);
    }
}
