package com.tvai.assistant;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CompactAssistantActivity extends MainActivity {
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
        title.setText("Mohamed TV AI  ·  المساعد");
        title.setTextColor(Color.WHITE); title.setTextSize(17); title.setGravity(Gravity.CENTER);
        panel.addView(title);
        status = new TextView(this);
        status.setText("جاهز للاستماع"); status.setTextColor(Color.rgb(120, 225, 245)); status.setTextSize(14);
        status.setGravity(Gravity.CENTER); status.setPadding(4, 8, 4, 8);
        panel.addView(status);
        response = new TextView(this);
        response.setText("تحدث لإعطاء أمر للتلفزيون"); response.setTextColor(Color.WHITE); response.setTextSize(15);
        response.setGravity(Gravity.CENTER); response.setMaxLines(3); response.setPadding(6, 4, 6, 8);
        panel.addView(response);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button mic = new Button(this); mic.setText("🎙 تحدث"); mic.setAllCaps(false);
        mic.setOnClickListener(v -> startListening());
        actions.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(this); close.setText("إغلاق"); close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        actions.addView(close, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(actions);
        setContentView(panel);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.width = (int)(getResources().getDisplayMetrics().density * 420);
        p.height = WindowManager.LayoutParams.WRAP_CONTENT;
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = 22; p.y = 90;
        getWindow().setAttributes(p);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }
}