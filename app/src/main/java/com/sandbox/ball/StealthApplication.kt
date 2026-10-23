package com.sandbox.ball

import android.app.Application
import android.util.Log
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.nativebridge.NativeBridge
import com.sandbox.ball.core.spoofer.ProfileManager

class StealthApplication : Application() {
    companion object {
        private const val TAG = "StealthApplication"
        lateinit var instance: StealthApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Initializing Stealth Sandbox Engine...")

        // 1. Initialize Profile Manager and load or generate active hardware profile
        val profileManager = ProfileManager.getInstance(this)
        val activeProfile = profileManager.getActiveProfile()

        // 2. Initialize Hook Manager and arm all frameworks
        if (activeProfile != null) {
            HookManager.getInstance(this).applyProfile(activeProfile)
        }

        // 3. Log native bridge state
        if (NativeBridge.isNativeLoaded()) {
            Log.i(TAG, "Native VFS and Anti-Debug hooker engine engaged successfully")
        } else {
            Log.w(TAG, "Native library not loaded; falling back to Java framework interception")
        }
    }
}
