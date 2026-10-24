package com.sandbox.ball.core.nativebridge

import android.util.Log

object NativeBridge {
    private const val TAG = "NativeBridge"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("stealth_engine")
            isLoaded = true
            Log.i(TAG, "Native stealth_engine library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load stealth_engine native library", e)
            isLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = isLoaded

    external fun nativeInitEngine(
        virtualRootDir: String,
        targetPkg: String,
        fakeMac: String,
        fakeSerial: String
    ): Boolean

    external fun nativeSetVfsEnabled(enabled: Boolean)

    external fun nativeSetAntiDebugEnabled(enabled: Boolean)

    external fun nativeSetMapsFilterEnabled(enabled: Boolean)

    external fun nativeAddPathMapping(virtualPath: String, realPath: String)

    external fun nativeAddPrefixMapping(virtualPrefix: String, realPrefix: String)

    external fun nativeGetEngineStatus(): String

    external fun nativeApplyPltHooks(): Boolean

    external fun nativeSetBuildProp(fieldName: String, fieldValue: String): Boolean

    external fun nativeSetSystemProperties(
        model: String,
        manufacturer: String,
        brand: String,
        device: String,
        board: String,
        fingerprint: String,
        serial: String
    ): Boolean
}
