package com.kvaster.mobell;

import android.view.MotionEvent;

public interface GlApp {
    void start();
    void stop();

    void suspend();
    void resume();

    void pause();
    void unpause();

    void canvasCreated(int width, int height, int dpi, float density);
    void canvasSizeChanged(int width, int height);

    void update();
    void draw();

    boolean onTouchEvent(MotionEvent event);
    void onBackButtonPressed();

    void setGlView(GlView view);
}
