package com.sandbox.ball.core.container

import android.content.Context
import android.util.Log
import com.sandbox.ball.core.nativebridge.NativeBridge
import java.io.File

class VirtualFileSystem(private val context: Context) {
    companion object {
        private const val TAG = "VirtualFileSystem"
    }

    val virtualRoot: File = File(context.filesDir, "sandbox_vfs")

    init {
        ensureDirectories()
    }

    private fun ensureDirectories() {
        if (!virtualRoot.exists()) {
            virtualRoot.mkdirs()
        }
        File(virtualRoot, "apps").mkdirs()
        File(virtualRoot, "data").mkdirs()
        File(virtualRoot, "cache").mkdirs()
        File(virtualRoot, "fake_sys").mkdirs()
    }

    fun getAppContainerDir(packageName: String): File {
        val dir = File(File(virtualRoot, "apps"), packageName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAppDataDir(packageName: String): File {
        val dir = File(File(virtualRoot, "data"), packageName)
        if (!dir.exists()) {
            dir.mkdirs()
            File(dir, "files").mkdirs()
            File(dir, "databases").mkdirs()
            File(dir, "shared_prefs").mkdirs()
            File(dir, "cache").mkdirs()
            File(dir, "sdcard/Android/data/$packageName").mkdirs()
        }
        return dir
    }

    fun registerAppVfs(packageName: String) {
        val appDataDir = getAppDataDir(packageName)
        val appContainerDir = getAppContainerDir(packageName)
        val externalDataDir = File(appDataDir, "sdcard/Android/data/$packageName").apply { mkdirs() }

        if (NativeBridge.isNativeLoaded()) {
            // Map /data/data/<pkg> to our isolated container directory
            NativeBridge.nativeAddPrefixMapping("/data/data/$packageName", appDataDir.absolutePath)
            NativeBridge.nativeAddPrefixMapping("/data/user/0/$packageName", appDataDir.absolutePath)
            NativeBridge.nativeAddPrefixMapping("/data/app/$packageName", appContainerDir.absolutePath)

            // Anti-Shadowban FIX: Isolate external storage tokens (/storage/emulated/0 and /sdcard)
            NativeBridge.nativeAddPrefixMapping("/storage/emulated/0/Android/data/$packageName", externalDataDir.absolutePath)
            NativeBridge.nativeAddPrefixMapping("/sdcard/Android/data/$packageName", externalDataDir.absolutePath)
            NativeBridge.nativeAddPrefixMapping("/storage/emulated/0/Android/media/$packageName", externalDataDir.absolutePath)
            NativeBridge.nativeAddPrefixMapping("/sdcard/Android/media/$packageName", externalDataDir.absolutePath)

            Log.i(TAG, "VFS mappings registered for $packageName -> ${appDataDir.absolutePath}")
        }
    }

    fun clearAppData(packageName: String) {
        val dataDir = File(File(virtualRoot, "data"), packageName)
        if (dataDir.exists()) {
            dataDir.deleteRecursively()
            dataDir.mkdirs()
            File(dataDir, "files").mkdirs()
            File(dataDir, "databases").mkdirs()
            File(dataDir, "shared_prefs").mkdirs()
            File(dataDir, "cache").mkdirs()
            File(dataDir, "sdcard/Android/data/$packageName").mkdirs()
        }

        // Also clean up compiled dexopt cache for this package
        try {
            val dexOptDir = File(context.cacheDir, "dexopt/$packageName")
            if (dexOptDir.exists()) dexOptDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
