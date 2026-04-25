package com.ai.assistance.operit.provider;

import android.view.accessibility.AccessibilityEvent;

/**
 * AIDL callback interface for delivering accessibility events from the service to the client.
 */
oneway interface IAccessibilityEventCallback {
    /**
     * Called when an accessibility event is captured.
     * @param event The captured AccessibilityEvent object.
     */
    void onAccessibilityEvent(in AccessibilityEvent event);
} 