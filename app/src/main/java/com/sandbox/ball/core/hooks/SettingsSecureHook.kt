package com.sandbox.ball.core.hooks

import android.provider.Settings
import android.util.Log
import com.sandbox.ball.core.spoofer.HardwareProfile

object SettingsSecureHook {
    private const val TAG = "SettingsSecureHook"
    private var activeProfile: HardwareProfile? = null

    fun setProfile(profile: HardwareProfile) {
        activeProfile = profile
        Log.i(TAG, "SettingsSecureHook armed with Android ID: ${profile.androidId}")
    }

    /**
     * Intercepts Settings.Secure.getString calls
     */
    fun intercept(name: String, originalValue: String?): String? {
        val profile = activeProfile ?: return originalValue
        return when (name) {
            Settings.Secure.ANDROID_ID -> profile.androidId
            "bluetooth_name" -> "${profile.brand.uppercase()} ${profile.model}"
            "device_name" -> profile.model
            else -> originalValue
        }
    }
}
