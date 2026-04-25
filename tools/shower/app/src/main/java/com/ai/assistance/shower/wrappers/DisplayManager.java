package com.ai.assistance.shower.wrappers;

import com.ai.assistance.shower.device.DisplayInfo;
import com.ai.assistance.shower.device.Size;

import android.annotation.SuppressLint;
import android.hardware.display.VirtualDisplay;
import android.os.IBinder;
import android.view.Display;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class DisplayManager {

    private final Object manager;
    private Method getDisplayInfoMethod;
    private Method createVirtualDisplayMethod;

    static DisplayManager create() {
        try {
            Class<?> clazz = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Method getInstanceMethod = clazz.getDeclaredMethod("getInstance");
            Object dmg = getInstanceMethod.invoke(null);
            return new DisplayManager(dmg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private DisplayManager(Object manager) {
        this.manager = manager;
    }

    public static DisplayInfo parseDisplayInfo(String dumpsysDisplayOutput, int displayId) {
        Pattern regex = Pattern.compile(
                "^    mOverrideDisplayInfo=DisplayInfo\\{\".*?, displayId " + displayId + ".*?(, FLAG_.*)?, real ([0-9]+) x ([0-9]+).*?, "
                        + "rotation ([0-9]+).*?, density ([0-9]+).*?, layerStack ([0-9]+)",
                Pattern.MULTILINE);
        Matcher matcher = regex.matcher(dumpsysDisplayOutput);
        if (!matcher.find()) {
            return null;
        }

        int flags = parseDisplayFlags(matcher.group(1));
        int width = Integer.parseInt(matcher.group(2));
        int height = Integer.parseInt(matcher.group(3));
        int rotation = Integer.parseInt(matcher.group(4));
        int density = Integer.parseInt(matcher.group(5));
        int layerStack = Integer.parseInt(matcher.group(6));

        return new DisplayInfo(displayId, new Size(width, height), rotation, layerStack, flags, density, null);
    }

    private static DisplayInfo getDisplayInfoFromDumpsysDisplay(int displayId) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] {"dumpsys", "display"});
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }

            return parseDisplayInfo(output.toString(), displayId);
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static int parseDisplayFlags(String text) {
        if (text == null) {
            return 0;
        }

        int flags = 0;
        Pattern regex = Pattern.compile("FLAG_[A-Z_]+");
        Matcher matcher = regex.matcher(text);
        while (matcher.find()) {
            String flagString = matcher.group();
            try {
                Field field = Display.class.getDeclaredField(flagString);
                flags |= field.getInt(null);
            } catch (ReflectiveOperationException e) {
                // ignore @TestApi or vendor-only flags
            }
        }
        return flags;
    }

    private synchronized Method getGetDisplayInfoMethod() throws NoSuchMethodException {
        if (getDisplayInfoMethod == null) {
            getDisplayInfoMethod = manager.getClass().getMethod("getDisplayInfo", int.class);
        }
        return getDisplayInfoMethod;
    }

    public DisplayInfo getDisplayInfo(int displayId) {
        try {
            Method method = getGetDisplayInfoMethod();
            Object displayInfo = method.invoke(manager, displayId);
            if (displayInfo == null) {
                return getDisplayInfoFromDumpsysDisplay(displayId);
            }

            Class<?> cls = displayInfo.getClass();
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
            int layerStack = cls.getDeclaredField("layerStack").getInt(displayInfo);
            int flags = cls.getDeclaredField("flags").getInt(displayInfo);
            int dpi = cls.getDeclaredField("logicalDensityDpi").getInt(displayInfo);
            String uniqueId = (String) cls.getDeclaredField("uniqueId").get(displayInfo);
            return new DisplayInfo(displayId, new Size(width, height), rotation, layerStack, flags, dpi, uniqueId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public int[] getDisplayIds() {
        try {
            return (int[]) manager.getClass().getMethod("getDisplayIds").invoke(manager);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Method getCreateVirtualDisplayMethod() throws NoSuchMethodException {
        if (createVirtualDisplayMethod == null) {
            createVirtualDisplayMethod = android.hardware.display.DisplayManager.class
                    .getMethod("createVirtualDisplay", String.class, int.class, int.class, int.class, Surface.class);
        }
        return createVirtualDisplayMethod;
    }

    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int displayIdToMirror, Surface surface) throws Exception {
        Method method = getCreateVirtualDisplayMethod();
        return (VirtualDisplay) method.invoke(null, name, width, height, displayIdToMirror, surface);
    }
}
