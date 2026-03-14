/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
/*
 * AndroidDeviceInfoUtils.kt
 * 封装常用的设备与应用信息获取工具方法（Kotlin）
 * 使用说明：把此文件放入你的 Android 项目的合适包下（例如: com.example.utils），直接调用 DeviceInfoUtils 中的方法。
 * 兼容处理：对不同 Android API 做了兼容判断；对可能不存在的项做了多重回退。
 */

package com.xiaomo.androidforclaw.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger

/**
 * Encapsulate returned app version info object
 */
data class AppVersion(
    val versionName: String,
    val versionCode: Long
)

/**
 * Device and app info utility class
 */
object DeviceInfoUtils {

    /**
     * Get current app (caller package name) version info
     * @return AppVersion (never null, returns versionName="unknown", versionCode=-1 if cannot get)
     */
    fun getCurrentAppVersion(context: Context): AppVersion {
        return getAppVersionInfo(context, context.packageName)
            ?: AppVersion("unknown", -1L)
    }

    /**
     * Get version info for specified package name
     * @param context Context
     * @param targetPackage Package name to query
     * @return AppVersion? Returns null if not installed or exception
     */
    fun getAppVersionInfo(context: Context, targetPackage: String): AppVersion? {
        return try {
            val pm = context.packageManager
            val packageInfo: PackageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // From Android 13 (TIRAMISU), recommended to use API with flags
                    pm.getPackageInfo(targetPackage, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(targetPackage, 0)
                }

            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            AppVersion(versionName, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            LayoutExceptionLogger.log("DeviceInfoUtils#getAppVersion", e)
            null
        }
    }

    /**
     * Check if a package name is installed on device
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            LayoutExceptionLogger.log("DeviceInfoUtils#isAppInstalled", e)
            false
        }
    }

    /**
     * Get device manufacturer + model, e.g.: "Google Pixel 7" or "Vendor 12"
     */
    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER?.trim()
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?: ""
        val model = Build.MODEL?.trim() ?: ""
        return when {
            manufacturer.isEmpty() && model.isEmpty() -> "Unknown"
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    /**
     * Get device name (prioritize device name from system settings, fallback to Bluetooth name, then fallback to manufacturer+model)
     * Note: Different manufacturers/systems store device name in different locations, so multiple attempts
     */
    fun getDeviceName(context: Context): String {
        // 1) Try from Settings.Global (some ROMs store here)
        try {
            val nameGlobal =
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            if (!nameGlobal.isNullOrBlank()) return nameGlobal
        } catch (e: Exception) {
            LayoutExceptionLogger.log("DeviceInfoUtils#getDeviceName#Global", e)
            // ignore
        }

        // 2) Try from Settings.Secure (some devices/manufacturers may store here)
        try {
            val nameSecure = Settings.Secure.getString(context.contentResolver, "device_name")
            if (!nameSecure.isNullOrBlank()) return nameSecure
        } catch (e: Exception) {
            LayoutExceptionLogger.log("DeviceInfoUtils#getDeviceName#Secure", e)
            // ignore
        }

        return getDeviceModel()
    }

    /**
     * Get readable name of installed app (app label)
     * @return App name (returns null if cannot get)
     */
    fun getAppLabel(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            LayoutExceptionLogger.log("DeviceInfoUtils#getAppName", e)
            null
        }
    }

    /**
     * Format PackageInfo version info to string (for logging)
     */
    fun formatAppVersionForLog(appVersion: AppVersion?): String {
        return if (appVersion == null) {
            "not installed"
        } else {
            "versionName=${appVersion.versionName}, versionCode=${appVersion.versionCode}"
        }
    }

}
