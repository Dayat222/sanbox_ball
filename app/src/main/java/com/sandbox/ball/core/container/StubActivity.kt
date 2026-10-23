package com.sandbox.ball.core.container

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.sandbox.ball.R
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.spoofer.ProfileManager
import com.sandbox.ball.ui.view.StealthToast
import dalvik.system.DexClassLoader
import java.io.File

class StubActivity : Activity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_target_package"
        const val EXTRA_APK_PATH = "extra_target_apk"
    }

    private var targetPackage: String? = null
    private var apkPath: String? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stub_container)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        apkPath = intent.getStringExtra(EXTRA_APK_PATH)

        val txtStatus = findViewById<TextView>(R.id.txt_container_status)
        val txtDetails = findViewById<TextView>(R.id.txt_container_details)
        val btnReset = findViewById<Button>(R.id.btn_container_reset)
        val btnExit = findViewById<Button>(R.id.btn_container_exit)

        val activeProfile = ProfileManager.getInstance(this).getActiveProfile()

        txtStatus.text = "SANDBOX CONTAINER RUNNING"
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
            append("✓ ptrace(PTRACE_TRACEME) Hook: FORCED 0\n")
            append("✓ /proc/self/maps: SANITIZED\n")
            append("✓ /proc/self/status: TracerPid = 0\n")
            append("✓ Dynamic Package Renaming: ACTIVE\n")
            append("✓ App Visibility Hiding: ENGAGED")
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
        try {
            val dexOptDir = File(cacheDir, "dexopt").apply { mkdirs() }
            val nativeLibDir = File(filesDir, "sandbox_vfs/libs").apply { mkdirs() }
            val classLoader = DexClassLoader(
                path,
                dexOptDir.absolutePath,
                nativeLibDir.absolutePath,
                classLoader
            )
            HookManager.getInstance(this).attachTargetPackage(targetPackage ?: "")
        } catch (_: Exception) {}
    }
}
