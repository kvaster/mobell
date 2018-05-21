package com.kvaster.mobell;

public interface CallService
{
    enum CallStatus
    {
        NONE,
        UNACCEPTED,
        SUPPRESSED,
        ACCEPTED
    }

    interface Listener
    {
        void onCallStatus(CallStatus status);
    }

    void addListener(Listener listener);

    void removeListener(Listener listener);

    void suppressCall();

    void acceptCall();

    void stopCall();

    void openDoor();
}
