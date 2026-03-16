package com.xiaomo.androidforclaw.accessibility.service;

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: in-app accessibility/observer service layer.
 */


import android.graphics.Bitmap;
import android.util.Log;
import com.xiaomo.androidforclaw.aidl.ViewNodeParcelable;
import com.xiaomo.androidforclaw.aidl.IAccessibilityService;
import com.xiaomo.androidforclaw.accessibility.MediaProjectionHelper;
import kotlin.Pair;

import java.util.ArrayList;
import java.util.List;

public class AccessibilityBinder extends IAccessibilityService.Stub {
    private static final String TAG = "AccessibilityBinder";
    private static final String VERSION = "1.0.0";

    private PhoneAccessibilityService service;

    public AccessibilityBinder(PhoneAccessibilityService service) {
        this.service = service;
        Log.d(TAG, "AccessibilityBinder created with service=" + (service != null));
    }

    // Allow updating the service instance after creation
    public void setService(PhoneAccessibilityService service) {
        this.service = service;
        Log.i(TAG, "✅ Service instance updated");
    }

    @Override
    public boolean isServiceReady() {
        PhoneAccessibilityService svc = waitForService();
        if (svc == null) {
            Log.w(TAG, "isServiceReady: service is null after wait");
            return false;
        }
        return svc.getRootInActiveWindow() != null;
    }

    @Override
    public String getServiceVersion() {
        return VERSION;
    }

    @Override
    public List<ViewNodeParcelable> dumpViewTree() {
        PhoneAccessibilityService svc = waitForService();
        if (svc == null) {
            Log.w(TAG, "dumpViewTree: service is null after wait");
            return new ArrayList<>();
        }
        try {
            List<ViewNode> nodes = svc.dumpView();
            Log.d(TAG, "dumpViewTree: returning " + nodes.size() + " nodes");

            List<ViewNodeParcelable> result = new ArrayList<>();
            for (ViewNode node : nodes) {
                result.add(new ViewNodeParcelable(
                    node.getIndex(),
                    node.getText(),
                    node.getResourceId(),
                    node.getClassName(),
                    node.getPackageName(),
                    node.getContentDesc(),
                    node.getClickable(),
                    node.getEnabled(),
                    node.getFocusable(),
                    node.getFocused(),
                    node.getScrollable(),
                    node.getPoint().getX(),
                    node.getPoint().getY(),
                    node.getLeft(),
                    node.getRight(),
                    node.getTop(),
                    node.getBottom()
                ));
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to dump view tree", e);
            return new ArrayList<>();
        }
    }

    /**
     * Wait for service to become available (up to 2s).
     * Handles the timing issue where binder is created before PhoneAccessibilityService connects.
     */
    private PhoneAccessibilityService waitForService() {
        if (service != null) return service;
        // Service may not be set yet — wait briefly
        for (int i = 0; i < 4; i++) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            // Check if service was updated via setService()
            if (service != null) {
                Log.i(TAG, "Service became available after " + ((i + 1) * 500) + "ms");
                return service;
            }
            // Also check AccessibilityBinderService static instance
            PhoneAccessibilityService instance = AccessibilityBinderService.Companion.getServiceInstance();
            if (instance != null) {
                this.service = instance;
                Log.i(TAG, "Service resolved from static instance after " + ((i + 1) * 500) + "ms");
                return instance;
            }
        }
        Log.w(TAG, "Service still null after 2s wait");
        return null;
    }

    @Override
    public boolean performTap(int x, int y) {
        PhoneAccessibilityService svc = waitForService();
        if (svc == null) {
            Log.w(TAG, "performTap: service is null after wait");
            return false;
        }
        try {
            return svc.performClickAtSync(x, y, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform tap at (" + x + ", " + y + ")", e);
            return false;
        }
    }

    @Override
    public boolean performLongPress(int x, int y) {
        PhoneAccessibilityService svc = waitForService();
        if (svc == null) {
            Log.w(TAG, "performLongPress: service is null after wait");
            return false;
        }
        try {
            return svc.performClickAtSync(x, y, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform long press at (" + x + ", " + y + ")", e);
            return false;
        }
    }

    @Override
    public boolean performSwipe(int startX, int startY, int endX, int endY, long durationMs) {
        if (service == null) {
            Log.w(TAG, "performSwipe: service is null");
            return false;
        }
        try {
            service.performSwipe((float) startX, (float) startY, (float) endX, (float) endY);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform swipe", e);
            return false;
        }
    }

    @Override
    public boolean pressHome() {
        if (service == null) {
            Log.w(TAG, "pressHome: service is null");
            return false;
        }
        try {
            service.pressHomeButton();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to press home", e);
            return false;
        }
    }

    @Override
    public boolean pressBack() {
        if (service == null) {
            Log.w(TAG, "pressBack: service is null");
            return false;
        }
        try {
            service.pressBackButton();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to press back", e);
            return false;
        }
    }

    @Override
    public boolean inputText(String text) {
        if (service == null) {
            Log.w(TAG, "inputText: service is null");
            return false;
        }
        try {
            return service.inputText(text);
        } catch (Exception e) {
            Log.e(TAG, "Failed to input text: " + text, e);
            return false;
        }
    }

    @Override
    public String getCurrentPackageName() {
        if (service == null) {
            Log.w(TAG, "getCurrentPackageName: service is null");
            return "";
        }
        return service.currentPackageName;
    }

    @Override
    public boolean isMediaProjectionGranted() {
        return MediaProjectionHelper.INSTANCE.isMediaProjectionGranted();
    }

    @Override
    public String captureScreen() {
        try {
            Pair<Bitmap, String> result = MediaProjectionHelper.INSTANCE.captureScreen();
            if (result != null) {
                String path = result.getSecond();
                Log.d(TAG, "Screenshot saved to: " + path);
                return path;
            } else {
                Log.w(TAG, "Failed to capture screen");
                return "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screen", e);
            return "";
        }
    }

    @Override
    public boolean requestMediaProjection() {
        // 无法直接从 Service 请求权限，需要返回 false
        // 调用方需要通过 Activity 请求
        Log.w(TAG, "Cannot request MediaProjection from service, needs Activity");
        return false;
    }

    @Override
    public String getMediaProjectionStatus() {
        return MediaProjectionHelper.INSTANCE.getPermissionStatus();
    }
}
