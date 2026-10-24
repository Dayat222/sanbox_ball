package com.sandbox.ball

import com.sandbox.ball.core.container.NativeLibExtractor
import com.sandbox.ball.core.updater.UpdateManager
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SecurityHardeningTest {

    @Test
    fun testZipSlipDirectoryTraversalProtection() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "sandbox_test_libs_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Test 1: Malicious entry attempting to escape using ..
            val maliciousEntry = "lib/arm64-v8a/../../shared_prefs/hacked.xml"
            try {
                NativeLibExtractor.validateEntry(maliciousEntry, "lib/arm64-v8a/", tempDir)
                fail("Expected SecurityException for Zip Slip entry: $maliciousEntry")
            } catch (e: SecurityException) {
                assertTrue("Exception message should mention Zip Slip", e.message?.contains("Zip Slip") == true)
            }

            // Test 2: Sub-directory separator traversal
            val subDirEntry = "lib/arm64-v8a/subfolder/libtest.so"
            try {
                NativeLibExtractor.validateEntry(subDirEntry, "lib/arm64-v8a/", tempDir)
                fail("Expected SecurityException for nested sub-directory entry")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("Zip Slip") == true)
            }

            // Test 3: Legitimate native library entry
            val validEntry = "lib/arm64-v8a/libtest.so"
            val validOutFile = NativeLibExtractor.validateEntry(validEntry, "lib/arm64-v8a/", tempDir)
            assertEquals("libtest.so", validOutFile.name)
            assertEquals(tempDir.canonicalPath, validOutFile.parentFile?.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUpdateUrlValidation() {
        // HTTP protocol downgrade must be rejected
        assertFalse(
            "HTTP must be rejected",
            UpdateManager.isTrustedUpdateUrl("http://github.com/Dayat222/sanbox_ball/releases/download/v1.4.0/app-release.apk")
        )

        // Untrusted / malicious third-party domain must be rejected
        assertFalse(
            "External domain must be rejected",
            UpdateManager.isTrustedUpdateUrl("https://malicious-server.com/malware.apk")
        )
        assertFalse(
            "Spoofed domain must be rejected",
            UpdateManager.isTrustedUpdateUrl("https://github.com.evil.com/app.apk")
        )

        // Legitimate GitHub release URLs must be accepted
        assertTrue(
            "Official github.com release URL must be accepted",
            UpdateManager.isTrustedUpdateUrl("https://github.com/Dayat222/sanbox_ball/releases/download/v1.4.0/app-release.apk")
        )
        assertTrue(
            "Official GitHub CDN asset URL must be accepted",
            UpdateManager.isTrustedUpdateUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/app-release.apk")
        )
    }

    @Test
    fun testSha256ChecksumVerification() {
        val tempApk = File.createTempFile("test_update_", ".apk")
        try {
            tempApk.writeText("Stealth Sandbox Engine secure integrity content payload")

            val actualSha256 = UpdateManager.calculateSha256(tempApk)
            assertNotNull(actualSha256)
            assertEquals(64, actualSha256.length)

            // Correct checksum should succeed
            assertTrue(
                "Matching SHA-256 must pass verification",
                UpdateManager.verifySha256(tempApk, actualSha256)
            )

            // Case-insensitive checksum comparison
            assertTrue(
                "Uppercase SHA-256 must pass verification",
                UpdateManager.verifySha256(tempApk, actualSha256.uppercase())
            )

            // Tampered / incorrect checksum must be rejected
            val fakeSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            assertFalse(
                "Mismatched SHA-256 must be rejected",
                UpdateManager.verifySha256(tempApk, fakeSha256)
            )
        } finally {
            tempApk.delete()
        }
    }
}
