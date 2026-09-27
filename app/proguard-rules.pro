# NetLocker release ProGuard/R8 rules.
# Add project specific ProGuard rules here.

# Keep Room entities/DAOs (annotation-driven, but keep names for schema export safety).
-keep class com.netlocker.data.local.entity.** { *; }

# Kotlin coroutines / metadata
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn kotlinx.coroutines.**

# Strip verbose/debug NetLocker logging in release builds (see util/Logger.kt).
-assumenosideeffects class com.netlocker.util.Logger {
    public static void d(...);
    public static void v(...);
}
