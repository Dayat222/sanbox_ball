package com.sandbox.ball

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
import com.sandbox.ball.core.hooks.BinderProxyManager
import com.sandbox.ball.core.hooks.PackageManagerHook
import com.sandbox.ball.core.hooks.SettingsSecureHook
import com.sandbox.ball.core.hooks.WifiHook
import com.sandbox.ball.core.spoofer.HardwareProfile
import org.junit.Assert.*
import org.junit.Test

class BinderProxyManagerTest {

    @Test
    fun testPlayStoreInstallerSpoofing() {
        val installer = PackageManagerHook.getInstallerPackageName("com.miniclip.eightballpool")
        assertEquals("Installer must be Google Play Store", "com.android.vending", installer)

        val origin = PackageManagerHook.getOriginatingPackageName()
        assertEquals("Origin must be Google Play Store", "com.android.vending", origin)
    }

    @Test
    fun testPackageVisibilityFiltering() {
        // Sandboxed package must be visible
        PackageManagerHook.setTargetPackage("com.miniclip.eightballpool")
        assertTrue(PackageManagerHook.isPackageVisible("com.miniclip.eightballpool"))

        // Common system packages must be visible
        assertTrue(PackageManagerHook.isPackageVisible("android"))
        assertTrue(PackageManagerHook.isPackageVisible("com.google.android.gms"))
        assertTrue(PackageManagerHook.isPackageVisible("com.android.vending"))

        // Suspicious tool packages must be hidden
        assertFalse(PackageManagerHook.isPackageVisible("de.robv.android.xposed.installer"))
        assertFalse(PackageManagerHook.isPackageVisible("com.topjohnwu.magisk"))
        assertFalse(PackageManagerHook.isPackageVisible("com.noshufou.android.su"))

        // Sandbox host package must be hidden from guest game
        assertFalse(PackageManagerHook.isPackageVisible("com.sandbox.ball"))

        // When no guest app is running (host dashboard mode), host app must be visible to itself
        PackageManagerHook.setTargetPackage(null)
        assertTrue(PackageManagerHook.isPackageVisible("com.sandbox.ball"))
    }

    @Test
    fun testSettingsSecureIntercept() {
        val profile = HardwareProfile(androidId = "aabbccddeeff0011", model = "SM-S928B", brand = "samsung")
        SettingsSecureHook.setProfile(profile)

        val interceptedId = SettingsSecureHook.intercept(Settings.Secure.ANDROID_ID, "original_hardware_id")
        assertEquals("aabbccddeeff0011", interceptedId)

        val interceptedName = SettingsSecureHook.intercept("device_name", "original_device")
        assertEquals("SM-S928B", interceptedName)
    }

    @Test
    fun testWifiHookSpoofing() {
        val profile = HardwareProfile(
            macAddress = "02:11:22:33:44:55",
            wifiSsid = "Test_WiFi_5G",
            wifiBssid = "00:11:22:33:44:55",
            fakeIpAddress = "192.168.1.150"
        )
        WifiHook.setProfile(profile)

        assertEquals("02:11:22:33:44:55", WifiHook.getMacAddress())
        assertEquals("\"Test_WiFi_5G\"", WifiHook.getSSID())
        assertEquals("00:11:22:33:44:55", WifiHook.getBSSID())
        assertTrue("IP address integer must be non-zero", WifiHook.getIpAddressInt() != 0)
    }

    @Test
    fun testFilterPackageInfoList() {
        PackageManagerHook.setTargetPackage("com.miniclip.eightballpool")

        val targetPkg = PackageInfo().apply {
            packageName = "com.miniclip.eightballpool"
            applicationInfo = ApplicationInfo().apply { flags = 0 }
        }
        val systemPkg = PackageInfo().apply {
            packageName = "com.android.vending"
            applicationInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
        }
        val blockedPkg = PackageInfo().apply {
            packageName = "com.topjohnwu.magisk"
            applicationInfo = ApplicationInfo().apply { flags = 0 }
        }

        val originalList = listOf(targetPkg, systemPkg, blockedPkg)
        val filtered = PackageManagerHook.filterPackageInfoList(originalList)

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.packageName == "com.miniclip.eightballpool" })
        assertTrue(filtered.any { it.packageName == "com.android.vending" })
        assertFalse(filtered.any { it.packageName == "com.topjohnwu.magisk" })
    }

    @Test
    fun testDynamicProfileUpdateInBinderProxyManager() {
        val initialProfile = HardwareProfile(imei = "358941209847192", model = "SM-S928B")
        BinderProxyManager.install(initialProfile)
        assertEquals("358941209847192", BinderProxyManager.getActiveProfile().imei)

        // 1-Click Reset generates a new profile and updates BinderProxyManager (FIX-01)
        val rotatedProfile = HardwareProfile(imei = "359999999999999", model = "Pixel 8 Pro")
        BinderProxyManager.install(rotatedProfile)
        assertEquals("359999999999999", BinderProxyManager.getActiveProfile().imei)
        assertEquals("Pixel 8 Pro", BinderProxyManager.getActiveProfile().model)
    }
}
