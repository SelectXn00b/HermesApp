package com.ai.assistance.shower.wrappers;

import android.os.Build;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Method;

public final class WindowManager {

    public static final int DISPLAY_IME_POLICY_LOCAL = 0;
    public static final int DISPLAY_IME_POLICY_FALLBACK_DISPLAY = 1;
    public static final int DISPLAY_IME_POLICY_HIDE = 2;

    private static final String TAG = "ShowerWindowManager";

    private final IInterface manager;
    private Method setDisplayImePolicyMethod;

    private WindowManager(IInterface manager) {
        this.manager = manager;
    }

    public static WindowManager create() {
        IInterface manager = ServiceManager.getService("window", "android.view.IWindowManager");
        return new WindowManager(manager);
    }

    private Method getSetDisplayImePolicyMethod() throws NoSuchMethodException {
        if (setDisplayImePolicyMethod == null) {
            Class<?> cls = manager.getClass();
            if (Build.VERSION.SDK_INT >= 31) {
                setDisplayImePolicyMethod = cls.getMethod("setDisplayImePolicy", int.class, int.class);
            } else {
                setDisplayImePolicyMethod = cls.getMethod("setShouldShowIme", int.class, boolean.class);
            }
        }
        return setDisplayImePolicyMethod;
    }

    public void setDisplayImePolicy(int displayId, int displayImePolicy) {
        if (displayId < 0) {
            return;
        }
        try {
            Method method = getSetDisplayImePolicyMethod();
            if (Build.VERSION.SDK_INT >= 31) {
                method.invoke(manager, displayId, displayImePolicy);
            } else if (displayImePolicy != DISPLAY_IME_POLICY_HIDE) {
                boolean shouldShowIme = displayImePolicy == DISPLAY_IME_POLICY_LOCAL;
                method.invoke(manager, displayId, shouldShowIme);
            } else {
                Log.w(TAG, "DISPLAY_IME_POLICY_HIDE is not supported before Android 12");
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "setDisplayImePolicy method not found", e);
        } catch (Exception e) {
            Log.e(TAG, "setDisplayImePolicy failed", e);
        }
    }
}
