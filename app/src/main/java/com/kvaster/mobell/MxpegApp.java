package com.kvaster.mobell;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Objects;

import static com.kvaster.mobell.AndroidUtils.TAG;

public class MxpegApp implements GlApp, MxpegStreamer.Listener, AudioRecorderListener, CallService.Listener
{
    private boolean needResume;
    private final MxpegStreamer streamer;

    private boolean started;
    private boolean recordingEnabled;
    private boolean recordingRequested;
    private boolean recordingInProgress;

    private volatile boolean volumeEnabled;

    private CallService callService;

    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private volatile boolean actionGestureInProgress;
    private volatile float scale;
    private volatile float panX;
    private volatile float panY;

    private int canvasWidth;
    private int canvasHeight;

    public MxpegApp(Context ctx, String host, int port, String login, String password, DisplayMetrics displayMetrics)
    {
        streamer = new MxpegStreamer(host, port, login, password, this,
                1024 * 1024 * 2, // 2mb packets - should be really enough even for 6mpx data
                1024 * 1024 * 8, // 8mb ring buffer to hold large amount of data
                1000, // on audio/video stream huge delays are really bad -> wait only 1s
                500 // reconnect almost immediatelly with live streams
        );

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
            {
                if (scaleGestureDetector.isInProgress() || actionGestureInProgress)
                    return false;

                panX -= distanceX;
                panY -= distanceY;
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e)
            {
                if (scaleGestureDetector.isInProgress() || actionGestureInProgress)
                    return false;

                resetSize();
                return true;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            float startPanX;
            float startPanY;
            float startFocusX;
            float startFocusY;

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector)
            {
                if (actionGestureInProgress)
                    return false;

                startFocusX = detector.getFocusX();
                startFocusY = detector.getFocusY();
                startPanX = panX;
                startPanY = panY;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector)
            {
                if (actionGestureInProgress)
                    return false;

                scale = Math.max(0.5f, Math.min(2.0f, scale * detector.getScaleFactor()));
                panX = startPanX + detector.getFocusX() - startFocusX;
                panY = startPanY + detector.getFocusY() - startFocusY;
                return true;
            }
        });

        int w = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
        int iw = w / 6;
        iconDist = iw / 7;
        iconTexSize = iw >= 256 ? 256 : 128;
        iconSize = iw;

        toolIconSize = iconSize * 2 / 3;
        toolIconDist = iconDist / 4;

        loadIcons(ctx);
    }

    public void onServiceBind(CallService service)
    {
        // Service bind is called in main ui thread and is initiated in async mode during activity onCreate method.
        // This means it should be always called BEFORE app's start method.
        callService = service;
    }

    public void onServiceUnbind()
    {
        // do nothing
    }

    public void resetSize()
    {
        scale = 1;
        panX = panY = 0;
    }


    public synchronized void allowRecording()
    {
        recordingEnabled = true;
    }

    private synchronized void requestStartRecording()
    {
        if (recordingEnabled)
        {
            recordingRequested = true;
            if (started)
                startRecording();
        }
    }

    private synchronized void requestStopRecording()
    {
        recordingRequested = false;
        stopRecording();
    }

    private synchronized void startRecording()
    {
        if (!recordingInProgress)
        {
            recordingInProgress = true;
            streamer.startAudio();
            MxpegNative.startRecord(this);
        }
    }

    private synchronized void stopRecording()
    {
        if (recordingInProgress)
        {
            recordingInProgress = false;
            MxpegNative.stopRecord();
            streamer.stopAudio();
        }
    }

    @Override
    public void start()
    {
        MxpegNative.start();
        bindResources();

        streamer.start();

        callService.addListener(this);
    }

    @Override
    public void stop()
    {
        callService.removeListener(this);

        streamer.stop();

        unbindResources();
        MxpegNative.stop();
    }

    @Override
    public void suspend()
    {
        needResume = true; // sometimes we can receive canvasSizeChanged before resume...
        streamer.stop();

        unbindResources();
        MxpegNative.suspend();
    }

    @Override
    public void resume()
    {
        // real resume will be done only after surface creation
        needResume = true;
        streamer.start();
    }

    @Override
    public void pause()
    {
        // do nothing
    }

    @Override
    public void unpause()
    {
        // do nothing
    }

    @Override
    public void canvasCreated(int width, int height, int dpi, float density)
    {
        // do nothing
    }

    @Override
    public void canvasSizeChanged(int width, int height)
    {
        if (needResume)
        {
            needResume = false;
            realResume();
            //streamer.start();
        }

        resetSize();
        scale = 1;
        panX = 0;
        panY = 0;

        canvasWidth = width;
        canvasHeight = height;

        MxpegNative.canvasSizeChanged(width, height);
    }

    @Override
    public void update()
    {
        MxpegNative.update();
    }

    @Override
    public void draw()
    {
        MxpegNative.draw(scale, panX, panY);
        drawActions();
    }

    private void realResume()
    {
        MxpegNative.resume();
        bindResources();
    }

    @Override
    public synchronized void onStreamStart()
    {
        started = true;

        MxpegNative.onStreamStart();
        streamer.startVideo();

        if (recordingEnabled && recordingRequested)
            startRecording();
    }

    @Override
    public synchronized void onStreamStop()
    {
        if (recordingEnabled)
            stopRecording();

        MxpegNative.onStreamStop();
    }

    @Override
    public boolean onStreamVideoPacket(ByteBuffer packet, int size)
    {
        return MxpegNative.onStreamVideoPacket(packet, size);
    }

    @Override
    public boolean onStreamAudioPacket(ByteBuffer packet, int size)
    {
        return !volumeEnabled || MxpegNative.onStreamAudioPacket(packet, size);
    }

    @Override
    public void onMobotixEvent(JSONObject event)
    {
        // do nothing by default
    }

    @Override
    public void onAudioData(byte[] data)
    {
        streamer.sendAudio(data);
    }

    public void onBackButtonPressed()
    {
        // TODO
    }

    public boolean onTouchEvent(MotionEvent event)
    {
        boolean ok = canProcess(event);

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN)
        {
            if (onActionFocus((int)event.getX(), (int)event.getY()))
                actionGestureInProgress = true;
        }
        else if (actionGestureInProgress)
        {
            if (action == MotionEvent.ACTION_UP)
            {
                actionGestureInProgress = false;
                onActionPerform((int)event.getX(), (int)event.getY());
            }
        }

        ok |= scaleGestureDetector.onTouchEvent(event);
        ok |= gestureDetector.onTouchEvent(event);

        return ok;
    }

    private static boolean canProcess(MotionEvent event)
    {
        switch (event.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }

        return false;
    }

    ////////////////////////////////////////////////////////////////
    // actions

    private interface IconSupplier
    {
        Icon getIcon();
    }

    private interface DisabledSupplier
    {
        boolean isDisabled();
    }

    private static class Action
    {
        int x;
        int y;
        int w;
        int h;
        boolean focused;

        Runnable action;
        IconSupplier iconSupplier;
        DisabledSupplier disabledSupplier;

        Action(Runnable action, Icon icon)
        {
            this(action, () -> icon);
        }

        Action(Runnable action, IconSupplier iconSupplier)
        {
            this(action, iconSupplier, null);
        }

        Action(Runnable action, IconSupplier iconSupplier, DisabledSupplier disabledSupplier)
        {
            this.action = action;
            this.iconSupplier = iconSupplier;
            this.disabledSupplier = disabledSupplier == null ? () -> false : disabledSupplier;
        }

        boolean hit(int px, int py)
        {
            return (px >= x && px < (x + w) && py >= y && py < (y + h));
        }

        Icon getIcon()
        {
            return iconSupplier.getIcon();
        }

        void action()
        {
            action.run();
        }

        boolean isDisabled()
        {
            return disabledSupplier.isDisabled();
        }

        void resetSize()
        {
            x = y = w = h = 0;
        }
    }

    private Action[] actions = {};
    private Action settingsAction = new Action(() -> {}, Icon.SETTINGS);
    private Action sizeAction = new Action(() -> {
        scale = 1;
        panX = 0;
        panY = 0;
    }, Icon.DEFAULT_SIZE);

    private synchronized void setActions(Action... actions)
    {
        for (Action a : this.actions)
        {
            a.resetSize();
        }

        this.actions = actions;
    }

    private synchronized void drawActions()
    {
        int x = (canvasWidth - (iconSize * actions.length + iconDist * (actions.length - 1))) / 2;
        int y = canvasHeight - iconSize - toolIconDist;

        for (Action a : actions)
        {
            a.x = x;
            a.y = y;
            a.w = iconSize;
            a.h = iconSize;

            drawAction(a);

            x += iconSize + iconDist;
        }

        settingsAction.x = canvasWidth - toolIconSize - toolIconDist;
        settingsAction.y = canvasHeight - toolIconSize - toolIconDist;
        settingsAction.w = settingsAction.h = toolIconSize;
        drawAction(settingsAction);

        if (scale == 1 && panX == 0 && panY == 0)
        {
            sizeAction.resetSize();
        }
        else
        {
            sizeAction.x = toolIconDist;
            sizeAction.y = canvasHeight - toolIconSize - toolIconDist;
            sizeAction.w = sizeAction.h = toolIconSize;
            drawAction(sizeAction);
        }
    }

    private void drawAction(Action a)
    {
        drawIcon(a.getIcon().ordinal(), a.x, a.y, a.w, a.h, a.isDisabled() ? IconStyle.DISABLED : (a.focused ? IconStyle.FOCUSED : IconStyle.NORMAL));
    }

    private interface ActionConsumer
    {
        boolean onAction(Action action);
    }

    private boolean forEachAction(ActionConsumer c)
    {
        boolean ok = false;
        for (Action a : actions)
            ok |= c.onAction(a);
        ok |= c.onAction(settingsAction);
        ok |= c.onAction(sizeAction);
        return ok;
    }

    private synchronized void onActionCancel()
    {
        forEachAction((a) -> a.focused = false);
    }

    private synchronized boolean onActionFocus(int x, int y)
    {
        return forEachAction((a) -> a.focused = a.hit(x, y));
    }

    private synchronized boolean onActionPerform(int x, int y)
    {
        return forEachAction((a) -> {
            boolean act = a.focused && a.hit(x, y);
            if (act)
                a.action();
            a.focused = false;
            return act;
        });
    }

    @Override
    public synchronized void onCallStatus(CallService.CallStatus status)
    {
        switch (status)
        {
            case DISCONNECTED:
                setActions();
                break;

            case IDLE:
                setActions(
                        createVolumeOnOffAction(false),
                        createMicOnOffAction(false),
                        createDoorOpenAction(false, false)
                );
                break;

            case SUPPRESSED:
                setActions(
                        createVolumeOnOffAction(true),
                        createMicOnOffAction(false),
                        createDoorOpenAction(false, false)
                );
                break;

            case UNACCEPTED:
                setActions(
                        createAcceptCallAction(),
                        createRejectCallAction(),
                        createDoorOpenAction(true, true)
                );
                break;

            case ACCEPTED:
                setActions(
                        createRejectCallAction(),
                        createVolumeOnOffAction(true),
                        createMicOnOffAction(true),
                        createDoorOpenAction(false, true)
                );
                break;
        }
    }

    private Action createRejectCallAction()
    {
        return new Action(() -> callService.stopCall(), Icon.PHONE_REJECT);
    }

    private Action createAcceptCallAction()
    {
        return new Action(() -> callService.acceptCall(), Icon.PHONE_ACCEPT);
    }

    private Action createDoorOpenAction(boolean acceptCall, boolean rejectCall)
    {
        return new Action(() -> {
            if (acceptCall)
                callService.acceptCall();
            if (rejectCall)
                callService.stopCall();
            callService.openDoor();
        }, Icon.DOOR_OPEN);
    }

    private Action createVolumeOnOffAction(boolean enabled)
    {
        volumeEnabled = enabled;

        return new Action(() -> {
            volumeEnabled = ! volumeEnabled;
        }, () -> volumeEnabled ? Icon.VOLUME_ON : Icon.VOLUME_OFF);
    }

    private Action createMicOnOffAction(boolean enabled)
    {
        if (recordingEnabled)
        {
            if (enabled)
                requestStartRecording();
            else
                requestStopRecording();
        }

        return new Action(() -> {
            if (recordingEnabled)
            {
                if (recordingRequested)
                    requestStopRecording();
                else
                    requestStartRecording();
            }
        }, () -> recordingRequested ? Icon.MIC_ON : Icon.MIC_OFF, () -> !recordingEnabled);
    }

    ////////////////////////////////////////////////////////////////
    // java ui part

    private enum Icon
    {
        PHONE_ACCEPT(R.drawable.ic_phone_accept),
        PHONE_REJECT(R.drawable.ic_phone_reject),
        MIC_ON(R.drawable.ic_mic_on),
        MIC_OFF(R.drawable.ic_mic_off),
        VOLUME_ON(R.drawable.ic_volume_on),
        VOLUME_OFF(R.drawable.ic_volume_off),
        DOOR_OPEN(R.drawable.ic_door_open),
        SETTINGS(R.drawable.ic_settings),
        DEFAULT_SIZE(R.drawable.ic_default_size);

        int resId;

        Icon(int resId)
        {
            this.resId = resId;
        }
    }

    private enum IconStyle
    {
        NORMAL,
        FOCUSED,
        DISABLED
    }

    private int iconTexSize;
    private int iconSize;
    private int iconDist;

    private int toolIconSize;
    private int toolIconDist;


    private Bitmap[] iconsBmps;
    private int[] iconsTexs;

    private int[] vbo = new int[1];
    private int program;
    private int vertAttr;
    private int texAttr;
    private int colorAttr;
    private int scaleAttr;
    private int posAttr;

    private static final float[] STYLE_NORMAL = new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private static final float[] STYLE_FOCUSED = new float[] {
            1.2f, 0, 0, 0.2f,
            0, 1.2f, 0, 0.2f,
            0, 0, 1.2f, 0.2f,
            0, 0, 0, 1
    };

    private static final float[] STYLE_DISABLED = new float[] {
            0.2126f, 0.7152f, 0.0722f, 0,
            0.2126f, 0.7152f, 0.0722f, 0,
            0.2126f, 0.7152f, 0.0722f, 0,
            0, 0, 0, 0.5f
    };

    private Bitmap loadIcon(Context ctx, int resId, int width, int height)
    {
        Drawable vd = Objects.requireNonNull(ctx.getDrawable(resId));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vd.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vd.draw(canvas);
        return bitmap;
    }

    private void loadIcons(Context ctx)
    {
        Icon[] icons = Icon.values();
        final int count = icons.length;
        iconsBmps = new Bitmap[count];

        for (int i = 0; i < count; i++)
            iconsBmps[i] = loadIcon(ctx, icons[i].resId, iconTexSize, iconTexSize);
    }

    private void bindResources()
    {
        // textures
        final int count = iconsBmps.length;
        iconsTexs = new int[count];
        GLES20.glGenTextures(count, iconsTexs, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        for (int i = 0; i < count; i++)
        {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconsTexs[i]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, iconsBmps[i], GLES20.GL_UNSIGNED_BYTE, 0);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }

        // vbo
        float[] data = new float[] {
                0,  1,  0, 0, // top left
                1,  1,  1, 0, // top right
                0,  0,  0, 1, // bottom left
                1,  1,  1, 0, // top right
                0,  0,  0, 1, // bottom left
                1,  0,  1, 1  // bottom right
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);

        GLES20.glGenBuffers(1, vbo, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);

        // shaders
        final String FRAG_SHADER =
                "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "\n" +
                "uniform sampler2D texture;\n" +
                "uniform mat4 p_color;\n" +
                "varying vec2 texCoord;\n" +
                "\n" +
                "void main()\n" +
                "{\n" +
                "gl_FragColor = texture2D(texture, texCoord) * p_color;\n" +
                "}\n";

        final String VERT_SHADER =
                "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "\n" +
                "attribute vec2 a_position_0;\n" +
                "attribute vec2 a_texcoord_0;\n" +
                "attribute vec2 p_scale;\n" +
                "attribute vec2 p_pos;\n" +
                "varying vec2 texCoord;\n" +
                "\n" +
                "void main()\n" +
                "{\n" +
                "texCoord = a_texcoord_0;\n" +
                "gl_Position = vec4(a_position_0 * p_scale + p_pos, 0.0, 1.0);\n" +
                "}\n";

        program = GLES20.glCreateProgram();

        int vertShader = compileShader(VERT_SHADER, GLES20.GL_VERTEX_SHADER);
        int fragShader = compileShader(FRAG_SHADER, GLES20.GL_FRAGMENT_SHADER);

        GLES20.glAttachShader(program, vertShader);
        GLES20.glAttachShader(program, fragShader);

        GLES20.glLinkProgram(program);

        GLES20.glDetachShader(program, vertShader);
        GLES20.glDetachShader(program, fragShader);

        GLES20.glDeleteShader(vertShader);
        GLES20.glDeleteShader(fragShader);

        GLES20.glUseProgram(program);

        vertAttr = GLES20.glGetAttribLocation(program, "a_position_0");
        texAttr = GLES20.glGetAttribLocation(program, "a_texcoord_0");
        scaleAttr = GLES20.glGetAttribLocation(program, "p_scale");
        posAttr = GLES20.glGetAttribLocation(program, "p_pos");
        colorAttr = GLES20.glGetUniformLocation(program, "p_color");
    }

    private void unbindResources()
    {
        GLES20.glDeleteTextures(iconsTexs.length, iconsTexs, 0);
        GLES20.glDeleteProgram(program);
        GLES20.glDeleteBuffers(1, vbo, 0);
    }

    private static int compileShader(String source, int type)
    {
        int handle = GLES20.glCreateShader(type);
        GLES20.glShaderSource(handle, source);
        GLES20.glCompileShader(handle);
        return handle;
    }

    private void drawIcon(int id, int x, int y, int w, int h, IconStyle style)
    {
        GLES20.glUseProgram(program);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconsTexs[id]);
        GLES20.glEnable(GLES20.GL_TEXTURE_2D);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        float ws = (float)w * 2 / canvasWidth;
        float hs = (float)h * 2 / canvasHeight;

        float xp = (float)(x - canvasWidth / 2) * 2 / canvasWidth;
        float yp = (float)(canvasHeight / 2 - y - h) * 2 / canvasHeight;

        GLES20.glVertexAttrib2f(scaleAttr, ws, hs);
        GLES20.glVertexAttrib2f(posAttr, xp, yp);

        switch (style)
        {
            case NORMAL:
                GLES20.glUniformMatrix4fv(colorAttr, 1, false, STYLE_NORMAL, 0);
                break;

            case FOCUSED:
                GLES20.glUniformMatrix4fv(colorAttr, 1, false, STYLE_FOCUSED, 0);
                break;

            case DISABLED:
                GLES20.glUniformMatrix4fv(colorAttr, 1, false, STYLE_DISABLED, 0);
                break;
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glEnableVertexAttribArray(vertAttr);
        GLES20.glVertexAttribPointer(vertAttr, 2, GLES20.GL_FLOAT, false, 4 * 4, 0);
        GLES20.glEnableVertexAttribArray(texAttr);
        GLES20.glVertexAttribPointer(texAttr, 2, GLES20.GL_FLOAT, false, 4 * 4, 2 * 4);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(vertAttr);
        GLES20.glDisableVertexAttribArray(texAttr);
        GLES20.glDisable(GLES20.GL_TEXTURE_2D);
        GLES20.glDisable(GLES20.GL_BLEND);
    }
}
