package com.tvai.assistant;

import android.accessibilityservice.AccessibilityService;
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
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            getSharedPreferences("remote_key_probe", MODE_PRIVATE).edit()
                .putInt("last_key_code", event.getKeyCode())
                .putString("last_key_name", KeyEvent.keyCodeToString(event.getKeyCode()))
                .putLong("last_key_time", System.currentTimeMillis())
                .putInt("last_key_device_id", event.getDeviceId())
                .putInt("last_key_scan_code", event.getScanCode())
                .apply();
        }
        // Probe only: never consume the key during identification.
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
