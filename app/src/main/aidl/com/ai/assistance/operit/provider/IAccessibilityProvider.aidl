package com.ai.assistance.operit.provider;

interface IAccessibilityProvider {
    String getUiHierarchy();
    boolean performClick(int x, int y);
    boolean performLongPress(int x, int y);
    boolean performGlobalAction(int actionId);
    boolean performSwipe(int startX, int startY, int endX, int endY, long duration);
    String findFocusedNodeId();
    boolean setTextOnNode(String nodeId, String text);
    boolean takeScreenshot(String path, String format);
    boolean isAccessibilityServiceEnabled();
    String getCurrentActivityName();
} 