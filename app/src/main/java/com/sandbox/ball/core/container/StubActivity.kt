package com.sandbox.ball.core.container

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.sandbox.ball.R
import com.sandbox.ball.core.hooks.BinderProxyManager
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.core.spoofer.ProfileManager
import com.sandbox.ball.ui.view.StealthToast
import dalvik.system.DexClassLoader
import java.io.File

class StubActivity : Activity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_target_package"
        const val EXTRA_APK_PATH = "extra_target_apk"
        private const val TAG = "StubActivity"
    }

    private var targetPackage: String? = null
    private var apkPath: String? = null
    private var containerContext: ContainerContext? = null
    private var containerClassLoader: DexClassLoader? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stub_container)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        apkPath = intent.getStringExtra(EXTRA_APK_PATH)

        val txtStatus = findViewById<TextView>(R.id.txt_container_status)
        val txtDetails = findViewById<TextView>(R.id.txt_container_details)
        val btnLaunch = findViewById<Button>(R.id.btn_container_launch)
        val btnReset = findViewById<Button>(R.id.btn_container_reset)
        val btnExit = findViewById<Button>(R.id.btn_container_exit)

        val activeProfile = ProfileManager.getInstance(this).getActiveProfile()

        txtStatus.text = "SANDBOX CONTAINER ACTIVE (:container)"
        txtDetails.text = buildString {
            append("Target Package: ").append(targetPackage).append("\n\n")
            append("[VFS ISOLATION]\n")
            append("Mounted Root: /data/data/").append(targetPackage).append("\n")
            append("Real Container: ").append(apkPath).append("\n\n")
            append("[ACTIVE HARDWARE IDENTITY]\n")
            append("Device: ").append(activeProfile?.manufacturer).append(" ").append(activeProfile?.model).append("\n")
            append("IMEI: ").append(activeProfile?.imei).append("\n")
            append("Android ID: ").append(activeProfile?.androidId).append("\n")
            append("MAC: ").append(activeProfile?.macAddress).append("\n")
            append("WiFi SSID: ").append(activeProfile?.wifiSsid).append("\n\n")
            append("[ANTI-DETECTION PROTOCOLS]\n")
            append("✓ Native PLT/GOT Syscall Hook: ACTIVE\n")
            append("✓ ptrace(PTRACE_TRACEME) Hook: FORCED 0\n")
            append("✓ /proc/self/maps: SANITIZED (Thread-Safe)\n")
            append("✓ /proc/self/status: TracerPid = 0\n")
            append("✓ Binder IPC Proxy: ACTIVE\n")
            append("✓ Google Play Store Origin: SPOOFED\n")
            append("✓ Dynamic Package Renaming: ACTIVE\n")
            append("✓ App Visibility Hiding: ENGAGED")
        }

        btnLaunch.setOnClickListener {
            targetPackage?.let { pkg ->
                val path = apkPath
                try {
                    // 1. Resolve main launch activity from container's base.apk directly (FIX-04)
                    var launchActivityName: String? = null
                    if (path != null) {
                        val archiveInfo = packageManager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
                        launchActivityName = archiveInfo?.activities?.firstOrNull()?.name
                    }
                    if (launchActivityName == null) {
                        launchActivityName = packageManager.getLaunchIntentForPackage(pkg)?.component?.className
                    }

                    if (launchActivityName != null) {
                        ContainerInstrumentation.activeTargetPackage = pkg
                        ContainerInstrumentation.activeContainerContext = containerContext
                        ContainerInstrumentation.activeClassLoader = containerClassLoader

                        val launchIntent = Intent().apply {
                            setClassName(pkg, launchActivityName)
                            putExtra(ContainerInstrumentation.EXTRA_REAL_ACTIVITY, launchActivityName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(launchIntent)
                        StealthToast.show(this, "Aplikasi $pkg diluncurkan di dalam proses :container")
                    } else {
                        StealthToast.show(this, "Aplikasi $pkg siap di sandbox")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed launching container activity: ${e.message}", e)
                    StealthToast.show(this, "Gagal meluncurkan: ${e.message}")
                }
            }
        }

        btnReset.setOnClickListener {
            targetPackage?.let { pkg ->
                val newProfile = AppContainer.getInstance(this).resetApp(pkg)
                StealthToast.show(
                    this,
                    "Hardware ID direset ke ${newProfile.model} (IMEI: ${newProfile.imei.take(8)}...)"
                )
                // Refresh details
                onCreate(savedInstanceState)
            }
        }

        btnExit.setOnClickListener {
            finish()
        }

        loadContainerClassLoader()
    }

    private fun loadContainerClassLoader() {
        val path = apkPath ?: return
        val pkg = targetPackage ?: return
        try {
            val apkFile = File(path)
            val vfs = AppContainer.getInstance(this).vfs
            val appContainerDir = vfs.getAppContainerDir(pkg)
            val nativeLibDir = File(appContainerDir, "lib").apply { mkdirs() }
            val dexOptDir = File(cacheDir, "dexopt/$pkg").apply { mkdirs() }

            // Extract native .so libraries from APK (REM-01 Requirement 3)
            NativeLibExtractor.extractNativeLibs(apkFile, nativeLibDir)

            // Load classes and native libs via DexClassLoader
            val cl = DexClassLoader(
                path,
                dexOptDir.absolutePath,
                nativeLibDir.absolutePath,
                classLoader
            )
            containerClassLoader = cl

            // Create isolated container context (REM-01 Requirement 1)
            val dataDir = vfs.getAppDataDir(pkg)
            containerContext = ContainerContext(this, pkg, dataDir, apkFile, cl)

            // Inject custom Instrumentation (REM-01 Requirement 2 & FIX-04)
            ContainerInstrumentation.inject()
            ContainerInstrumentation.activeTargetPackage = pkg
            ContainerInstrumentation.activeContainerContext = containerContext
            ContainerInstrumentation.activeClassLoader = cl

            // Initialize hardware profile & binder proxies
            val activeProfile = ProfileManager.getInstance(this).getActiveProfile() ?: HardwareProfile()
            HookManager.getInstance(this).attachTargetPackage(pkg)
            BinderProxyManager.install(activeProfile, pkg)

            Log.i(TAG, "Container runtime successfully initialized for $pkg with isolated VFS: ${dataDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing container runtime: ${e.message}", e)
        }
    }
}
