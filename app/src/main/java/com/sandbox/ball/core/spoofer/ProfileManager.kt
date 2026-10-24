package com.sandbox.ball.core.spoofer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sandbox.ball.core.hooks.HookManager
import com.sandbox.ball.core.nativebridge.NativeBridge
import java.io.File

class ProfileManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stealth_hardware_profiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACTIVE_PROFILE = "key_active_hardware_profile"
        private const val KEY_PROFILE_LIST = "key_profile_list"
        private const val KEY_RESET_COUNT = "key_reset_count"

        @Volatile
        private var instance: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return instance ?: synchronized(this) {
                instance ?: ProfileManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        if (getActiveProfile() == null) {
            val defaultProfile = ProfileGenerator.generateNaturalProfile()
            setActiveProfile(defaultProfile)
            saveProfile(defaultProfile)
        }
    }

    fun getActiveProfile(): HardwareProfile? {
        val json = prefs.getString(KEY_ACTIVE_PROFILE, null) ?: return null
        return try {
            gson.fromJson(json, HardwareProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun setActiveProfile(profile: HardwareProfile) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, gson.toJson(profile)).apply()
        // If running in container process, update active hooks immediately
        try {
            val isContainer = com.sandbox.ball.StealthApplication.instance.getProcessNameCompat().endsWith(":container")
            if (isContainer) {
                HookManager.getInstance(context).applyProfile(profile)
                if (NativeBridge.isNativeLoaded()) {
                    val virtualRootDir = File(context.filesDir, "sandbox_vfs").absolutePath
                    NativeBridge.nativeInitEngine(virtualRootDir, "", profile.macAddress, profile.serialNumber)
                }
            }
        } catch (_: Exception) {}
    }

    fun getSavedProfiles(): List<HardwareProfile> {
        val json = prefs.getString(KEY_PROFILE_LIST, null) ?: return emptyList()
        val type = object : TypeToken<List<HardwareProfile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfile(profile: HardwareProfile) {
        val list = getSavedProfiles().toMutableList()
        val index = list.indexOfFirst { it.profileId == profile.profileId }
        if (index >= 0) {
            list[index] = profile
        } else {
            list.add(0, profile)
        }
        prefs.edit().putString(KEY_PROFILE_LIST, gson.toJson(list)).apply()
    }

    /**
     * PRD Core Feature: 1-Click Reset
     * Rotates Hardware ID to a completely fresh profile, wipes sandbox cache,
     * and re-arms stealth evasion engines.
     */
    fun rotateIdentity(clearSandboxCache: Boolean = true): HardwareProfile {
        val current = getActiveProfile()
        val newProfile = if (current?.isNaturalMode == true) {
            ProfileGenerator.generateNaturalProfile()
        } else {
            ProfileGenerator.generateRandomProfile("Rotated Identity")
        }
        setActiveProfile(newProfile)
        saveProfile(newProfile)

        if (clearSandboxCache) {
            clearVirtualCache()
        }

        val resetCount = prefs.getInt(KEY_RESET_COUNT, 0) + 1
        prefs.edit().putInt(KEY_RESET_COUNT, resetCount).apply()

        return newProfile
    }

    fun getResetCount(): Int = prefs.getInt(KEY_RESET_COUNT, 0)

    private fun clearVirtualCache() {
        try {
            val vfsDir = File(context.filesDir, "sandbox_vfs")
            val cacheDir = File(vfsDir, "cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }
        } catch (_: Exception) {}
    }
}
