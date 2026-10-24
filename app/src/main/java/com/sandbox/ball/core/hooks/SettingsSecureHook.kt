package com.sandbox.ball.core.hooks

import android.provider.Settings
import android.util.Log
import com.sandbox.ball.core.spoofer.HardwareProfile

object SettingsSecureHook {
    private const val TAG = "SettingsSecureHook"
    private var activeProfile: HardwareProfile? = null

    fun setProfile(profile: HardwareProfile) {
        activeProfile = profile
        installInMemoryHook(profile)
        Log.i(TAG, "SettingsSecureHook armed with Android ID: ${profile.androidId}")
    }

    /**
     * Intercepts Settings.Secure.getString calls directly
     */
    fun intercept(name: String, originalValue: String?): String? {
        val profile = activeProfile ?: return originalValue
        return when (name) {
            Settings.Secure.ANDROID_ID -> profile.androidId
            "advertising_id" -> profile.advertisingId
            "oaid" -> profile.oaid
            "bluetooth_name" -> "${profile.brand.uppercase()} ${profile.model}"
            "device_name" -> profile.model
            else -> originalValue
        }
    }

    /**
     * Injects spoofed values into Settings.Secure's internal static cache (sNameValueCache)
     * so that Settings.Secure.getString() reads the spoofed values without IPC query.
     */
    @Suppress("UNCHECKED_CAST")
    fun installInMemoryHook(profile: HardwareProfile) {
        try {
            val secureClass = Class.forName("android.provider.Settings\$Secure")
            val cacheField = secureClass.declaredFields.firstOrNull {
                it.name == "sNameValueCache" || it.type.name.contains("NameValueCache")
            }
            if (cacheField != null) {
                cacheField.isAccessible = true
                val nameValueCache = cacheField.get(null)
                if (nameValueCache != null) {
                    val valuesField = nameValueCache.javaClass.declaredFields.firstOrNull {
                        it.name == "mValues" || Map::class.java.isAssignableFrom(it.type)
                    }
                    if (valuesField != null) {
                        valuesField.isAccessible = true
                        var map = valuesField.get(nameValueCache) as? MutableMap<String, String>
                        if (map == null) {
                            map = HashMap()
                            valuesField.set(nameValueCache, map)
                        }
                        map[Settings.Secure.ANDROID_ID] = profile.androidId
                        map["advertising_id"] = profile.advertisingId
                        map["oaid"] = profile.oaid
                        map["bluetooth_name"] = "${profile.brand.uppercase()} ${profile.model}"
                        map["device_name"] = profile.model
                        Log.i(TAG, "In-memory Settings.Secure cache hooked with Android ID: ${profile.androidId}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "In-memory hook for Settings.Secure notice: ${e.message}")
        }
    }
}
