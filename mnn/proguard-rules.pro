# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep MNN native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep MNN classes
-keep class com.ai.assistance.mnn.** { *; }

# Keep MNN enums
-keepclassmembers enum com.ai.assistance.mnn.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

