/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger

object CustomOSAppOpsChecker {

    private const val TAG = "CustomOSAppOpsChecker"

    /**
     * 通过反射调用隐藏的checkOp方法检查特定操作码权限
     * @param op 要检查的操作码 (如10021)
     * @return 如果有权限返回true，否则返回false
     */
    fun checkOpByReflection(op: Int): Boolean {
        return try {
            // 1. 获取AppOpsManager实例
            val appOpsManager =
                MyApplication.application.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            // 2. 通过反射获取checkOp方法
            val checkOpMethod = appOpsManager.javaClass.getMethod(
                "checkOp",  // 方法名
                Int::class.javaPrimitiveType,  // op: int
                Int::class.javaPrimitiveType,  // uid: int
                String::class.java             // packageName: String
            )

            // 3. 调用方法并传入参数
            val result = checkOpMethod.invoke(
                appOpsManager,
                op,                      // 操作码，如10021
                Process.myUid(),         // 当前应用UID
                MyApplication.application.packageName      // 当前应用包名
            ) as Int

            // 判断权限是否被允许
            result == AppOpsManager.MODE_ALLOWED

        } catch (e: NoSuchMethodException) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#checkOpByReflection#NoSuchMethod", e)
            Log.e("CustomOSAppOpsChecker", "checkOp方法不存在", e)
            false
        } catch (e: SecurityException) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#checkOpByReflection#Security", e)
            // 这可能意味着权限确实被拒绝，也可能是反射调用本身的安全异常
            Log.e("CustomOSAppOpsChecker", "安全异常或权限被拒绝", e)
            false
        } catch (e: Exception) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#checkOpByReflection", e)
            Log.e("CustomOSAppOpsChecker", "检查权限时发生未知异常", e)
            false
        }
    }

    /**
     * 快捷方法：检查后台弹出页面权限 (OP_CODE 10021)
     * @return 如果是Device手机且有权限返回true，否则返回false；非Device手机默认返回true
     */
    fun checkBackgroundPopupPermission(): Boolean {
        // 判断是否为Device设备
        return if (isCustomOSRom()) {
            checkOpByReflection(10021)
        } else {
            true // 非Device手机默认返回true
        }
    }

    /**
     * 判断当前设备是否为CustomOS ROM
     * @return 如果是CustomOS ROM返回true，否则返回false
     */
    fun isCustomOSRom(): Boolean {
        return try {
            // 使用反射方式获取SystemProperties类和get方法
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            val systemProperty = getMethod.invoke(null, "ro.custom.ui.version.name") as String
            systemProperty.isNotEmpty()
        } catch (e: Exception) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#isCustomOS", e)
            false
        }
    }

    /**
     * 获取Device rom 版本号，获取失败返回 -1
     *
     * @return custom rom version code, if fail , return -1
     */
    private fun getCustomOSVersion(): Int {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            val version = getMethod.invoke(null, "ro.custom.ui.version.name") as String
            if (version.isNotEmpty()) {
                Integer.parseInt(version.substring(1))
            } else {
                -1
            }
        } catch (e: Exception) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#getCustomOSVersion", e)
            Log.e(TAG, "获取 CustomOS 版本号失败", e)
            -1
        }
    }

    /**
     * 跳转到应用的权限详情页面 (根据CustomOS版本选择不同的页面)
     */
    fun jumpToAppOpsDetail() {
        try {
            val versionCode = getCustomOSVersion()
            when {
                versionCode == 5 -> {
                    goToCustomOSPermissionActivity_V5()
                }

                versionCode == 6 -> {
                    goToCustomOSPermissionActivity_V6()
                }

                versionCode == 7 -> {
                    goToCustomOSPermissionActivity_V7()
                }

                versionCode >= 8 -> {
                    goToCustomOSPermissionActivity_V8()
                }

                else -> {
                    // 默认跳转方式
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val packageName = MyApplication.application.packageName
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    MyApplication.application.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#openPermissionDetailPage", e)
            Log.e("CustomOSAppOpsChecker", "跳转到权限详情页失败", e)
        }
    }

    private fun isIntentAvailable(intent: Intent): Boolean {
        return try {
            MyApplication.application.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            ).isNotEmpty()
        } catch (e: Exception) {
            LayoutExceptionLogger.log("CustomOSAppOpsChecker#canOpenPermissionDetailPage", e)
            false
        }
    }

    /**
     * Device V5 版本 ROM权限申请
     */
    private fun goToCustomOSPermissionActivity_V5() {
        val packageName = MyApplication.application.packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        if (isIntentAvailable(intent)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            MyApplication.application.startActivity(intent)
        } else {
            Log.e(TAG, "V5版本 intent 不可用!")
        }
    }

    /**
     * Device V6 版本 ROM权限申请
     */
    private fun goToCustomOSPermissionActivity_V6() {
        val intent = Intent("vendor.intent.action.APP_PERM_EDITOR")
        intent.setClassName(
            "com.vendor.securitycenter",
            "com.vendor.permcenter.permissions.AppPermissionsEditorActivity"
        )
        intent.putExtra("extra_pkgname", MyApplication.application.packageName)
        if (isIntentAvailable(intent)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            MyApplication.application.startActivity(intent)
        } else {
            Log.e(TAG, "V6版本 intent 不可用!")
        }
    }

    /**
     * Device V7 版本 ROM权限申请
     */
    private fun goToCustomOSPermissionActivity_V7() {
        val intent = Intent("vendor.intent.action.APP_PERM_EDITOR")
        intent.setClassName(
            "com.vendor.securitycenter",
            "com.vendor.permcenter.permissions.AppPermissionsEditorActivity"
        )
        intent.putExtra("extra_pkgname", MyApplication.application.packageName)
        if (isIntentAvailable(intent)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            MyApplication.application.startActivity(intent)
        } else {
            Log.e(TAG, "V7版本 intent 不可用!")
        }
    }

    /**
     * Device V8 及以上版本 ROM权限申请
     */
    private fun goToCustomOSPermissionActivity_V8() {
        var intent = Intent("vendor.intent.action.APP_PERM_EDITOR")
        intent.setClassName(
            "com.vendor.securitycenter",
            "com.vendor.permcenter.permissions.PermissionsEditorActivity"
        )
        intent.putExtra("extra_pkgname", MyApplication.application.packageName)
        if (isIntentAvailable(intent)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            MyApplication.application.startActivity(intent)
        } else {
            intent = Intent("vendor.intent.action.APP_PERM_EDITOR")
            intent.setPackage("com.vendor.securitycenter")
            intent.putExtra("extra_pkgname", MyApplication.application.packageName)
            if (isIntentAvailable(intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                MyApplication.application.startActivity(intent)
            } else {
                Log.e(TAG, "V8及以上版本 intent 不可用!")
                // 降级到默认方式
                val defaultIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val packageName = MyApplication.application.packageName
                val uri = Uri.fromParts("package", packageName, null)
                defaultIntent.data = uri
                defaultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                MyApplication.application.startActivity(defaultIntent)
            }
        }
    }
}