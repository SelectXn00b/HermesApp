# Consumer ProGuard rules for MNN module

# Keep MNN native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep MNN public API
-keep public class com.ai.assistance.mnn.** {
    public *;
}

