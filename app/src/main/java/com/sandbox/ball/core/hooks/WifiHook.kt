package com.sandbox.ball.core.hooks

import android.util.Log
import com.sandbox.ball.core.spoofer.HardwareProfile
import java.net.InetAddress

object WifiHook {
    private const val TAG = "WifiHook"
    private var activeProfile: HardwareProfile? = null

    fun setProfile(profile: HardwareProfile) {
        activeProfile = profile
        Log.i(TAG, "WifiHook armed with MAC: ${profile.macAddress}, SSID: ${profile.wifiSsid}")
    }

    fun getMacAddress(): String {
        return activeProfile?.macAddress ?: "02:00:00:00:00:00"
    }

    fun getSSID(): String {
        val ssid = activeProfile?.wifiSsid ?: "Wi-Fi"
        return "\"$ssid\""
    }

    fun getBSSID(): String {
        return activeProfile?.wifiBssid ?: "02:A1:B2:C3:D4:E5"
    }

    fun getIpAddressInt(): Int {
        val ipStr = activeProfile?.fakeIpAddress ?: "192.168.1.100"
        return try {
            val bytes = InetAddress.getByName(ipStr).address
            (bytes[3].toInt() and 0xFF shl 24) or
            (bytes[2].toInt() and 0xFF shl 16) or
            (bytes[1].toInt() and 0xFF shl 8) or
            (bytes[0].toInt() and 0xFF)
        } catch (_: Exception) {
            0
        }
    }

    fun getHardwareAddressBytes(): ByteArray {
        val mac = activeProfile?.macAddress ?: "02:00:00:00:00:00"
        val parts = mac.split(":")
        val bytes = ByteArray(parts.size)
        for (i in parts.indices) {
            bytes[i] = parts[i].toInt(16).toByte()
        }
        return bytes
    }
}
