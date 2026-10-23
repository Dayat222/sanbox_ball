package com.sandbox.ball

import com.sandbox.ball.core.hooks.PackageManagerHook
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PackageManagerHookTest {

    @Before
    fun setUp() {
        PackageManagerHook.setTargetPackage("com.target.game")
        PackageManagerHook.setVisibilityHidingEnabled(true)
    }

    @Test
    fun testTargetPackageAllowed() {
        assertTrue(PackageManagerHook.isPackageAllowed("com.target.game"))
    }

    @Test
    fun testBlacklistedPackagesBlocked() {
        assertFalse("Host package must be hidden", PackageManagerHook.isPackageAllowed("com.sandbox.ball"))
        assertFalse("Magisk must be hidden", PackageManagerHook.isPackageAllowed("com.topjohnwu.magisk"))
        assertFalse("SuperSU must be hidden", PackageManagerHook.isPackageAllowed("eu.chainfire.supersu"))
        assertFalse("Xposed must be hidden", PackageManagerHook.isPackageAllowed("de.robv.android.xposed.installer"))
        assertFalse("LuckyPatcher must be hidden", PackageManagerHook.isPackageAllowed("com.chelpus.luckypatcher"))
    }
}
