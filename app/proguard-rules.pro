# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 保留 Shizuku 相关类
-keep class rikka.shizuku.** { *; }

# 保留 Shower 相关 Binder IPC 类型，确保与 shower-server.jar 的类名保持一致
-keep class com.ai.assistance.shower.ShowerBinderContainer { *; }
-keep class com.ai.assistance.shower.IShowerService { *; }
-keep class com.ai.assistance.shower.IShowerVideoSink { *; }

# 保留自定义的 UserService 类及 AIDL 接口
-keep class com.lyneon.cytoidinfoquerier.service.FileService { *; }
-keep class com.lyneon.cytoidinfoquerier.IFileService { *; }
-keep interface com.lyneon.cytoidinfoquerier.IFileService { *; }

# 保留 ServiceConnection 和 Binder 相关方法
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# 保留 QuickJS 反射绑定对象
-keep class com.ai.assistance.operit.core.tools.javascript.JsEngine$JsToolCallInterface { *; }

# Rules to suppress R8 warnings about missing classes
# SVG Support
-dontwarn com.caverock.androidsvg.SVG
-dontwarn com.caverock.androidsvg.SVGParseException

# Java AWT classes (not available on Android)
-dontwarn java.awt.**
-dontwarn java.awt.color.**
-dontwarn java.awt.geom.**
-dontwarn java.awt.image.**

# Image processing libraries
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**

# Saxon XML
-dontwarn net.sf.saxon.**

# Apache Batik
-dontwarn org.apache.batik.**

# OSGi Framework
-dontwarn org.osgi.framework.**

# XZ compression
-dontwarn org.tukaani.xz.**

# POI dependencies
-dontwarn org.apache.poi.xslf.draw.**
-dontwarn org.apache.poi.xslf.usermodel.**
-dontwarn org.apache.poi.util.**

# PDF Box dependencies
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.fontbox.**

# Apache commons compress
-dontwarn org.apache.commons.compress.archivers.sevenz.**

# xmlbeans
-dontwarn org.apache.xmlbeans.**

# GIF handling
-dontwarn pl.droidsonroids.gif.**

# Reactor BlockHound integration with Netty
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn io.netty.util.internal.Hidden$NettyBlockHoundIntegration