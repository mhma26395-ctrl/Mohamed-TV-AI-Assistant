package com.tvai.assistant;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
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

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        bubble = new TextView(this);
        bubble.setText("AI");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(17);
        bubble.setGravity(Gravity.CENTER);
        bubble.setElevation(12f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.rgb(0, 180, 215));
        bg.setStroke(3, Color.WHITE);
        bubble.setBackground(bg);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(64, 64, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 24;
        params.y = 180;
        bubble.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY(); startX = params.x; startY = params.y; dragged = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX; float dy = event.getRawY() - downY;
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragged = true;
                    params.x = Math.max(0, startX - (int)dx); params.y = Math.max(0, startY + (int)dy);
                    try { windowManager.updateViewLayout(bubble, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragged) openAssistant();
                    return true;
            }
            return true;
        });
        try { windowManager.addView(bubble, params); } catch (Exception e) { stopSelf(); }
    }

    private void openAssistant() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("start_voice", true);
        startActivity(i);
    }

    @Override public void onDestroy() {
        if (bubble != null && windowManager != null) { try { windowManager.removeView(bubble); } catch (Exception ignored) {} }
        bubble = null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}