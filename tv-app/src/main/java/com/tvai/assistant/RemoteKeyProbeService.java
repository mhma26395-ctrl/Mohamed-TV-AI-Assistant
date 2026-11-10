package com.tvai.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class RemoteKeyProbeService extends AccessibilityService {
    private boolean isProtectedSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_ASSIST
                || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
                || keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == KeyEvent.KEYCODE_TV_INPUT
                || keyCode == KeyEvent.KEYCODE_GUIDE
                || keyCode == KeyEvent.KEYCODE_SETTINGS;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // The XML declares canRequestFilterKeyEvents; the runtime flag is also
        // required before Android delivers global key events to onKeyEvent().
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Exception ignored) { }
        android.content.SharedPreferences prefs = getSharedPreferences("remote_key_probe", MODE_PRIVATE);
        if (isProtectedSystemKey(prefs.getInt("mapped_key_code", -1))) {
            prefs.edit().putBoolean("mapping_enabled", false)
                    .putInt("mapped_key_code", -1)
                    .putString("mapped_key_name", "لم يتم اختيار زر").apply();
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int keyCode = event.getKeyCode();
        if (isProtectedSystemKey(keyCode)) return false;
        android.content.SharedPreferences prefs = getSharedPreferences("remote_key_probe", MODE_PRIVATE);

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            prefs.edit().putInt("last_key_code", keyCode)
                    .putString("last_key_name", KeyEvent.keyCodeToString(keyCode))
                    .putLong("last_key_time", System.currentTimeMillis()).apply();

            if (prefs.getBoolean("capture_armed", false)) {
                String keyName = KeyEvent.keyCodeToString(keyCode);
                prefs.edit().putInt("captured_key_code", keyCode)
                        .putString("captured_key_name", keyName)
                        .putBoolean("capture_armed", false)
                        .putBoolean("capture_notice_pending", true).apply();
                try {
                    Intent result = new Intent(this, MainActivity.class);
                    result.setAction("com.tvai.assistant.REMOTE_KEY_CAPTURED");
                    result.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    result.putExtra("captured_key_code", keyCode);
                    result.putExtra("captured_key_name", keyName);
                    startActivity(result);
                } catch (Exception ignored) { }
                return false;
            }

            int mapped = prefs.getInt("mapped_key_code", -1);
            if (prefs.getBoolean("mapping_enabled", false) && mapped >= 0 && keyCode == mapped) {
                if (event.getRepeatCount() == 0) {
                    try {
                        Intent launch = new Intent(this, CompactAssistantActivity.class);
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        launch.putExtra("start_voice", true);
                        startActivity(launch);
                    } catch (Exception ignored) { return false; }
                }
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_UP
                && prefs.getBoolean("mapping_enabled", false)
                && prefs.getInt("mapped_key_code", -1) == keyCode) return true;
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
