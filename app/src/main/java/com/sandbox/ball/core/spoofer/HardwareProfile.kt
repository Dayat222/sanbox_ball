package com.sandbox.ball.core.spoofer

import java.io.Serializable
import java.util.UUID

data class HardwareProfile(
    val profileId: String = "",
    val profileName: String = "Default Stealth Profile",
    val brand: String = "samsung",
    val model: String = "SM-S928B",
    val manufacturer: String = "samsung",
    val product: String = "e3qxxx",
    val device: String = "e3q",
    val board: String = "s5e9945",
    val hardware: String = "qcom",
    val fingerprint: String = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXB5:user/release-keys",
    val buildId: String = "UP1A.231005.007",
    val androidVersion: String = "14",
    val sdkInt: Int = 34,
    val imei: String = "358941209847192",
    val imei2: String = "358941209847193",
    val meid: String = "99000086247185",
    val imsi: String = "510101234567890",
    val simSerialNumber: String = "89621012345678901234",
    val androidId: String = "4b2e8a109c5d7f3e",
    val oaid: String = "4b2e8a10-9c5d-7f3e-8a10-4b2e8a109c5d",
    val advertisingId: String = "4b2e8a10-9c5d-7f3e-8a10-4b2e8a109c5d",
    val macAddress: String = "02:A1:B2:C3:D4:E5",
    val bluetoothAddress: String = "02:A1:B2:C3:D4:E6",
    val serialNumber: String = "RFCT30XYZ01",
    val phoneNumber: String = "+6281298765432",
    val networkOperator: String = "51010",
    val networkOperatorName: String = "Telkomsel",
    val simOperator: String = "51010",
    val simOperatorName: String = "Telkomsel",
    val wifiSsid: String = "Fiber_Fast_5G",
    val wifiBssid: String = "A4:2B:B0:12:34:56",
    val fakeIpAddress: String = "192.168.1.188",
    val isNaturalMode: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable
