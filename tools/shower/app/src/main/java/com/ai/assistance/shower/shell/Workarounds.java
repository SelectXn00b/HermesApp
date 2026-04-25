package com.ai.assistance.shower.shell;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public final class Workarounds {

    private static final Class<?> ACTIVITY_THREAD_CLASS;
    private static final Object ACTIVITY_THREAD;

    static {
        try {
            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
            Constructor<?> activityThreadConstructor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            ACTIVITY_THREAD = activityThreadConstructor.newInstance();

            Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, ACTIVITY_THREAD);

            Field mSystemThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread");
            mSystemThreadField.setAccessible(true);
            mSystemThreadField.setBoolean(ACTIVITY_THREAD, true);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Workarounds() {
    }

    public static void apply() {
        if (Build.VERSION.SDK_INT >= 31) {
            fillConfigurationController();
        }

        boolean mustFillAppInfo = !Build.BRAND.equalsIgnoreCase("ONYX");
        if (mustFillAppInfo) {
            fillAppInfo();
        }
        fillAppContext();
        fillInstrumentation();
    }

    private static void fillAppInfo() {
        try {
            Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> appBindDataConstructor = appBindDataClass.getDeclaredConstructor();
            appBindDataConstructor.setAccessible(true);
            Object appBindData = appBindDataConstructor.newInstance();

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.packageName = FakeContext.PACKAGE_NAME;

            Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            appInfoField.set(appBindData, applicationInfo);

            Field mBoundApplicationField = ACTIVITY_THREAD_CLASS.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            mBoundApplicationField.set(ACTIVITY_THREAD, appBindData);
        } catch (Throwable ignored) {
        }
    }

    private static void fillAppContext() {
        try {
            Application app = new Application();
            Field baseField = ContextWrapper.class.getDeclaredField("mBase");
            baseField.setAccessible(true);
            baseField.set(app, FakeContext.get());

            Field mInitialApplicationField = ACTIVITY_THREAD_CLASS.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(ACTIVITY_THREAD, app);
        } catch (Throwable ignored) {
        }
    }

    private static void fillInstrumentation() {
        try {
            Field instrField = ACTIVITY_THREAD_CLASS.getDeclaredField("mInstrumentation");
            instrField.setAccessible(true);
            Object current = instrField.get(ACTIVITY_THREAD);
            if (current == null) {
                Instrumentation instrumentation = new Instrumentation();
                instrField.set(ACTIVITY_THREAD, instrumentation);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fillConfigurationController() {
        try {
            Class<?> configurationControllerClass = Class.forName("android.app.ConfigurationController");
            Class<?> activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal");

            Constructor<?> configurationControllerConstructor =
                    configurationControllerClass.getDeclaredConstructor(activityThreadInternalClass);
            configurationControllerConstructor.setAccessible(true);
            Object configurationController = configurationControllerConstructor.newInstance(ACTIVITY_THREAD);

            Field configurationControllerField = ACTIVITY_THREAD_CLASS.getDeclaredField("mConfigurationController");
            configurationControllerField.setAccessible(true);
            configurationControllerField.set(ACTIVITY_THREAD, configurationController);
        } catch (Throwable ignored) {
        }
    }

    private static Context invokeGetSystemContext(Object activityThread) {
        if (activityThread == null) {
            return null;
        }
        try {
            Method getSystemContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
            return (Context) getSystemContextMethod.invoke(activityThread);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context invokeGetSystemUiContext(Object activityThread) {
        if (activityThread == null) {
            return null;
        }
        try {
            Method getSystemUiContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemUiContext");
            return (Context) getSystemUiContextMethod.invoke(activityThread);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeActivityThreadStatic(String methodName) {
        try {
            Method method = ACTIVITY_THREAD_CLASS.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Context getSystemContext() {
        Context context = invokeGetSystemContext(ACTIVITY_THREAD);
        if (context != null) {
            return context;
        }

        Object currentActivityThread = invokeActivityThreadStatic("currentActivityThread");
        context = invokeGetSystemContext(currentActivityThread);
        if (context != null) {
            return context;
        }

        context = invokeGetSystemUiContext(currentActivityThread);
        if (context != null) {
            return context;
        }

        Object systemMainThread = invokeActivityThreadStatic("systemMain");
        if (systemMainThread != null) {
            try {
                Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
                sCurrentActivityThreadField.setAccessible(true);
                sCurrentActivityThreadField.set(null, systemMainThread);
            } catch (Throwable ignored) {
            }

            context = invokeGetSystemContext(systemMainThread);
            if (context != null) {
                return context;
            }

            context = invokeGetSystemUiContext(systemMainThread);
            if (context != null) {
                return context;
            }
        }

        return null;
    }
}
