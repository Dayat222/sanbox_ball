package com.sandbox.ball.core.hooks

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.util.Log

object PackageManagerHook {
    private const val TAG = "PackageManagerHook"

    // Sensitive packages that must never be visible to target apps
    private val BLACKLISTED_PACKAGES = setOf(
        "com.sandbox.ball",
        "com.topjohnwu.magisk",
        "io.github.vvb2060.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "org.lsposed.manager",
        "org.lsposed.lspatch",
        "com.chelpus.luckypatcher",
        "catch_.me_.if_.you_.can_",
        "com.cih.game_cih",
        "com.charles.android",
        "com.guoshi.httpcanary"
    )

    private var targetPackageName: String? = null
    private var isVisibilityHidingEnabled: Boolean = true

    fun setTargetPackage(packageName: String) {
        targetPackageName = packageName
        Log.i(TAG, "PackageManagerHook configured for target package: $packageName")
    }

    fun setVisibilityHidingEnabled(enabled: Boolean) {
        isVisibilityHidingEnabled = enabled
        Log.i(TAG, "App visibility hiding set to: $enabled")
    }

    fun filterPackageInfoList(originalList: List<PackageInfo>?): List<PackageInfo> {
        if (!isVisibilityHidingEnabled || originalList == null) {
            return originalList ?: emptyList()
        }

        return originalList.filter { pkg ->
            val name = pkg.packageName
            val isTarget = name == targetPackageName
            val appInfo = pkg.applicationInfo
            val isSystem = appInfo != null && ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            val isBlacklisted = BLACKLISTED_PACKAGES.contains(name) || name.contains("sandbox")

            // Cloned app only sees itself and standard system packages, never sandbox host or root tools
            (isTarget || isSystem) && !isBlacklisted
        }
    }

    fun filterApplicationInfoList(originalList: List<ApplicationInfo>?): List<ApplicationInfo> {
        if (!isVisibilityHidingEnabled || originalList == null) {
            return originalList ?: emptyList()
        }

        return originalList.filter { app ->
            val name = app.packageName
            val isTarget = name == targetPackageName
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isBlacklisted = BLACKLISTED_PACKAGES.contains(name) || name.contains("sandbox")

            (isTarget || isSystem) && !isBlacklisted
        }
    }

    fun filterResolveInfoList(originalList: List<ResolveInfo>?): List<ResolveInfo> {
        if (!isVisibilityHidingEnabled || originalList == null) {
            return originalList ?: emptyList()
        }

        return originalList.filter { resolve ->
            val pkgName = resolve.activityInfo?.packageName ?: ""
            !BLACKLISTED_PACKAGES.contains(pkgName) && !pkgName.contains("sandbox")
        }
    }

    fun isPackageAllowed(packageName: String): Boolean {
        if (!isVisibilityHidingEnabled) return true
        if (packageName == targetPackageName) return true
        if (BLACKLISTED_PACKAGES.contains(packageName) || packageName.contains("sandbox")) {
            return false
        }
        return true
    }

    const val PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * Anti-Shadowban feature:
     * Spoofs the installer source to Google Play Store (com.android.vending).
     * Prevents games from flagging the installation as untrusted sideload / sandbox.
     */
    fun getInstallerPackageName(packageName: String): String {
        return PLAY_STORE_PACKAGE
    }

    fun getOriginatingPackageName(): String {
        return PLAY_STORE_PACKAGE
    }
}
