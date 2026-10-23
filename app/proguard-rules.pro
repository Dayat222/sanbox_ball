# ProGuard rules for Stealth Sandbox Engine
-keep class com.sandbox.ball.core.nativebridge.** { *; }
-keep class com.sandbox.ball.core.spoofer.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-keepattributes *Annotation*
-dontwarn com.sandbox.ball.**
