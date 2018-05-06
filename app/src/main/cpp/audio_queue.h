#ifndef __AUDIO_QUEUE__
#define __AUDIO_QUEUE__

#include <cstdint>
#include <cstdlib>
#include <string.h>
#include <atomic>

class AudioBuffer
{
public:
    AudioBuffer(size_t capacity)
    {
        buffer = capacity == 0 ? nullptr : malloc(capacity);
        this->capacity = capacity;
        this->size = 0;

        next = nullptr;
    }

    ~AudioBuffer()
    {
        free(buffer);
    }

    void set(void* data, size_t size)
    {
        if (capacity < size)
        {
            buffer = buffer ? realloc(buffer, size) : malloc(size);
            capacity = size;
        }

        this->size = size;
        memcpy(buffer, data, size);
    }

    void* buffer;
    size_t capacity;
    size_t size; // audio data size

    AudioBuffer* next;
};

class AudioBufferStack
{
public:
    AudioBufferStack()
    {
        head = nullptr;
    }

    ~AudioBufferStack()
    {
        AudioBuffer* r = head;
        while (r)
        {
            AudioBuffer* n = r->next;
            delete r;
            r = n;
        }

        head = nullptr;
    }

    AudioBuffer* get()
    {
        AudioBuffer* b = head;

        if (!b)
            return nullptr;

        while (!head.compare_exchange_strong(b, b->next));

        b->next = nullptr;

        return b;
    }

    void put(AudioBuffer* b)
    {
        AudioBuffer* r = head;

        while (true)
        {
            b->next = r;
            if (head.compare_exchange_strong(r, b))
                break;
        }
    }

private:
    std::atomic<AudioBuffer*> head;
};

class AudioBufferQueue
{
public:
    AudioBufferQueue()
    {
        head = nullptr;
        tail = nullptr;
    }

    ~AudioBufferQueue()
    {
        AudioBuffer* r = head;
        while (r)
        {
            AudioBuffer* n = r->next;
            delete r;
            r = n;
        }

        head = nullptr;
        tail = nullptr;
    }

    void put(AudioBuffer* b)
    {
        lock();

        if (tail)
        {
            tail->next = b;
            tail = b;
        }
        else
        {
            tail = b;
            head = b;
        }

        unlock();
    }

    AudioBuffer* get()
    {
        lock();

        AudioBuffer* b = head;

        if (b)
        {
            head = b->next;
            if (!head)
                tail = nullptr;

            b->next = nullptr;
        }

        unlock();

        return b;
    }

private:
    AudioBuffer* head;
    AudioBuffer* tail;

    std::atomic_bool acquired { false };

    void lock()
    {
        while (acquired.exchange(true, std::memory_order_relaxed));
        std::atomic_thread_fence(std::memory_order_acquire);
    }

    void unlock()
    {
        std::atomic_thread_fence(std::memory_order_release);
        acquired.store(false, std::memory_order_relaxed);
    }
};

#endif // __AUDIO_QUEUE__
