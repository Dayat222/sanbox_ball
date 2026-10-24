# ProGuard rules for Stealth Sandbox Engine (sanbox_ball)

# Preserve JNI methods and NativeBridge
-keep class com.sandbox.ball.core.nativebridge.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Preserve HardwareProfile, Models and Serializable classes
-keep class com.sandbox.ball.core.spoofer.** { *; }
-keep class com.sandbox.ball.model.** { *; }
-keep class com.sandbox.ball.core.updater.** { *; }

# Preserve Framework hooks, Proxies and Container runtime
-keep class com.sandbox.ball.core.hooks.** { *; }
-keep class com.sandbox.ball.core.container.** { *; }

# General safety rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.sandbox.ball.**
-dontwarn dalvik.system.**
