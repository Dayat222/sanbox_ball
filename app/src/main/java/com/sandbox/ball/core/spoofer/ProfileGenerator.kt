package com.sandbox.ball.core.spoofer

import java.security.SecureRandom
import java.util.UUID

object ProfileGenerator {
    private val random = SecureRandom()

    enum class DevicePreset {
        NATURAL_DEVICE,
        SAMSUNG_S24_ULTRA,
        GOOGLE_PIXEL_8_PRO,
        XIAOMI_14,
        ASUS_ROG_8,
        ONEPLUS_12
    }

    /**
     * Anti-Shadowban Engine: Natural Profile Generator
     * Preserves physical SoC, GPU, Board, and Model consistency while rotating
     * uniquely banned tracking identifiers (IMEI, Android ID, OAID, MAC, Serial).
     * Prevents game anti-cheat from detecting OpenGL / CPU discrepancies.
     */
    fun generateNaturalProfile(): HardwareProfile {
        val uniqueSuffix = (1000..9999).random()
        val brand = android.os.Build.BRAND?.takeIf { it.isNotEmpty() } ?: "android"
        val model = android.os.Build.MODEL?.takeIf { it.isNotEmpty() } ?: "Generic_Device"
        val manufacturer = android.os.Build.MANUFACTURER?.takeIf { it.isNotEmpty() } ?: "Android"
        val product = android.os.Build.PRODUCT?.takeIf { it.isNotEmpty() } ?: "generic"
        val device = android.os.Build.DEVICE?.takeIf { it.isNotEmpty() } ?: "generic"
        val board = android.os.Build.BOARD?.takeIf { it.isNotEmpty() } ?: "generic"
        val hardware = android.os.Build.HARDWARE?.takeIf { it.isNotEmpty() } ?: "qcom"
        val fingerprint = android.os.Build.FINGERPRINT?.takeIf { it.isNotEmpty() }
            ?: "$brand/$product/$device:14/UP1A.231005.007/$uniqueSuffix:user/release-keys"
        val buildId = android.os.Build.ID?.takeIf { it.isNotEmpty() } ?: "UP1A.231005.007"
        val androidVersion = android.os.Build.VERSION.RELEASE?.takeIf { it.isNotEmpty() } ?: "14"
        val sdkInt = android.os.Build.VERSION.SDK_INT.takeIf { it > 0 } ?: 34

        return HardwareProfile(
            profileId = UUID.randomUUID().toString(),
            profileName = "Natural Mode ($model)",
            brand = brand,
            model = model,
            manufacturer = manufacturer,
            product = product,
            device = device,
            board = board,
            hardware = hardware,
            fingerprint = fingerprint,
            buildId = buildId,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            imei = generateLuhnImei("35"),
            imei2 = generateLuhnImei("35"),
            meid = generateRandomNumeric(14),
            imsi = "51010" + generateRandomNumeric(10),
            simSerialNumber = "896210" + generateRandomNumeric(14),
            androidId = generateRandomHex(16),
            oaid = UUID.randomUUID().toString(),
            advertisingId = UUID.randomUUID().toString(),
            macAddress = generateRandomMac(),
            bluetoothAddress = generateRandomMac(),
            serialNumber = "NAT" + generateRandomAlphanumeric(8).uppercase(),
            phoneNumber = "+6281" + (10000000..99999999).random(),
            networkOperator = "51010",
            networkOperatorName = "Telkomsel",
            simOperator = "51010",
            simOperatorName = "Telkomsel",
            wifiSsid = listOf("Home_5G_Fast", "Private_WiFi", "Office_WiFi_5G").random(),
            wifiBssid = generateRandomMac(),
            fakeIpAddress = "192.168.1." + (20..240).random(),
            isNaturalMode = true,
            createdAt = System.currentTimeMillis()
        )
    }

    fun generatePreset(preset: DevicePreset): HardwareProfile {
        val uniqueSuffix = (1000..9999).random()
        return when (preset) {
            DevicePreset.NATURAL_DEVICE -> generateNaturalProfile()
            DevicePreset.SAMSUNG_S24_ULTRA -> HardwareProfile(
                profileId = UUID.randomUUID().toString(),
                profileName = "Samsung Galaxy S24 Ultra",
                brand = "samsung",
                model = "SM-S928B",
                manufacturer = "samsung",
                product = "e3qxxx",
                device = "e3q",
                board = "s5e9945",
                hardware = "qcom",
                fingerprint = "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXB$uniqueSuffix:user/release-keys",
                buildId = "UP1A.231005.007",
                androidVersion = "14",
                sdkInt = 34,
                imei = generateLuhnImei("35"),
                imei2 = generateLuhnImei("35"),
                meid = generateRandomNumeric(14),
                imsi = "51010" + generateRandomNumeric(10),
                simSerialNumber = "896210" + generateRandomNumeric(14),
                androidId = generateRandomHex(16),
                macAddress = generateRandomMac(),
                bluetoothAddress = generateRandomMac(),
                serialNumber = "RFC" + generateRandomAlphanumeric(8).uppercase(),
                phoneNumber = "+6281" + (10000000..99999999).random(),
                networkOperator = "51010",
                networkOperatorName = "Telkomsel",
                simOperator = "51010",
                simOperatorName = "Telkomsel",
                wifiSsid = "SpeedyMesh_5G",
                wifiBssid = generateRandomMac(),
                fakeIpAddress = "192.168.1." + (20..240).random()
            )

            DevicePreset.GOOGLE_PIXEL_8_PRO -> HardwareProfile(
                profileId = UUID.randomUUID().toString(),
                profileName = "Google Pixel 8 Pro",
                brand = "google",
                model = "Pixel 8 Pro",
                manufacturer = "Google",
                product = "husky",
                device = "husky",
                board = "husky",
                hardware = "zuma",
                fingerprint = "google/husky/husky:14/UD1A.230803.041/$uniqueSuffix:user/release-keys",
                buildId = "UD1A.230803.041",
                androidVersion = "14",
                sdkInt = 34,
                imei = generateLuhnImei("35"),
                imei2 = generateLuhnImei("35"),
                meid = generateRandomNumeric(14),
                imsi = "51011" + generateRandomNumeric(10),
                simSerialNumber = "896211" + generateRandomNumeric(14),
                androidId = generateRandomHex(16),
                macAddress = generateRandomMac(),
                bluetoothAddress = generateRandomMac(),
                serialNumber = "3A" + generateRandomAlphanumeric(8).uppercase(),
                phoneNumber = "+6285" + (10000000..99999999).random(),
                networkOperator = "51011",
                networkOperatorName = "Indosat Ooredoo",
                simOperator = "51011",
                simOperatorName = "Indosat Ooredoo",
                wifiSsid = "GoogleFiber_Pro",
                wifiBssid = generateRandomMac(),
                fakeIpAddress = "192.168.0." + (20..240).random()
            )

            DevicePreset.XIAOMI_14 -> HardwareProfile(
                profileId = UUID.randomUUID().toString(),
                profileName = "Xiaomi 14",
                brand = "Xiaomi",
                model = "23127PN0CG",
                manufacturer = "Xiaomi",
                product = "houji",
                device = "houji",
                board = "houji",
                hardware = "qcom",
                fingerprint = "Xiaomi/houji/houji:14/UKQ1.230804.001/V816.0.4.0.UNCMIXM:user/release-keys",
                buildId = "UKQ1.230804.001",
                androidVersion = "14",
                sdkInt = 34,
                imei = generateLuhnImei("86"),
                imei2 = generateLuhnImei("86"),
                meid = generateRandomNumeric(14),
                imsi = "51089" + generateRandomNumeric(10),
                simSerialNumber = "896289" + generateRandomNumeric(14),
                androidId = generateRandomHex(16),
                macAddress = generateRandomMac(),
                bluetoothAddress = generateRandomMac(),
                serialNumber = "XM" + generateRandomAlphanumeric(8).uppercase(),
                phoneNumber = "+6289" + (10000000..99999999).random(),
                networkOperator = "51089",
                networkOperatorName = "Tri (3)",
                simOperator = "51089",
                simOperatorName = "Tri (3)",
                wifiSsid = "Xiaomi_Mesh_WiFi",
                wifiBssid = generateRandomMac(),
                fakeIpAddress = "192.168.31." + (20..240).random()
            )

            DevicePreset.ASUS_ROG_8 -> HardwareProfile(
                profileId = UUID.randomUUID().toString(),
                profileName = "ASUS ROG Phone 8",
                brand = "asus",
                model = "ASUS_AI2401",
                manufacturer = "asus",
                product = "WW_AI2401",
                device = "AI2401",
                board = "kalama",
                hardware = "qcom",
                fingerprint = "asus/WW_AI2401/AI2401:14/U1B1.231201.001/34.1420.1420.326:user/release-keys",
                buildId = "U1B1.231201.001",
                androidVersion = "14",
                sdkInt = 34,
                imei = generateLuhnImei("35"),
                imei2 = generateLuhnImei("35"),
                meid = generateRandomNumeric(14),
                imsi = "51028" + generateRandomNumeric(10),
                simSerialNumber = "896228" + generateRandomNumeric(14),
                androidId = generateRandomHex(16),
                macAddress = generateRandomMac(),
                bluetoothAddress = generateRandomMac(),
                serialNumber = "ROG" + generateRandomAlphanumeric(8).uppercase(),
                phoneNumber = "+6288" + (10000000..99999999).random(),
                networkOperator = "51028",
                networkOperatorName = "Smartfren",
                simOperator = "51028",
                simOperatorName = "Smartfren",
                wifiSsid = "ROG_Gaming_5G",
                wifiBssid = generateRandomMac(),
                fakeIpAddress = "192.168.50." + (20..240).random()
            )

            DevicePreset.ONEPLUS_12 -> HardwareProfile(
                profileId = UUID.randomUUID().toString(),
                profileName = "OnePlus 12",
                brand = "OnePlus",
                model = "CPH2581",
                manufacturer = "OnePlus",
                product = "CPH2581",
                device = "OP594DL1",
                board = "pineapple",
                hardware = "qcom",
                fingerprint = "OnePlus/CPH2581/OP594DL1:14/UKQ1.230924.001/U.14b8a21-7fa1:user/release-keys",
                buildId = "UKQ1.230924.001",
                androidVersion = "14",
                sdkInt = 34,
                imei = generateLuhnImei("86"),
                imei2 = generateLuhnImei("86"),
                meid = generateRandomNumeric(14),
                imsi = "51010" + generateRandomNumeric(10),
                simSerialNumber = "896210" + generateRandomNumeric(14),
                androidId = generateRandomHex(16),
                macAddress = generateRandomMac(),
                bluetoothAddress = generateRandomMac(),
                serialNumber = "1P" + generateRandomAlphanumeric(8).uppercase(),
                phoneNumber = "+6287" + (10000000..99999999).random(),
                networkOperator = "51010",
                networkOperatorName = "Telkomsel",
                simOperator = "51010",
                simOperatorName = "Telkomsel",
                wifiSsid = "NeverSettle_WiFi",
                wifiBssid = generateRandomMac(),
                fakeIpAddress = "192.168.1." + (20..240).random()
            )
        }
    }

    fun generateRandomProfile(baseName: String = "Dynamic Stealth Identity"): HardwareProfile {
        val presets = DevicePreset.values()
        val pickedPreset = presets.random()
        val template = generatePreset(pickedPreset)
        return template.copy(
            profileId = UUID.randomUUID().toString(),
            profileName = "$baseName (${template.model})",
            imei = generateLuhnImei("35"),
            imei2 = generateLuhnImei("35"),
            androidId = generateRandomHex(16),
            oaid = UUID.randomUUID().toString(),
            advertisingId = UUID.randomUUID().toString(),
            macAddress = generateRandomMac(),
            bluetoothAddress = generateRandomMac(),
            serialNumber = "SN" + generateRandomAlphanumeric(10).uppercase(),
            wifiSsid = listOf("Home_Network_5G", "Linksys_Fast", "Office_WiFi_Secure", "Stealth_AP_5G").random(),
            wifiBssid = generateRandomMac(),
            fakeIpAddress = "192.168." + (1..10).random() + "." + (20..240).random(),
            createdAt = System.currentTimeMillis()
        )
    }

    private fun generateLuhnImei(prefix: String): String {
        val builder = StringBuilder(prefix)
        while (builder.length < 14) {
            builder.append(random.nextInt(10))
        }
        val checkDigit = calculateLuhnCheckDigit(builder.toString())
        builder.append(checkDigit)
        return builder.toString()
    }

    private fun calculateLuhnCheckDigit(numberStr: String): Int {
        var sum = 0
        for (i in numberStr.indices) {
            var digit = numberStr[i] - '0'
            if (i % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return (10 - (sum % 10)) % 10
    }

    fun generateRandomHex(length: Int): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    fun generateRandomNumeric(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(random.nextInt(10))
        }
        return sb.toString()
    }

    fun generateRandomAlphanumeric(length: Int): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    fun generateRandomMac(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        // Set locally administered unicast bit
        bytes[0] = (bytes[0].toInt() and 0xFE or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it) }
    }
}
