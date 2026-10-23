package com.sandbox.ball.core.container

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.core.spoofer.ProfileManager
import com.sandbox.ball.model.ClonedAppInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppContainer(private val context: Context) {
    companion object {
        private const val TAG = "AppContainer"
        private const val PREFS_NAME = "stealth_app_container"
        private const val KEY_CLONED_APPS = "cloned_apps_list"

        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    val vfs = VirtualFileSystem(context)
    private val profileManager = ProfileManager.getInstance(context)

    fun getClonedApps(): List<ClonedAppInfo> {
        val json = prefs.getString(KEY_CLONED_APPS, null) ?: return emptyList()
        val type = object : TypeToken<List<ClonedAppInfo>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveClonedApps(list: List<ClonedAppInfo>) {
        prefs.edit().putString(KEY_CLONED_APPS, gson.toJson(list)).apply()
    }

    fun isAppCloned(packageName: String): Boolean {
        return getClonedApps().any { it.packageName == packageName }
    }

    /**
     * PRD Core Feature: Kloning Aplikasi
     * Copies APK from OS into sandbox container directory without root.
     */
    fun cloneApp(packageName: String): ClonedAppInfo {
        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        val appInfo = pkgInfo.applicationInfo ?: throw IllegalArgumentException("ApplicationInfo not found")

        val appName = pm.getApplicationLabel(appInfo).toString()
        val sourceApk = File(appInfo.sourceDir)
        val containerDir = vfs.getAppContainerDir(packageName)
        val targetApk = File(containerDir, "base.apk")

        // Copy APK to isolated sandbox container
        copyFile(sourceApk, targetApk)

        // Initialize virtual directories
        vfs.getAppDataDir(packageName)
        vfs.registerAppVfs(packageName)

        val activeProfile = profileManager.getActiveProfile()

        val clonedApp = ClonedAppInfo(
            packageName = packageName,
            appName = appName,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            sourceApkPath = targetApk.absolutePath,
            containerDir = containerDir.absolutePath,
            cloneDate = System.currentTimeMillis(),
            profileId = activeProfile?.profileId ?: "",
            profileName = activeProfile?.profileName ?: "Default Stealth Profile",
            isStealthShieldEnabled = true
        )

        val currentList = getClonedApps().toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            currentList[index] = clonedApp
        } else {
            currentList.add(clonedApp)
        }
        saveClonedApps(currentList)

        Log.i(TAG, "Application $packageName cloned successfully to ${targetApk.absolutePath}")
        return clonedApp
    }

    /**
     * Launches target cloned app inside isolated sandbox environment.
     */
    fun launchApp(clonedApp: ClonedAppInfo): Boolean {
        // Arm hooks and VFS for the target app
        HookManager.getInstance(context).attachTargetPackage(clonedApp.packageName)
        vfs.registerAppVfs(clonedApp.packageName)

        // Launch app using StubActivity or launch intent with intercepted environment
        val intent = Intent(context, StubActivity::class.java).apply {
            putExtra(StubActivity.EXTRA_PACKAGE_NAME, clonedApp.packageName)
            putExtra(StubActivity.EXTRA_APK_PATH, clonedApp.sourceApkPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * PRD Core Feature: 1-Click Reset for specific cloned app
     * Clears container storage cache and assigns fresh hardware identity
     */
    fun resetApp(packageName: String): HardwareProfile {
        vfs.clearAppData(packageName)
        val newProfile = profileManager.rotateIdentity(clearSandboxCache = true)

        val list = getClonedApps().toMutableList()
        val index = list.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            val updated = list[index].copy(
                profileId = newProfile.profileId,
                profileName = newProfile.profileName
            )
            list[index] = updated
            saveClonedApps(list)
        }

        return newProfile
    }

    fun removeClonedApp(packageName: String): Boolean {
        vfs.clearAppData(packageName)
        val containerDir = vfs.getAppContainerDir(packageName)
        containerDir.deleteRecursively()

        val list = getClonedApps().filter { it.packageName != packageName }
        saveClonedApps(list)
        return true
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                inStream.channel.transferTo(0, inStream.channel.size(), outStream.channel)
            }
        }
    }
}
