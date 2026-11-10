package com.tvai.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class RemoteKeyProbeService extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        android.content.SharedPreferences prefs = getSharedPreferences("remote_key_probe", MODE_PRIVATE);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            prefs.edit()
                .putInt("last_key_code", event.getKeyCode())
                .putString("last_key_name", KeyEvent.keyCodeToString(event.getKeyCode()))
                .putLong("last_key_time", System.currentTimeMillis())
                .putInt("last_key_device_id", event.getDeviceId())
                .putInt("last_key_scan_code", event.getScanCode())
                .apply();

            if (prefs.getBoolean("capture_armed", false)) {
                prefs.edit()
                    .putInt("captured_key_code", event.getKeyCode())
                    .putString("captured_key_name", KeyEvent.keyCodeToString(event.getKeyCode()))
                    .putBoolean("capture_armed", false)
                    .putBoolean("capture_notice_pending", true)
                    .apply();
                return false;
            }

            int mapped = prefs.getInt("mapped_key_code", -1);
            if (prefs.getBoolean("mapping_enabled", false) && mapped >= 0 && event.getKeyCode() == mapped && event.getRepeatCount() == 0) {
                try {
                    Intent launch = new Intent(this, CompactAssistantActivity.class);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    launch.putExtra("start_voice", true);
                    startActivity(launch);
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
        }
        if (prefs.getBoolean("mapping_enabled", false) && prefs.getInt("mapped_key_code", -1) == event.getKeyCode()) {
            return true;
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}