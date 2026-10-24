package com.sandbox.ball

import android.content.ContextWrapper
import com.sandbox.ball.core.container.ContainerContext
import com.sandbox.ball.core.container.VirtualFileSystem
import com.sandbox.ball.core.hooks.SettingsSecureHook
import com.sandbox.ball.core.spoofer.HardwareProfile
import com.sandbox.ball.core.spoofer.ProfileGenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AntiCheatEvasionTest {

    @Test
    fun testAdvertisingIdFormat() {
        val profile = ProfileGenerator.generateRandomProfile()
        assertNotNull("Advertising ID must not be null", profile.advertisingId)
        assertTrue("Advertising ID must be valid UUID string",
            profile.advertisingId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))

        // Test natural profile generation
        val natural = ProfileGenerator.generateNaturalProfile()
        assertTrue("Natural profile advertising ID must be valid UUID",
            natural.advertisingId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
    }

    @Test
    fun testSettingsSecureAdvertisingIdIntercept() {
        val testUuid = UUID.randomUUID().toString()
        val profile = HardwareProfile(advertisingId = testUuid, oaid = "test-oaid-1234")
        SettingsSecureHook.setProfile(profile)

        val interceptedAdvId = SettingsSecureHook.intercept("advertising_id", "real-hw-id")
        assertEquals("advertising_id must be intercepted and spoofed", testUuid, interceptedAdvId)

        val interceptedOaid = SettingsSecureHook.intercept("oaid", "real-oaid")
        assertEquals("oaid must be intercepted and spoofed", "test-oaid-1234", interceptedOaid)
    }

    @Test
    fun testContainerContextTargetApplicationIdentity() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_container_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val dummyApk = File(tempDir, "base.apk").apply { createNewFile() }

        try {
            val dummyBase = object : ContextWrapper(null) {}
            val targetPkg = "com.miniclip.eightballpool"

            val containerContext = ContainerContext(
                base = dummyBase,
                targetPackageName = targetPkg,
                containerDataDir = tempDir,
                apkFile = dummyApk,
                containerClassLoader = AntiCheatEvasionTest::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
            )

            // Verify package name masking
            assertEquals("ContainerContext must return target package", targetPkg, containerContext.packageName)
            assertEquals("targetApplication must return target package name", targetPkg, containerContext.targetApplication.packageName)
            assertEquals("getApplicationContext() must match targetApplication", containerContext.targetApplication, containerContext.applicationContext)

            // Verify isolated external storage paths
            val extFiles = containerContext.getExternalFilesDir(null)
            assertTrue("External files dir must be isolated inside sdcard path",
                extFiles.absolutePath.contains("sdcard${File.separator}Android${File.separator}data${File.separator}$targetPkg"))

            val extCache = containerContext.externalCacheDir
            assertTrue("External cache dir must be isolated inside sdcard path",
                extCache.absolutePath.contains("sdcard${File.separator}Android${File.separator}data${File.separator}$targetPkg"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testExternalStorageResetCleanliness() {
        val tempBase = File(System.getProperty("java.io.tmpdir"), "test_vfs_clean_${System.currentTimeMillis()}")
        tempBase.mkdirs()
        val targetPkg = "com.miniclip.eightballpool"

        try {
            val dummyBase = object : ContextWrapper(null) {
                override fun getFilesDir(): File = tempBase
            }
            val vfs = VirtualFileSystem(dummyBase)

            val appDataDir = vfs.getAppDataDir(targetPkg)
            val externalDir = File(appDataDir, "sdcard/Android/data/$targetPkg/files").apply { mkdirs() }
            val sensitiveTokenFile = File(externalDir, "miniclip_account_token.dat").apply { writeText("secret_banned_id") }

            assertTrue("Token file must exist before reset", sensitiveTokenFile.exists())

            // Execute clearAppData
            vfs.clearAppData(targetPkg)

            assertFalse("Token file in external storage must be wiped after clearAppData", sensitiveTokenFile.exists())
            assertTrue("Target app data directory should exist as clean recreated container", appDataDir.exists())
            val cleanExtDir = File(appDataDir, "sdcard/Android/data/$targetPkg")
            assertTrue("Clean external directory must exist", cleanExtDir.exists())
            assertTrue("Sensitive files inside external directory must be wiped", cleanExtDir.listFiles()?.isEmpty() == true)
        } finally {
            tempBase.deleteRecursively()
        }
    }
}
