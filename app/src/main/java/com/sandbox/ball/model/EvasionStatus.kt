package com.sandbox.ball.model

import java.io.Serializable

data class EvasionStatus(
    val isVfsActive: Boolean = true,
    val isAntiDebugActive: Boolean = true,
    val isMapsFilterActive: Boolean = true,
    val isHardwareSpooferActive: Boolean = true,
    val isAppVisibilityHidingActive: Boolean = true,
    val isNativeHookingEngaged: Boolean = true,
    val activeModel: String = "",
    val activeImei: String = "",
    val activeAndroidId: String = ""
) : Serializable {
    fun getActiveShieldCount(): Int {
        var count = 0
        if (isVfsActive) count++
        if (isAntiDebugActive) count++
        if (isMapsFilterActive) count++
        if (isHardwareSpooferActive) count++
        if (isAppVisibilityHidingActive) count++
        return count
    }

    fun getTotalShieldCount(): Int = 5
}
