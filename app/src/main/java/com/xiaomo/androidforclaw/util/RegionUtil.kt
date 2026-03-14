/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: utility helpers.
 */
package com.xiaomo.androidforclaw.util

import android.os.Build
import android.text.TextUtils
import java.util.Locale
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger

/**
 */
object RegionUtil {
    var sRegion: String = ""
    fun getRealRegion(): String {
        if (TextUtils.isEmpty(sRegion)) {
            sRegion = systemPropertiesGet("ro.vendor.region", "CN")
            if (!TextUtils.isEmpty(sRegion)) {
                return sRegion
            }
            sRegion = systemPropertiesGet("persist.sys.vendor.region", "CN")
            if (!TextUtils.isEmpty(sRegion)) {
                return sRegion
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // 使用反射，防止低版本编译时没有LocaleList类而报错
                    val localeList = Class.forName("android.os.LocaleList")
                        .getMethod("getDefault").invoke(null)
                    val size = localeList.javaClass.getMethod("size").invoke(localeList)
                    if (size is Int && size > 0) {
                        val locale =
                            localeList.javaClass.getMethod("get", Int::class.javaPrimitiveType)
                                .invoke(localeList, 0)
                        val country = locale.javaClass.getMethod("getCountry").invoke(locale)
                        if (country is String) {
                            sRegion = country
                        }
                    }
                }
            } catch (e: Exception) {
                LayoutExceptionLogger.log("RegionUtil#getRealRegion", e)
                e.printStackTrace()
            }
            if (TextUtils.isEmpty(sRegion)) {
                sRegion = Locale.getDefault().country
            }
        }
        if (TextUtils.isEmpty(sRegion)) {
            sRegion = "CN"
        }
        return sRegion
    }

    private fun systemPropertiesGet(key: String?, defaultValue: String): String {
        try {
            var systemPropertiesClass: Class<*>? = null
            try {
                systemPropertiesClass = Class.forName("vendor.os.SystemProperties")
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
//                e.printStackTrace();
            }
            if (systemPropertiesClass == null) {
                systemPropertiesClass = Class.forName("android.os.SystemProperties")
            }
            val method = systemPropertiesClass!!.getDeclaredMethod("get", String::class.java)
            return method.invoke(null, key) as String
        } catch (e: java.lang.Exception) {
                e.printStackTrace()

        }
        return defaultValue
    }
}