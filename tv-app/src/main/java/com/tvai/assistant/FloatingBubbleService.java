package com.tvai.assistant;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingBubbleService extends Service {
    private WindowManager windowManager;
    private TextView bubble;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int startX, startY;
    private boolean dragged;
    private int bubbleSize;
    private int moveStep;

    @Override public void onCreate() {
        super.onCreate();
        startAssistantForeground();
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        bubbleSize = (int)(64 * getResources().getDisplayMetrics().density);
        moveStep = (int)(36 * getResources().getDisplayMetrics().density);

        bubble = new TextView(this);
        bubble.setText("AI");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(17);
        bubble.setGravity(Gravity.CENTER);
        bubble.setElevation(12f);
        bubble.setFocusable(true);
        bubble.setFocusableInTouchMode(true);
        bubble.setContentDescription("AI assistant. Use arrow keys to move and OK to open.");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.rgb(0, 180, 215));
        bg.setStroke(3, Color.WHITE);
        bubble.setBackground(bg);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
            bubbleSize, bubbleSize, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = (int)(24 * getResources().getDisplayMetrics().density);
        params.y = (int)(180 * getResources().getDisplayMetrics().density);

        bubble.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveBubble(moveStep, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveBubble(-moveStep, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveBubble(0, -moveStep);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveBubble(0, moveStep);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    openAssistant();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    // Do not trap BACK; let the system or foreground window handle it.
                    return false;
                default:
                    // Preserve unrelated remote keys for the focused app/system.
                    return false;
            }
        });

        bubble.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    dragged = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragged = true;
                    params.x = clamp(startX - (int)dx, 0, maxX());
                    params.y = clamp(startY + (int)dy, 0, maxY());
                    try { windowManager.updateViewLayout(bubble, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragged) openAssistant();
                    bubble.requestFocus();
                    return true;
            }
            return true;
        });

        try {
            windowManager.addView(bubble, params);
            bubble.requestFocus();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private int maxX() {
        return Math.max(0, getResources().getDisplayMetrics().widthPixels - bubbleSize);
    }

    private int maxY() {
        return Math.max(0, getResources().getDisplayMetrics().heightPixels - bubbleSize);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void moveBubble(int dx, int dy) {
        if (params == null || bubble == null) return;
        params.x = clamp(params.x + dx, 0, maxX());
        params.y = clamp(params.y + dy, 0, maxY());
        try {
            windowManager.updateViewLayout(bubble, params);
            bubble.requestFocus();
        } catch (Exception ignored) {}
    }

    private void startAssistantForeground() {
        String channelId = "tvai_floating_assistant";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId, "TV AI Assistant", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Mohamed TV AI Assistant").setContentText("استخدم الأسهم لتحريك زر AI وOK لفتحه").setOngoing(true).build()
            : new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Mohamed TV AI Assistant").setOngoing(true).build();
        startForeground(4102, notification);
    }

    private void openAssistant() {
        Intent i = new Intent(this, CompactAssistantActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        i.putExtra("start_voice", true);
        startActivity(i);
    }

    @Override public void onDestroy() {
        if (bubble != null && windowManager != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) {}
        }
        bubble = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
