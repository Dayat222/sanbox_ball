package com.sandbox.ball.core.hooks

import android.os.Build
import android.util.Log
import com.sandbox.ball.core.nativebridge.NativeBridge
import com.sandbox.ball.core.spoofer.HardwareProfile

object BuildPropsHook {
    private const val TAG = "BuildPropsHook"

    fun apply(profile: HardwareProfile) {
        if (!profile.isNaturalMode) {
            setBuildProperty("BRAND", profile.brand)
            setBuildProperty("MODEL", profile.model)
            setBuildProperty("MANUFACTURER", profile.manufacturer)
            setBuildProperty("DEVICE", profile.device)
            setBuildProperty("PRODUCT", profile.product)
            setBuildProperty("BOARD", profile.board)
            setBuildProperty("HARDWARE", profile.hardware)
            setBuildProperty("FINGERPRINT", profile.fingerprint)
        }
        setBuildProperty("ID", profile.buildId)
        setBuildProperty("SERIAL", profile.serialNumber)

        Log.i(TAG, "Build properties hooked (NaturalMode: ${profile.isNaturalMode}): ${profile.manufacturer} ${profile.model}")
    }

    private fun setBuildProperty(fieldName: String, value: String) {
        // Method 1: JNI direct field override (Bypasses Java Hidden API policy on Android 9 - 14)
        if (NativeBridge.isNativeLoaded()) {
            val jniSuccess = NativeBridge.nativeSetBuildProp(fieldName, value)
            if (jniSuccess) return
        }

        // Method 2: Safe reflection fallback without accessing blocked 'modifiers' field
        try {
            val field = Build::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set Build.$fieldName via fallback reflection: ${e.message}")
        }
    }
}
