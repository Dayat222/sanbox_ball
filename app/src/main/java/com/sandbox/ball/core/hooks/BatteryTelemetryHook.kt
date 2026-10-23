package com.sandbox.ball.core.hooks

import android.os.BatteryManager
import java.security.SecureRandom

object BatteryTelemetryHook {
    private val random = SecureRandom()

    // Realistic battery telemetry simulation to defeat anti-emulator heuristic checks
    fun getRealisticBatteryLevel(): Int {
        return 65 + random.nextInt(25) // 65% - 90%
    }

    fun getRealisticTemperature(): Int {
        // Temperature in tenths of a degree Celsius (320 = 32.0 C)
        return 310 + random.nextInt(45) // 31.0°C - 35.5°C
    }

    fun getRealisticVoltage(): Int {
        return 3900 + random.nextInt(250) // 3900 mV - 4150 mV
    }

    fun getRealisticHealth(): Int {
        return BatteryManager.BATTERY_HEALTH_GOOD
    }

    fun getRealisticTechnology(): String {
        return "Li-poly"
    }

    fun getRealisticStatus(): Int {
        return BatteryManager.BATTERY_STATUS_DISCHARGING
    }
}
