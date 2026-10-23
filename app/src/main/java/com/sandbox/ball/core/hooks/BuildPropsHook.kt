package com.sandbox.ball.core.hooks

import android.os.Build
import android.util.Log
import com.sandbox.ball.core.spoofer.HardwareProfile
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object BuildPropsHook {
    private const val TAG = "BuildPropsHook"

    fun apply(profile: HardwareProfile) {
        setStaticField(Build::class.java, "BRAND", profile.brand)
        setStaticField(Build::class.java, "MODEL", profile.model)
        setStaticField(Build::class.java, "MANUFACTURER", profile.manufacturer)
        setStaticField(Build::class.java, "DEVICE", profile.device)
        setStaticField(Build::class.java, "PRODUCT", profile.product)
        setStaticField(Build::class.java, "BOARD", profile.board)
        setStaticField(Build::class.java, "HARDWARE", profile.hardware)
        setStaticField(Build::class.java, "FINGERPRINT", profile.fingerprint)
        setStaticField(Build::class.java, "ID", profile.buildId)
        setStaticField(Build::class.java, "SERIAL", profile.serialNumber)

        Log.i(TAG, "Build properties hooked: ${profile.manufacturer} ${profile.model}")
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true

            // Handle static final modifier where allowed
            try {
                val modifiersField = Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            } catch (_: Exception) {}

            field.set(null, value)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set Build.$fieldName via reflection: ${e.message}")
        }
    }
}
