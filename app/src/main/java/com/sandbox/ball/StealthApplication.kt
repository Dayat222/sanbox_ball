package com.sandbox.ball

import android.app.Application
import android.os.Build
import android.util.Log
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.nativebridge.NativeBridge
import com.sandbox.ball.core.spoofer.ProfileManager
import java.io.File

class StealthApplication : Application() {
    companion object {
        private const val TAG = "StealthApplication"
        lateinit var instance: StealthApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Install Global Crash Guardian to capture stack traces and prevent silent terminations
        installCrashGuardian()

        val processName = getProcessNameCompat()
        Log.i(TAG, "Initializing Stealth Sandbox Engine in process: $processName")

        // 2. Initialize Profile Manager and load active hardware profile
        val profileManager = ProfileManager.getInstance(this)
        val activeProfile = profileManager.getActiveProfile()

        // 3. Process-aware hook activation:
        // Invasive VFS, PLT/GOT syscall redirection, and Binder proxies are ONLY needed
        // inside the isolated container process (":container").
        // The main dashboard process ("com.sandbox.ball") must run unhindered to guarantee UI stability!
        if (processName.endsWith(":container")) {
            if (activeProfile != null) {
                HookManager.getInstance(this).applyProfile(activeProfile)
            }
            if (NativeBridge.isNativeLoaded()) {
                Log.i(TAG, "Native VFS and Anti-Debug hooker engine engaged in :container process")
            } else {
                Log.w(TAG, "Native library not loaded in container process; using Java hooks")
            }
        } else {
            Log.i(TAG, "Main dashboard process running with native Android bindings (Stability Mode)")
        }
    }

    private fun installCrashGuardian() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
                val crashFile = File(filesDir, "last_crash.log")
                crashFile.writeText(buildString {
                    append("TIMESTAMP: ${java.util.Date()}\n")
                    append("PROCESS: ${getProcessNameCompat()}\n")
                    append("THREAD: ${thread.name}\n")
                    append("EXCEPTION: ${throwable.javaClass.name}: ${throwable.message}\n\n")
                    append("STACK TRACE:\n")
                    append(Log.getStackTraceString(throwable))
                })
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getProcessNameCompat(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return try {
            File("/proc/self/cmdline").readText().trim().trim { it <= ' ' || it == '\u0000' }
        } catch (_: Exception) {
            packageName
        }
    }
}
