package com.tvai.assistant;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CompactAssistantActivity extends MainActivity {
    private ScrollView answerScroll;
    private Button answerUp, answerDown, mic, close;

    @Override protected void buildUi() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 14, 18, 14);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(9, 29, 52));
        bg.setCornerRadius(22);
        bg.setStroke(2, Color.rgb(0, 190, 220));
        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("Mohamed TV AI · المساعد");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        status = new TextView(this);
        status.setText("جاهز للاستماع");
        status.setTextColor(Color.rgb(120, 225, 245));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(4, 8, 4, 8);
        panel.addView(status);

        transcript = new TextView(this);
        transcript.setVisibility(View.GONE);
        panel.addView(transcript);

        answerScroll = new ScrollView(this);
        answerScroll.setId(View.generateViewId());
        answerScroll.setFillViewport(true);
        answerScroll.setFocusable(true);
        answerScroll.setFocusableInTouchMode(true);
        answerScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        answerScroll.setContentDescription("منطقة إجابة المساعد. استخدم السهمين لأعلى وأسفل لقراءة بقية الإجابة");
        answerScroll.setVerticalScrollBarEnabled(true);
        answerScroll.setSmoothScrollingEnabled(true);
        int answerHeight = (int)(getResources().getDisplayMetrics().density * 230);
        answerScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, answerHeight));

        response = new TextView(this);
        response.setText("تحدث لإعطاء أمر للتلفزيون");
        response.setTextColor(Color.WHITE);
        response.setTextSize(16);
        response.setGravity(Gravity.TOP | Gravity.RIGHT);
        response.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        response.setMaxLines(Integer.MAX_VALUE);
        response.setSingleLine(false);
        response.setFocusable(false);
        response.setPadding(8, 8, 8, 12);
        answerScroll.addView(response, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        answerScroll.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (answerScroll.canScrollVertically(1)) {
                    answerScroll.smoothScrollBy(0, (int)(150 * getResources().getDisplayMetrics().density));
                } else if (answerUp != null) {
                    answerUp.requestFocus();
                }
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                if (answerScroll.canScrollVertically(-1)) {
                    answerScroll.smoothScrollBy(0, -(int)(150 * getResources().getDisplayMetrics().density));
                    return true;
                }
                return false;
            }
            return false;
        });
        panel.addView(answerScroll);

        LinearLayout answerControls = new LinearLayout(this);
        answerControls.setGravity(Gravity.CENTER);
        answerUp = new Button(this);
        answerUp.setId(View.generateViewId());
        answerUp.setText("↑ أعلى");
        answerUp.setAllCaps(false);
        answerUp.setOnClickListener(v -> answerScroll.smoothScrollBy(0,
                -(int)(180 * getResources().getDisplayMetrics().density)));
        answerControls.addView(answerUp, new LinearLayout.LayoutParams(0, -2, 1));

        answerDown = new Button(this);
        answerDown.setId(View.generateViewId());
        answerDown.setText("↓ باقي الإجابة");
        answerDown.setAllCaps(false);
        answerDown.setOnClickListener(v -> answerScroll.smoothScrollBy(0,
                (int)(180 * getResources().getDisplayMetrics().density)));
        answerControls.addView(answerDown, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(answerControls);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        mic = new Button(this);
        mic.setId(View.generateViewId());
        mic.setText("🎙 تحدث");
        mic.setAllCaps(false);
        mic.setOnClickListener(v -> startListening());
        actions.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));

        close = new Button(this);
        close.setId(View.generateViewId());
        close.setText("إغلاق النافذة");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        actions.addView(close, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(actions);

        // Explicit D-pad path: answer -> scroll controls -> voice/close.
        answerScroll.setNextFocusDownId(answerUp.getId());
        answerUp.setNextFocusUpId(answerScroll.getId());
        answerDown.setNextFocusUpId(answerScroll.getId());
        answerUp.setNextFocusRightId(answerDown.getId());
        answerDown.setNextFocusLeftId(answerUp.getId());
        answerUp.setNextFocusDownId(mic.getId());
        answerDown.setNextFocusDownId(close.getId());
        mic.setNextFocusUpId(answerUp.getId());
        close.setNextFocusUpId(answerDown.getId());
        mic.setNextFocusRightId(close.getId());
        close.setNextFocusLeftId(mic.getId());
        answerUp.setFocusable(true);
        answerDown.setFocusable(true);
        mic.setFocusable(true);
        close.setFocusable(true);

        setContentView(panel);
        answerScroll.requestFocus();
    }

    @Override protected void reply(String message) {
        super.reply(message);
        if (response != null) {
            boolean arabic = message != null && message.matches("(?s).*[\\u0600-\\u06FF].*");
            response.setTextDirection(arabic ? View.TEXT_DIRECTION_FIRST_STRONG_RTL : View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            response.setGravity(arabic ? Gravity.TOP | Gravity.RIGHT : Gravity.TOP | Gravity.LEFT);
            if (answerScroll != null) answerScroll.post(() -> answerScroll.scrollTo(0, 0));
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.width = (int)(getResources().getDisplayMetrics().density * 420);
        p.height = (int)(getResources().getDisplayMetrics().density * 520);
        p.height = Math.min(p.height, getResources().getDisplayMetrics().heightPixels - (int)(40 * getResources().getDisplayMetrics().density));
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = 22;
        p.y = 90;
        getWindow().setAttributes(p);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && answerScroll != null && !hasUsableFocus()) answerScroll.requestFocus();
    }

    private boolean hasUsableFocus() {
        View focused = getCurrentFocus();
        return focused != null && focused.isShown() && focused.isFocusable();
    }

    @Override public void onBackPressed() {
        finish();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (getSharedPreferences("floating_assistant", MODE_PRIVATE).getBoolean("enabled", false)) {
            Intent show = new Intent(this, FloatingBubbleService.class);
            show.setAction("com.tvai.assistant.FLOATING_SHOW");
            try { startService(show); } catch (Exception ignored) {}
        }
    }
}
