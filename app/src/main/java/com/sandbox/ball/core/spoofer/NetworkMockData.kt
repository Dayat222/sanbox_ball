package com.sandbox.ball.core.spoofer

data class NetworkMockData(
    val ssid: String = "Fiber_Ultra_5G",
    val bssid: String = "02:A1:B2:C3:D4:E5",
    val ipAddress: String = "192.168.1.188",
    val gateway: String = "192.168.1.1",
    val netmask: String = "255.255.255.0",
    val dns1: String = "8.8.8.8",
    val dns2: String = "1.1.1.1",
    val linkSpeedMbps: Int = 866,
    val frequencyMhz: Int = 5180,
    val networkId: Int = 1
)
