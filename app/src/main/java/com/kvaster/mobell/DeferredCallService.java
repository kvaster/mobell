package com.kvaster.mobell;

public class DeferredCallService implements CallService {
    private Listener listener;
    private boolean isAdd;

    public void perform(CallService service) {
        if (listener != null) {
            if (isAdd) {
                service.addListener(listener);
            } else {
                service.removeListener(listener);
            }

            listener = null;
        }
    }

    @Override
    public void addListener(Listener listener) {
        this.listener = listener;
        isAdd = true;
    }

    @Override
    public void removeListener(Listener listener) {
        this.listener = listener;
        isAdd = false;
    }

    @Override
    public void suppressCall() {
        // do nothing
    }

    @Override
    public void acceptCall() {
        // do nothing
    }

    @Override
    public void stopCall() {
        // do nothing
    }

    @Override
    public void openDoor() {
        // do nothing
    }
}
