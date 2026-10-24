package com.sandbox.ball

import com.sandbox.ball.core.hooks.BinderProxyManager
import com.sandbox.ball.core.hooks.PackageManagerHook
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
    }
}
