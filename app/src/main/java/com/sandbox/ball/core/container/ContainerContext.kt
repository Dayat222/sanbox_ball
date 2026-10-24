package com.sandbox.ball.core.container

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
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

    private var customResources: Resources? = null
    private var customAssetManager: AssetManager? = null

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

    override fun getPackageName(): String = targetPackageName

    // FIX-02: Prevent leaking sandbox host identity (com.sandbox.ball) when target app calls getApplicationContext()
    override fun getApplicationContext(): Context = this

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
