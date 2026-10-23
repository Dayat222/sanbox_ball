package com.sandbox.ball.core.hooks

import android.util.Log
import com.sandbox.ball.core.spoofer.HardwareProfile

object TelephonyHook {
    private const val TAG = "TelephonyHook"
    private var activeProfile: HardwareProfile? = null

    fun setProfile(profile: HardwareProfile) {
        activeProfile = profile
        Log.i(TAG, "TelephonyHook armed with IMEI: ${profile.imei}, IMSI: ${profile.imsi}")
    }

    fun getDeviceId(slotIndex: Int = 0): String {
        val profile = activeProfile ?: return "358941209847192"
        return if (slotIndex == 1) profile.imei2 else profile.imei
    }

    fun getImei(slotIndex: Int = 0): String {
        val profile = activeProfile ?: return "358941209847192"
        return if (slotIndex == 1) profile.imei2 else profile.imei
    }

    fun getMeid(slotIndex: Int = 0): String {
        return activeProfile?.meid ?: "99000086247185"
    }

    fun getSubscriberId(): String {
        return activeProfile?.imsi ?: "510101234567890"
    }

    fun getSimSerialNumber(): String {
        return activeProfile?.simSerialNumber ?: "89621012345678901234"
    }

    fun getLine1Number(): String {
        return activeProfile?.phoneNumber ?: "+6281298765432"
    }

    fun getNetworkOperator(): String {
        return activeProfile?.networkOperator ?: "51010"
    }

    fun getNetworkOperatorName(): String {
        return activeProfile?.networkOperatorName ?: "Telkomsel"
    }

    fun getSimOperator(): String {
        return activeProfile?.simOperator ?: "51010"
    }

    fun getSimOperatorName(): String {
        return activeProfile?.simOperatorName ?: "Telkomsel"
    }

    fun getSimCountryIso(): String {
        return "id"
    }
}
