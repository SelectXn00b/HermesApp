package com.ai.assistance.shower.wrappers;

import com.ai.assistance.shower.shell.FakeContext;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ActivityManager {

    private final IInterface manager;
    private Method startActivityAsUserMethod;
    private Method forceStopPackageMethod;

    static ActivityManager create() {
        try {
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            Method getDefaultMethod = cls.getDeclaredMethod("getDefault");
            IInterface am = (IInterface) getDefaultMethod.invoke(null);
            return new ActivityManager(am);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private ActivityManager(IInterface manager) {
        this.manager = manager;
    }

    private Method getStartActivityAsUserMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (startActivityAsUserMethod == null) {
            Class<?> iApplicationThreadClass = Class.forName("android.app.IApplicationThread");
            Class<?> profilerInfo = Class.forName("android.app.ProfilerInfo");
            startActivityAsUserMethod = manager.getClass()
                    .getMethod("startActivityAsUser", iApplicationThreadClass, String.class, Intent.class, String.class,
                            IBinder.class, String.class, int.class, int.class, profilerInfo, Bundle.class, int.class);
        }
        return startActivityAsUserMethod;
    }

    public int startActivity(Intent intent, Bundle options) {
        try {
            Method method = getStartActivityAsUserMethod();
            return (int) method.invoke(
                    manager,
                    null,
                    FakeContext.PACKAGE_NAME,
                    intent,
                    null,
                    null,
                    null,
                    0,
                    0,
                    null,
                    options,
                    -2
            );
        } catch (Throwable e) {
            return 0;
        }
    }

    private Method getForceStopPackageMethod() throws NoSuchMethodException {
        if (forceStopPackageMethod == null) {
            forceStopPackageMethod = manager.getClass().getMethod("forceStopPackage", String.class, int.class);
        }
        return forceStopPackageMethod;
    }

    public void forceStopPackage(String packageName) {
        try {
            Method method = getForceStopPackageMethod();
            method.invoke(manager, packageName, -2);
        } catch (Throwable e) {
            // ignore
        }
    }
}
