package com.sandbox.ball.core.container

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import java.io.File

class ContainerContext(
    base: Context,
    private val targetPackageName: String,
    private val containerDataDir: File,
    private val apkFile: File,
    private val containerClassLoader: ClassLoader
) : ContextWrapper(base) {

    private val filesDirFile = File(containerDataDir, "files").apply { mkdirs() }
    private val cacheDirFile = File(containerDataDir, "cache").apply { mkdirs() }
    private val codeCacheDirFile = File(containerDataDir, "code_cache").apply { mkdirs() }
    private val noBackupDirFile = File(containerDataDir, "no_backup").apply { mkdirs() }
    private val databasesDirFile = File(containerDataDir, "databases").apply { mkdirs() }
    private val sharedPrefsDirFile = File(containerDataDir, "shared_prefs").apply { mkdirs() }

    // Isolated external storage directory mimicking /storage/emulated/0/Android/data/<pkg>
    private val externalDataDir = File(containerDataDir, "sdcard/Android/data/$targetPackageName").apply { mkdirs() }
    private val externalFilesDir = File(externalDataDir, "files").apply { mkdirs() }
    private val externalCacheDir = File(externalDataDir, "cache").apply { mkdirs() }

    private var customResources: Resources? = null
    private var customAssetManager: AssetManager? = null

    val targetApplication: Application by lazy {
        createTargetApplication()
    }

    init {
        initCustomResources(base)
    }

    private fun initCustomResources(base: Context) {
        try {
            val assetManagerClass = AssetManager::class.java
            val assetManager = assetManagerClass.getDeclaredConstructor().newInstance()
            val addAssetPathMethod = assetManagerClass.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.isAccessible = true
            addAssetPathMethod.invoke(assetManager, apkFile.absolutePath)

            val baseRes = base.resources
            customAssetManager = assetManager
            customResources = Resources(assetManager, baseRes.displayMetrics, baseRes.configuration)
        } catch (_: Exception) {
            customResources = base.resources
            customAssetManager = base.assets
        }
    }

    private fun createTargetApplication(): Application {
        return try {
            val dummyApp = object : Application() {
                override fun getPackageName(): String = targetPackageName
                override fun getApplicationContext(): Context = this
            }
            val baseField = ContextWrapper::class.java.getDeclaredField("mBase")
            baseField.isAccessible = true
            baseField.set(dummyApp, this)
            dummyApp
        } catch (_: Exception) {
            object : Application() {
                override fun getPackageName(): String = targetPackageName
                override fun getApplicationContext(): Context = this
            }
        }
    }

    override fun getPackageName(): String = targetPackageName

    // Anti-Shadowban FIX: Return target Application proxy so getApplicationContext() never leaks host com.sandbox.ball
    override fun getApplicationContext(): Context = targetApplication

    override fun getDataDir(): File = containerDataDir

    override fun getFilesDir(): File = filesDirFile

    override fun getCacheDir(): File = cacheDirFile

    override fun getCodeCacheDir(): File = codeCacheDirFile

    override fun getNoBackupFilesDir(): File = noBackupDirFile

    override fun getDatabasePath(name: String): File {
        return File(databasesDirFile, name)
    }

    override fun getDir(name: String, mode: Int): File {
        return File(containerDataDir, "app_$name").apply { mkdirs() }
    }

    override fun getExternalFilesDir(type: String?): File {
        val dir = if (type == null) externalFilesDir else File(externalFilesDir, type)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getExternalFilesDirs(type: String?): Array<File> {
        return arrayOf(getExternalFilesDir(type))
    }

    override fun getExternalCacheDir(): File = externalCacheDir

    override fun getExternalCacheDirs(): Array<File> {
        return arrayOf(externalCacheDir)
    }

    override fun getClassLoader(): ClassLoader = containerClassLoader

    override fun getResources(): Resources = customResources ?: super.getResources()

    override fun getAssets(): AssetManager = customAssetManager ?: super.getAssets()

    override fun getApplicationInfo(): ApplicationInfo {
        val baseInfo = super.getApplicationInfo()
        val customInfo = ApplicationInfo(baseInfo)
        customInfo.packageName = targetPackageName
        customInfo.dataDir = containerDataDir.absolutePath
        customInfo.sourceDir = apkFile.absolutePath
        customInfo.publicSourceDir = apkFile.absolutePath
        return customInfo
    }
}
