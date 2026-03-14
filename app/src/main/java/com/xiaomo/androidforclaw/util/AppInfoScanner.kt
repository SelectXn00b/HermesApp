/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.xiaomo.androidforclaw.core.MyApplication

/**
 * App information scanner tool
 * Used to quickly get app name, package name and main Activity of installed apps on device
 *
 * Usage:
 * 1. Call in code:AppInfoScanner.scanAndExport(context)
 * 2. View Logcat (tag: AppInfoScanner) to get output
 * 3. Or call AppInfoScanner.exportToFile(context) to export to file
 */
object AppInfoScanner {

    private const val TAG = "AppInfoScanner"

    /**
     * App information data class
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val mainActivity: String?
    )

    /**
     * Scan all installed apps and output to Logcat
     * @param context Context
     * @param includeSystemApps Whether to include system apps, default false
     * @param filterKeywords Filter keywords list
     */
    fun scanAndExport(
        context: Context,
        includeSystemApps: Boolean = false,
        filterKeywords: List<String> = listOf(
            "android.",
            "com.android.",
            "com.google.android.webview",
            "com.vendor.security",
            "com.vendor.system",
            "com.vendor.mipush",
            "com.qualcomm.",
            "vendor.qti.",
            "com.qti.",
            "org.codeaurora.",
            "com.longcheertel.",
            "com.boundax.",
            "com.wdstechnology.",
            "com.novatek.",
            "com.duokan.",
            "com.bsp.",
            "com.fingerprints.",
            "com.goodix.",
            "com.lbe.",
            "com.tencent.soter.",
            "com.microsoftsdk.",
            "com.agoda.",
            "com.booking.",
            "com.netflix.",
            "com.spotify.",
            "com.linkedin.",
            "com.facebook.",
            "com.amazon.",
            "com.google.ambient.",
            "com.wdstechnology.",
            "com.boundax.",
            "vendor.systemui.plugin",
            "android.autoinstalls.",
            "android.auto_generated_",
            "android.overlay.",
            "android.aosp.overlay.",
            "android.vendor.overlay.",
            "android.qvaoverlay.",
            "com.android.overlay.",
            "com.android.theme.",
            "com.android.internal.",
            "com.android.server.",
            "com.android.phone.auto_generated_",
            "com.android.providers.telephony.auto_generated_",
            "com.android.companiondevicemanager.auto_generated_",
            "com.vendor.systemui.overlay.",
            "com.vendor.settings.rro.",
            "com.vendor.phone.carriers.overlay.",
            "com.vendor.wallpaper.overlay.",
            "com.vendor.miwallpaper.overlay.",
            "com.vendor.miwallpaper.config.overlay.",
            "com.android.systemui.overlay.",
            "com.android.server.telecom.overlay.",
            "com.android.providers.telephony.overlay.",
            "com.android.carrierconfig.overlay.",
            "com.android.managedprovisioning.overlay.",
            "com.android.cellbroadcastreceiver.overlay.",
            "com.android.stk.overlay.",
            "com.android.bluetooth.overlay.",
            "com.android.phone.overlay.",
            "com.android.wifi.resources.overlay.",
            "com.android.wifi.resources.vendor",
            "com.google.android.overlay.",
            "com.google.android.wifi.resources.overlay.",
            "com.vendor.system.overlay",
            "com.vendor.systemui.carriers.overlay",
            "com.vendor.systemui.devices.overlay",
            "com.vendor.permissioncontroller.overlay",
            "com.vendor.cellbroadcastservice.overlay",
            "com.vendor.inputsettings.overlay",
            "com.android.settings.overlay.",
            "com.android.settings.intelligence",
            "com.android.inputsettings.overlay.",
            "com.android.inputdevices",
            "com.android.internal.systemui.",
            "com.android.internal.display.",
            "com.android.compos.",
            "com.android.microdroid.",
            "com.android.virtualmachine.",
            "com.android.uwb.resources.",
            "com.android.uwb.resources.overlay.",
            "com.android.dreams.",
            "com.android.egg",
            "com.android.emergency",
            "com.android.emergency",
            "com.android.hotwordenrollment.",
            "com.android.localtransport",
            "com.android.pacprocessor",
            "com.android.provision",
            "com.android.rkpdapp",
            "com.android.shell",
            "com.android.simappdialog",
            "com.android.soundpicker",
            "com.android.traceur",
            "com.android.vpndialogs",
            "com.android.wallpaperbackup",
            "com.android.wallpapercropper",
            "com.android.wallpaper.livepicker",
            "com.android.htmlviewer",
            "com.android.bookmarkprovider",
            "com.android.providers.",
            "com.android.proxyhandler",
            "com.android.sharedstoragebackup",
            "com.android.statementservice",
            "com.android.storagemanager",
            "com.android.externalstorage",
            "com.android.certinstaller",
            "com.android.calllogbackup",
            "com.android.backupconfirm",
            "com.android.avatarpicker",
            "com.android.apps.tag",
            "com.android.bips",
            "com.android.bluetoothmidiservice",
            "com.android.bluetooth",
            "com.android.carrierdefaultapp",
            "com.android.companiondevicemanager",
            "com.android.credentialmanager",
            "com.android.cts.",
            "com.android.DeviceAsWebcam",
            "com.android.devicediagnostics",
            "com.android.dreams.basic",
            "com.android.dreams.phototable",
            "com.android.dynsystem",
            "com.android.hotspot2.",
            "com.android.imsserviceentitlement",
            "com.android.keychain",
            "com.android.location.fused",
            "com.android.mms.service",
            "com.android.mtp",
            "com.android.musicfx",
            "com.android.nfc",
            "com.android.ons",
            "com.android.phone",
            "com.android.printspooler",
            "com.android.printservice.",
            "com.android.role.notes.enabled",
            "com.android.se",
            "com.android.settings",
            "com.android.smspush",
            "com.android.soundrecorder",
            "com.android.stk",
            "com.android.systemui",
            "com.android.systemui.accessibility.",
            "com.android.thememanager",
            "com.android.thememanager.customizethemeconfig.config.overlay",
            "com.android.theme.font.",
            "com.android.cameraextensions",
            "com.android.camera",
            "com.android.cellbroadcastreceiver",
            "com.android.cellbroadcastservice",
            "com.android.carrierconfig",
            "com.android.companiondevicemanager",
            "com.android.deskclock",
            "com.android.intentresolver",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.vendor.global.packageinstaller"
        )
    ) {
        val appInfoList = scanApps(context, includeSystemApps, filterKeywords)

        if (appInfoList.isEmpty()) {
            Log.w(TAG, "未找到任何应用")
            return
        }

        // 输出到 Logcat
        Log.d(TAG, "=".repeat(100))
        Log.d(TAG, "开始输出应用信息（共 ${appInfoList.size} 个应用）")
        Log.d(TAG, "=".repeat(100))

        // 按应用名排序
        val sortedList = appInfoList.sortedBy { it.appName }

        sortedList.forEach { appInfo ->
            val formattedCode = formatAppIntentInfo(appInfo)
            Log.d(TAG, formattedCode)
        }

        Log.d(TAG, "=".repeat(100))
        Log.d(TAG, "应用信息输出完成")
        Log.d(TAG, "=".repeat(100))

        // 输出统计信息
        Log.d(TAG, "\n统计信息：")
        Log.d(TAG, "总应用数: ${appInfoList.size}")
        val launchableCount = appInfoList.count { it.mainActivity != null }
        val unlaunchableCount = appInfoList.count { it.mainActivity == null }
        Log.d(TAG, "有主Activity的应用: $launchableCount")
        Log.d(TAG, "无主Activity的应用: $unlaunchableCount")
        if (unlaunchableCount > 0) {
            Log.d(TAG, "\n说明：无主Activity的应用通常是以下类型：")
            Log.d(TAG, "1. 服务类应用（Service）- 如 com.vendor.aiasst.service")
            Log.d(TAG, "2. 系统组件和库 - 如 com.vendor.analytics")
            Log.d(TAG, "3. 后台服务 - 如 com.google.android.ext.services")
            Log.d(TAG, "4. 这些应用无法通过普通方式启动，因此没有主Activity")
        }
    }

    /**
     * 扫描所有已安装应用
     */
    private fun scanApps(
        context: Context,
        includeSystemApps: Boolean,
        filterKeywords: List<String>
    ): List<AppInfo> {
        val appInfoList = mutableListOf<AppInfo>()

        try {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }

            packages.forEach { packageInfo ->
                val packageName = packageInfo.packageName

                // 过滤系统应用
                if (!includeSystemApps) {
                    val isSystemApp = filterKeywords.any {
                        packageName.startsWith(it, ignoreCase = true)
                    }
                    if (isSystemApp) return@forEach
                }

                // 获取应用名
                val appName = try {
                    val ai = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    packageName
                }

                // 获取主Activity（使用多种方法尝试）
                val mainActivity = getMainActivity(context, packageName, pm)

                appInfoList.add(AppInfo(packageName, appName, mainActivity))
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描应用失败: ${e.message}", e)
            LayoutExceptionLogger.log("AppInfoScanner#scanApps", e)
        }

        return appInfoList
    }

    /**
     * 获取应用的主Activity（使用多种方法）
     * 方法1: 使用 getLaunchIntentForPackage（最快，但可能受包可见性限制）
     * 方法2: 解析 PackageInfo 查找 MAIN/LAUNCHER Activity（更可靠）
     */
    private fun getMainActivity(
        context: Context,
        packageName: String,
        pm: PackageManager
    ): String? {
        // 方法1: 使用 getLaunchIntentForPackage（优先使用，因为最快）
        try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            val className = intent?.component?.className
            if (!className.isNullOrEmpty()) {
                return className
            }
        } catch (e: Exception) {
            Log.d(TAG, "getLaunchIntentForPackage 失败 ($packageName): ${e.message}")
        }

        // 方法2: 使用 queryIntentActivities 直接查找所有可启动的 Activity（更高效）
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                @Suppress("NewApi")
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            // 查找匹配当前包名的第一个 Activity
            for (resolveInfo in resolveInfoList) {
                if (resolveInfo.activityInfo.packageName == packageName) {
                    return resolveInfo.activityInfo.name
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "queryIntentActivities 失败 ($packageName): ${e.message}")
        }

        // 方法3: 解析 PackageInfo 查找 MAIN/LAUNCHER Activity（最后的备用方法）
        // 注意：某些应用（如服务类应用、系统组件）可能没有主Activity，这是正常的
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_ACTIVITIES.toLong() or
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
                )
            }

            // 如果应用没有任何 Activity，说明可能是服务类应用
            if (packageInfo.activities == null || packageInfo.activities.isEmpty()) {
                Log.d(TAG, "应用 $packageName 没有 Activity（可能是服务类应用）")
                return null
            }

            // 查找带有 MAIN/LAUNCHER intent-filter 的 Activity
            packageInfo.activities?.forEach { activityInfo ->
                try {
                    // 检查 Activity 是否有 MAIN/LAUNCHER intent-filter
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(packageName)
                        setClassName(packageName, activityInfo.name)
                    }

                    val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("NewApi")
                        pm.queryIntentActivities(
                            intent,
                            PackageManager.ResolveInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(intent, 0)
                    }

                    if (resolveInfoList.isNotEmpty()) {
                        return activityInfo.name
                    }
                } catch (e: Exception) {
                    // 忽略单个 Activity 的查询错误
                }
            }
        } catch (e: Exception) {
            // 如果获取 PackageInfo 失败，可能是权限问题或应用不存在
            Log.d(TAG, "解析 PackageInfo 失败 ($packageName): ${e.message}")
        }


        // 所有方法都失败，返回 null
        return null
    }

    /**
     * 格式化单个应用的 AppIntentInfo 代码
     */
    private fun formatAppIntentInfo(appInfo: AppInfo): String {
        // 清理应用名，移除特殊字符
        val cleanAppName = appInfo.appName
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .trim()

        // 生成 appNameList（默认包含应用名）
        val appNameList = mutableListOf<String>()
        appNameList.add(cleanAppName)

        // 如果包名有意义，也添加到列表中
        val packageNameParts = appInfo.packageName.split(".")
        if (packageNameParts.size > 1) {
            val lastPart = packageNameParts.last()
            if (lastPart.length > 2 &&
                !lastPart.equals(cleanAppName, ignoreCase = true) &&
                !lastPart.contains("overlay", ignoreCase = true) &&
                !lastPart.contains("rro", ignoreCase = true)) {
                appNameList.add(lastPart)
            }
        }

        // 如果有主Activity，输出完整格式；否则只输出基本信息
        return if (appInfo.mainActivity != null) {
            """
    AppIntentInfo(
        appName = "$cleanAppName",
        appNameList = mutableListOf(${appNameList.joinToString(", ") { "\"$it\"" }}),
        packageName = "${appInfo.packageName}",
        mainActivity = "${appInfo.mainActivity}"
    ),""".trimIndent()
        } else {
            """
    // 无主Activity: $cleanAppName (${appInfo.packageName})
    // AppIntentInfo(
    //     appName = "$cleanAppName",
    //     appNameList = mutableListOf(${appNameList.joinToString(", ") { "\"$it\"" }}),
    //     packageName = "${appInfo.packageName}",
    //     mainActivity = "未知"
    // ),""".trimIndent()
        }
    }

    /**
     * 快速扫描（使用 MyApplication 的 context）
     */
    fun quickScan() {
        val context = MyApplication.application
        scanAndExport(context)
    }

    /**
     * 扫描并导出为文本格式（便于复制）
     * @param context Context
     * @return 格式化的文本字符串
     */
    fun exportAsText(context: Context): String {
        val appInfoList = scanApps(context, includeSystemApps = false, filterKeywords = emptyList())
        val sortedList = appInfoList.sortedBy { it.appName }

        val sb = StringBuilder()
        sb.appendLine("=".repeat(100))
        sb.appendLine("应用信息列表（共 ${appInfoList.size} 个应用）")
        sb.appendLine("=".repeat(100))
        sb.appendLine()

        sortedList.forEach { appInfo ->
            sb.appendLine("应用名: ${appInfo.appName}")
            sb.appendLine("包名: ${appInfo.packageName}")
            sb.appendLine("主Activity: ${appInfo.mainActivity ?: "无"}")
            sb.appendLine("-".repeat(80))
        }

        return sb.toString()
    }

    /**
     * 只获取有主Activity的应用（可启动的应用）
     */
    fun scanLaunchableApps(context: Context): List<AppInfo> {
        return scanApps(context, includeSystemApps = false, filterKeywords = emptyList())
            .filter { it.mainActivity != null }
    }

    /**
     * 输出格式化的 AppIntentInfo 代码（仅包含有主Activity的应用）
     */
    fun exportAppIntentInfoCode(context: Context): String {
        val launchableApps = scanLaunchableApps(context)
        val sortedList = launchableApps.sortedBy { it.appName }

        val sb = StringBuilder()
        sb.appendLine("// 共 ${launchableApps.size} 个可启动应用")
        sb.appendLine()

        sortedList.forEach { appInfo ->
            val formattedCode = formatAppIntentInfo(appInfo)
            sb.appendLine(formattedCode)
            sb.appendLine()
        }

        return sb.toString()
    }
}
