package com.sandbox.ball.core.hooks

import android.content.Context
import android.util.Log
import com.sandbox.ball.core.nativebridge.NativeBridge
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.model.EvasionStatus
import java.io.File

class HookManager private constructor(private val context: Context) {
    private var activeProfile: HardwareProfile? = null

    companion object {
        private const val TAG = "HookManager"

        @Volatile
        private var instance: HookManager? = null

        fun getInstance(context: Context): HookManager {
            return instance ?: synchronized(this) {
                instance ?: HookManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun applyProfile(profile: HardwareProfile) {
        activeProfile = profile
        TelephonyHook.setProfile(profile)
        SettingsSecureHook.setProfile(profile)
        WifiHook.setProfile(profile)
        BuildPropsHook.apply(profile)
        BinderProxyManager.install(profile)

        if (NativeBridge.isNativeLoaded()) {
            val vfsDir = File(context.filesDir, "sandbox_vfs").absolutePath
            NativeBridge.nativeInitEngine(
                virtualRootDir = vfsDir,
                targetPkg = "",
                fakeMac = profile.macAddress,
                fakeSerial = profile.serialNumber
            )
            NativeBridge.nativeSetSystemProperties(
                model = profile.model,
                manufacturer = profile.manufacturer,
                brand = profile.brand,
                device = profile.device,
                board = profile.board,
                fingerprint = profile.fingerprint,
                serial = profile.serialNumber
            )
            NativeBridge.nativeSetVfsEnabled(true)
            NativeBridge.nativeSetAntiDebugEnabled(true)
            NativeBridge.nativeSetMapsFilterEnabled(true)
            NativeBridge.nativeApplyPltHooks()
        }

        Log.i(TAG, "All stealth hooks updated with profile: ${profile.profileName}")
    }

    fun attachTargetPackage(packageName: String) {
        PackageManagerHook.setTargetPackage(packageName)
        if (NativeBridge.isNativeLoaded()) {
            val vfsDir = File(context.filesDir, "sandbox_vfs").absolutePath
            val profile = activeProfile
            NativeBridge.nativeInitEngine(
                virtualRootDir = vfsDir,
                targetPkg = packageName,
                fakeMac = profile?.macAddress ?: "02:00:00:00:00:00",
                fakeSerial = profile?.serialNumber ?: "RFCT8934"
            )
            if (profile != null) {
                NativeBridge.nativeSetSystemProperties(
                    model = profile.model,
                    manufacturer = profile.manufacturer,
                    brand = profile.brand,
                    device = profile.device,
                    board = profile.board,
                    fingerprint = profile.fingerprint,
                    serial = profile.serialNumber
                )
            }
            NativeBridge.nativeApplyPltHooks()
        }
    }

    fun getEvasionStatus(): EvasionStatus {
        val nativeActive = NativeBridge.isNativeLoaded()
        return EvasionStatus(
            isVfsActive = nativeActive,
            isAntiDebugActive = nativeActive,
            isMapsFilterActive = nativeActive,
            isHardwareSpooferActive = activeProfile != null,
            isAppVisibilityHidingActive = true,
            isNativeHookingEngaged = nativeActive,
            activeModel = activeProfile?.model ?: "Unknown",
            activeImei = activeProfile?.imei ?: "Unknown",
            activeAndroidId = activeProfile?.androidId ?: "Unknown"
        )
    }
}
