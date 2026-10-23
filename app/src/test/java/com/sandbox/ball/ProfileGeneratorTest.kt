package com.sandbox.ball

import com.sandbox.ball.core.spoofer.ProfileGenerator
import org.junit.Assert.*
import org.junit.Test

class ProfileGeneratorTest {

    @Test
    fun testGenerateLuhnImei() {
        val profile = ProfileGenerator.generateRandomProfile()
        val imei = profile.imei

        assertEquals("IMEI must be 15 digits long", 15, imei.length)
        assertTrue("IMEI must contain only digits", imei.all { it.isDigit() })

        // Verify Luhn checksum
        var sum = 0
        for (i in imei.indices) {
            var digit = imei[i] - '0'
            if (i % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        assertEquals("IMEI Luhn checksum modulo 10 must be 0", 0, sum % 10)
    }

    @Test
    fun testGenerateAndroidId() {
        val profile = ProfileGenerator.generateRandomProfile()
        val androidId = profile.androidId

        assertEquals("Android ID must be 16 characters", 16, androidId.length)
        assertTrue("Android ID must be valid hex", androidId.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun testGenerateMacAddress() {
        val profile = ProfileGenerator.generateRandomProfile()
        val mac = profile.macAddress

        assertTrue("MAC address must match standard 6-byte hex format",
            mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")))
    }

    @Test
    fun testDevicePresets() {
        val samsung = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.SAMSUNG_S24_ULTRA)
        assertEquals("samsung", samsung.brand)
        assertEquals("SM-S928B", samsung.model)

        val pixel = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.GOOGLE_PIXEL_8_PRO)
        assertEquals("google", pixel.brand)
        assertEquals("Pixel 8 Pro", pixel.model)

        val rog = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.ASUS_ROG_8)
        assertEquals("asus", rog.brand)
        assertEquals("ASUS_AI2401", rog.model)

        val natural = ProfileGenerator.generatePreset(ProfileGenerator.DevicePreset.NATURAL_DEVICE)
        assertTrue("Natural profile must have isNaturalMode = true", natural.isNaturalMode)
        assertNotNull("Natural profile must have an OAID", natural.oaid)
        assertEquals(15, natural.imei.length)
    }
}
