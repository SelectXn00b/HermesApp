/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.xiaomo.androidforclaw.core.MyApplication;

import java.util.HashMap;
import java.util.Map;


public class WakeLockManager {

    private static final String TAG = "WakeLockManager";

    public static final long PROCESS_WAKELOCK_TIMEOUT = 30 * 1000;

    // Screen wake lock key
    public static final String SCREEN_WAKE_LOCK_KEY = "screen_wake_lock";
    // Periodic refresh interval: 4 minutes (WakeLock default timeout is 5 minutes)
    private static final long REFRESH_INTERVAL = 4 * 60 * 1000; // 4 minutes
    // Periodic screen wake interval: 30 seconds (use reflection to call userActivity to prevent screen lock)
    private static final long WAKE_SCREEN_INTERVAL = 30 * 1000; // 30 seconds
    
    private static PowerManager sPowerManager = getSystemService(Context.POWER_SERVICE);
    private static final Map<String, WakeLock> sWakeLockMap = new HashMap<String, WakeLock>();
    
    // Handler for periodic WakeLock refresh
    private static Handler sRefreshHandler = new Handler(Looper.getMainLooper());
    // Callback task for periodic WakeLock refresh
    private static Runnable sRefreshRunnable = null;
    // Callback task for periodic screen wake
    private static Runnable sWakeScreenRunnable = null;
    // Flag indicating whether screen wake lock is active
    private static boolean sScreenWakeLockActive = false;

    /**
     * Get system service
     */
    public static void acquire(@NonNull String key, long timeOut) {
        Log.i(TAG, String.format("acquire wakeLock: %s for %d", key, timeOut));
        try {
            WakeLock wakeLock = sPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    WakeLockManager.class.getCanonicalName() + "/" + key);
            wakeLock.acquire(timeOut);

            WakeLock oldWakeLock;
            synchronized (sWakeLockMap) {
                oldWakeLock = sWakeLockMap.get(key);
                sWakeLockMap.put(key, wakeLock);
                if (oldWakeLock != null && oldWakeLock.isHeld()) {
                    oldWakeLock.release();  // Since each WakeLock has its own timeout, old and new are not equivalent, must release old and replace with new
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "exception when aquire wakelock " + key + ": " + e.toString());
        }
    }

    /**
     * Release system service
     */
    public static void release(@NonNull String key) {
        try {
            WakeLock oldWakeLock;
            synchronized (sWakeLockMap) {
                oldWakeLock = sWakeLockMap.remove(key);
                if (oldWakeLock != null && oldWakeLock.isHeld()) {
                    Log.i(TAG, "release wakeLock: " + key);
                    oldWakeLock.release();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "exception when release wakelock " + key + ": " + e.toString());
        }
    }

    /**
     * Acquire screen wake lock to prevent device screen lock
     *
     * Multi-layered screen lock prevention approach:
     * 1. Use PARTIAL_WAKE_LOCK to keep CPU running (supported on all Android versions)
     * 2. Combine with FLAG_KEEP_SCREEN_ON at Window level (needs to be set in Activity/Service)
     * 3. Periodically refresh WakeLock to ensure it doesn't expire from timeout
     * 4. Use ACQUIRE_CAUSES_WAKEUP to ensure screen can be woken
     */
    public static void acquireScreenWakeLock() {
        Log.i(TAG, "acquire screen wakeLock to prevent screen lock - using multi-layered prevention approach");
        
        synchronized (WakeLockManager.class) {
            if (sScreenWakeLockActive) {
                Log.i(TAG, "Screen wake lock already active, skipping duplicate acquisition");
                return;
            }
            sScreenWakeLockActive = true;
        }
        
        try {
            // 🔥 Critical fix: PARTIAL_WAKE_LOCK cannot prevent screen lock!
            // Need to combine multiple approaches:
            // 1. PARTIAL_WAKE_LOCK keeps CPU running
            // 2. Try using SCREEN_DIM_WAKE_LOCK (even though deprecated on API 27+, may still work on some devices)
            // 3. Combine with FLAG_KEEP_SCREEN_ON at window level

            // Approach 1: Acquire PARTIAL_WAKE_LOCK to keep CPU running
            // 🔥 Use without timeout to ensure not automatically released
            int partialFlags = PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
            WakeLock partialWakeLock = sPowerManager.newWakeLock(partialFlags,
                    WakeLockManager.class.getCanonicalName() + "/" + SCREEN_WAKE_LOCK_KEY + "_partial");
            partialWakeLock.setReferenceCounted(false);
            // Use without timeout, maintain through periodic refresh
            partialWakeLock.acquire();

            // Approach 2: Try to acquire SCREEN_DIM_WAKE_LOCK to prevent screen lock (even if deprecated)
            WakeLock screenWakeLock = null;
            try {
                // Use reflection to try acquiring SCREEN_DIM_WAKE_LOCK (even if deprecated)
                int screenFlags = PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                screenWakeLock = sPowerManager.newWakeLock(screenFlags,
                        WakeLockManager.class.getCanonicalName() + "/" + SCREEN_WAKE_LOCK_KEY + "_screen");
                screenWakeLock.setReferenceCounted(false);
                // Use without timeout, maintain through periodic refresh
                screenWakeLock.acquire();
                Log.i(TAG, "Successfully acquired SCREEN_DIM_WAKE_LOCK (prevents screen lock)");
            } catch (Exception e) {
                Log.w(TAG, "Failed to acquire SCREEN_DIM_WAKE_LOCK (may be ignored by system): " + e.getMessage());
                // If failed, try SCREEN_BRIGHT_WAKE_LOCK
                try {
                    int brightFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                    screenWakeLock = sPowerManager.newWakeLock(brightFlags,
                            WakeLockManager.class.getCanonicalName() + "/" + SCREEN_WAKE_LOCK_KEY + "_screen");
                    screenWakeLock.setReferenceCounted(false);
                    // Use without timeout, maintain through periodic refresh
                    screenWakeLock.acquire();
                    Log.i(TAG, "Successfully acquired SCREEN_BRIGHT_WAKE_LOCK (prevents screen lock)");
                } catch (Exception e2) {
                    Log.w(TAG, "Failed to acquire SCREEN_BRIGHT_WAKE_LOCK too: " + e2.getMessage());
                }
            }

            WakeLock oldWakeLock;
            synchronized (sWakeLockMap) {
                // Save both WakeLocks
                oldWakeLock = sWakeLockMap.get(SCREEN_WAKE_LOCK_KEY);
                sWakeLockMap.put(SCREEN_WAKE_LOCK_KEY, partialWakeLock);
                if (screenWakeLock != null) {
                    sWakeLockMap.put(SCREEN_WAKE_LOCK_KEY + "_screen", screenWakeLock);
                }
                if (oldWakeLock != null && oldWakeLock.isHeld()) {
                    oldWakeLock.release();
                }
            }

            Log.i(TAG, "Screen wake lock acquired successfully - PARTIAL_WAKE_LOCK (CPU)" +
                    (screenWakeLock != null ? " + SCREEN_WAKE_LOCK (screen)" : " (screen lock may be ignored by system)"));

            // 🔥 Try to use WRITE_SECURE_SETTINGS permission to modify screen timeout (if possible)
            trySetScreenTimeoutNever();

            // Start periodic refresh mechanism
            startRefreshRoutine();

            // Start periodic screen wake mechanism (use reflection to call userActivity to prevent screen lock)
            startWakeScreenRoutine();

        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire screen wake lock: " + e.toString(), e);
            synchronized (WakeLockManager.class) {
                sScreenWakeLockActive = false;
            }
        }
    }
    
    /**
     * Periodically refresh WakeLock to ensure it doesn't expire from timeout
     * Refresh every 4 minutes (WakeLock default timeout is 5 minutes)
     */
    private static void startRefreshRoutine() {
        // Stop old refresh task
        stopRefreshRoutine();
        
        sRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!sScreenWakeLockActive) {
                    Log.d(TAG, "Screen wake lock released, stopping refresh");
                    return;
                }

                try {
                    synchronized (sWakeLockMap) {
                        // Refresh PARTIAL_WAKE_LOCK
                        WakeLock currentPartialLock = sWakeLockMap.get(SCREEN_WAKE_LOCK_KEY);
                        if (currentPartialLock != null && currentPartialLock.isHeld()) {
                            currentPartialLock.release();
                            
                            int partialFlags = PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                            WakeLock newPartialLock = sPowerManager.newWakeLock(partialFlags,
                                    WakeLockManager.class.getCanonicalName() + "/" + SCREEN_WAKE_LOCK_KEY + "_partial");
                            newPartialLock.setReferenceCounted(false);
                            // Use without timeout
                            newPartialLock.acquire();
                            sWakeLockMap.put(SCREEN_WAKE_LOCK_KEY, newPartialLock);
                        }

                        // Refresh SCREEN_WAKE_LOCK (if exists)
                        WakeLock currentScreenLock = sWakeLockMap.get(SCREEN_WAKE_LOCK_KEY + "_screen");
                        if (currentScreenLock != null && currentScreenLock.isHeld()) {
                            currentScreenLock.release();
                            
                            try {
                                int screenFlags = PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                                WakeLock newScreenLock = sPowerManager.newWakeLock(screenFlags,
                                        WakeLockManager.class.getCanonicalName() + "/" + SCREEN_WAKE_LOCK_KEY + "_screen");
                                    newScreenLock.setReferenceCounted(false);
                                    // Use without timeout
                                    newScreenLock.acquire();
                                    sWakeLockMap.put(SCREEN_WAKE_LOCK_KEY + "_screen", newScreenLock);
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to refresh SCREEN_WAKE_LOCK: " + e.getMessage());
                            }
                        }

                        if (currentPartialLock == null || !currentPartialLock.isHeld()) {
                            Log.w(TAG, "Current WakeLock not held, re-acquiring");
                            // If WakeLock is lost, re-acquire
                            acquireScreenWakeLock();
                            return;
                        }

                        Log.d(TAG, "Screen wake lock refreshed");
                    }

                    // Schedule next refresh
                    sRefreshHandler.postDelayed(this, REFRESH_INTERVAL);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to refresh screen wake lock: " + e.toString(), e);
                    // Even if refresh fails, try to continue
                    sRefreshHandler.postDelayed(this, REFRESH_INTERVAL);
                }
            }
        };

        // Start first refresh after REFRESH_INTERVAL delay
        sRefreshHandler.postDelayed(sRefreshRunnable, REFRESH_INTERVAL);
        Log.i(TAG, "Started periodic screen wake lock refresh mechanism, interval: " + (REFRESH_INTERVAL / 1000) + " seconds");
    }
    
    /**
     * Stop periodic refresh
     */
    private static void stopRefreshRoutine() {
        if (sRefreshRunnable != null) {
            sRefreshHandler.removeCallbacks(sRefreshRunnable);
            sRefreshRunnable = null;
            Log.d(TAG, "Stopped screen wake lock refresh mechanism");
        }
    }

    /**
     * Periodically wake screen to prevent screen lock (by calling userActivity through reflection)
     */
    private static void startWakeScreenRoutine() {
        // Stop old wake task
        stopWakeScreenRoutine();
        
        sWakeScreenRunnable = new Runnable() {
            @Override
            public void run() {
                if (!sScreenWakeLockActive) {
                    Log.d(TAG, "Screen wake lock released, stopping periodic wake");
                    return;
                }

                try {
                    // Use reflection to call userActivity() to tell system there's user activity, preventing screen lock
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            java.lang.reflect.Method userActivityMethod = sPowerManager.getClass()
                                    .getMethod("userActivity", long.class, int.class, int.class);
                            // USER_ACTIVITY_EVENT_OTHER = 0
                            int userActivityEventOther = 0;
                            userActivityMethod.invoke(sPowerManager, System.currentTimeMillis(),
                                    userActivityEventOther, 0);
                            Log.d(TAG, "Called userActivity() via reflection to prevent screen lock");
                        }
                    } catch (NoSuchMethodException e) {
                        Log.d(TAG, "userActivity() method does not exist, skipping");
                    } catch (Exception e) {
                        Log.w(TAG, "userActivity() call failed: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Periodic screen wake failed: " + e.toString(), e);
                }

                // Schedule next wake
                sRefreshHandler.postDelayed(this, WAKE_SCREEN_INTERVAL);
            }
        };

        // Delay start of first wake
        sRefreshHandler.postDelayed(sWakeScreenRunnable, WAKE_SCREEN_INTERVAL);
        Log.i(TAG, "Started periodic screen wake mechanism, interval: " + (WAKE_SCREEN_INTERVAL / 1000) + " seconds");
    }

    /**
     * Stop periodic screen wake
     */
    private static void stopWakeScreenRoutine() {
        if (sWakeScreenRunnable != null) {
            sRefreshHandler.removeCallbacks(sWakeScreenRunnable);
            sWakeScreenRunnable = null;
            Log.d(TAG, "Stopped periodic screen wake mechanism");
        }
    }

    /**
     * Release screen wake lock
     */
    public static void releaseScreenWakeLock() {
        synchronized (WakeLockManager.class) {
            if (!sScreenWakeLockActive) {
                Log.d(TAG, "Screen wake lock not active, skipping release");
                return;
            }
            sScreenWakeLockActive = false;
        }

        // Stop refresh mechanism
        stopRefreshRoutine();

        // Stop periodic screen wake mechanism
        stopWakeScreenRoutine();

        // Release all WakeLocks
        release(SCREEN_WAKE_LOCK_KEY); // PARTIAL_WAKE_LOCK
        release(SCREEN_WAKE_LOCK_KEY + "_screen"); // SCREEN_WAKE_LOCK

        // Restore screen timeout settings (optional)
        // restoreScreenTimeout();

        Log.i(TAG, "screen wakeLock released");
    }

    /**
     * Check if screen wake lock is active
     * @return true=active, false=inactive
     */
    public static boolean isScreenWakeLockActive() {
        synchronized (WakeLockManager.class) {
            return sScreenWakeLockActive;
        }
    }

    /**
     * Set FLAG_KEEP_SCREEN_ON for Window
     * This is another important layer for preventing screen lock, needs to be called in Activity/Service
     */
    public static void setKeepScreenOn(Window window) {
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d(TAG, "Set FLAG_KEEP_SCREEN_ON for Window");
        }
    }

    /**
     * Clear FLAG_KEEP_SCREEN_ON for Window
     */
    public static void clearKeepScreenOn(Window window) {
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d(TAG, "Cleared FLAG_KEEP_SCREEN_ON for Window");
        }
    }



    /**
     * Try to use WRITE_SECURE_SETTINGS permission to set screen timeout to never lock
     * Only attempt to set when permission exists, don't request permission or jump to settings
     */
    private static void trySetScreenTimeoutNever() {
        try {
            Context context = MyApplication.application.getApplicationContext();

            // Check if has WRITE_SECURE_SETTINGS permission (by attempting a test write)
            boolean hasPermission = false;
            try {
                Settings.Secure.putString(context.getContentResolver(),
                        "test_write_secure_settings", "test");
                Settings.Secure.putString(context.getContentResolver(),
                        "test_write_secure_settings", null);
                hasPermission = true;
            } catch (Exception e) {
                hasPermission = false;
            }

            if (hasPermission) {
                // Set screen timeout to max value (2147483647 milliseconds, about 24 days)
                // Note: SCREEN_OFF_TIMEOUT is in Settings.System, not in Secure
                try {
                    // Method 1: Try to set screen timeout via System (requires WRITE_SETTINGS permission)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.System.canWrite(context)) {
                            Settings.System.putInt(context.getContentResolver(),
                                    Settings.System.SCREEN_OFF_TIMEOUT, Integer.MAX_VALUE);
                            Log.i(TAG, "Set screen timeout to max via WRITE_SETTINGS");
                        } else {
                            Log.d(TAG, "No WRITE_SETTINGS permission, cannot set screen timeout via System");
                        }
                    } else {
                        // Can set directly on API < 23
                        Settings.System.putInt(context.getContentResolver(),
                                Settings.System.SCREEN_OFF_TIMEOUT, Integer.MAX_VALUE);
                        Log.i(TAG, "Set screen timeout to max (API < 23)");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set screen timeout via System: " + e.getMessage());
                }

                // Method 2: Try to set via Secure (some devices may support)
                try {
                    // Use reflection to try setting screen timeout in secure
                    java.lang.reflect.Method putIntMethod = Settings.Secure.class
                            .getMethod("putInt", android.content.ContentResolver.class, String.class, int.class);
                    // Try to set screen timeout (some devices may have related settings in Secure)
                    putIntMethod.invoke(null, context.getContentResolver(),
                            "screen_off_timeout", Integer.MAX_VALUE);
                    Log.i(TAG, "Attempted to set screen timeout via Secure");
                } catch (Exception e) {
                    Log.d(TAG, "Failed to set screen timeout via Secure (may not be supported): " + e.getMessage());
                }
            } else {
                Log.d(TAG, "No WRITE_SECURE_SETTINGS permission, skipping screen timeout setting (not jumping to settings)");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to attempt setting screen timeout: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T getSystemService(String name) {
        return (T) MyApplication.application
                .getApplicationContext()
                .getSystemService(name);
    }
}