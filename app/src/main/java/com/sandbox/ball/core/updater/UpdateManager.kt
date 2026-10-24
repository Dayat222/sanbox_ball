package com.sandbox.ball.core.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.sandbox.ball.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_REPO_RELEASES_API = "https://api.github.com/repos/Dayat222/sanbox_ball/releases"
    private const val GITHUB_REPO_LATEST_API = "https://api.github.com/repos/Dayat222/sanbox_ball/releases/latest"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Verifies that the update URL uses HTTPS and targets official GitHub domains (SEC-04).
     */
    fun isTrustedUpdateUrl(url: String): Boolean {
        return try {
            val parsed = URL(url)
            val scheme = parsed.protocol
            val host = parsed.host?.lowercase() ?: ""
            scheme.equals("https", ignoreCase = true) && (
                host == "github.com" ||
                host == "objects.githubusercontent.com" ||
                host.endsWith(".github.com") ||
                host.endsWith(".githubusercontent.com")
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculates the SHA-256 checksum of a file.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compares the computed SHA-256 against an expected checksum in constant time.
     */
    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        if (expectedSha256.isBlank()) return true
        val actualSha256 = calculateSha256(file)
        val expectedBytes = expectedSha256.trim().lowercase().toByteArray(Charsets.UTF_8)
        val actualBytes = actualSha256.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    /**
     * Verifies the integrity of a downloaded APK:
     * 1. Checksum matching (constant-time MessageDigest comparison)
     * 2. Package name validation
     * 3. Signing certificate verification against currently installed host
     */
    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedSha256: String = ""
    ): Boolean {
        if (!apkFile.exists()) return false

        // 1. Checksum verification
        if (expectedSha256.isNotBlank() && !verifySha256(apkFile, expectedSha256)) {
            Log.e(TAG, "SHA-256 mismatch for APK: ${apkFile.name}")
            apkFile.delete()
            return false
        }

        // 2. Package name and certificate verification
        try {
            val pm = context.packageManager
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }

            if (archiveInfo == null) {
                Log.e(TAG, "Could not parse APK archive info: ${apkFile.name}")
                apkFile.delete()
                return false
            }

            if (archiveInfo.packageName != context.packageName) {
                Log.e(TAG, "Package name mismatch! Expected: ${context.packageName}, Found: ${archiveInfo.packageName}")
                apkFile.delete()
                return false
            }

            val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signaturesMatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val curSign = installedInfo.signingInfo
                val archSign = archiveInfo.signingInfo
                if (curSign != null && archSign != null) {
                    val curSigs = if (curSign.hasMultipleSigners()) curSign.apkContentsSigners else curSign.signingCertificateHistory
                    val archSigs = if (archSign.hasMultipleSigners()) archSign.apkContentsSigners else archSign.signingCertificateHistory
                    curSigs != null && archSigs != null && curSigs.any { cur ->
                        archSigs.any { arch -> MessageDigest.isEqual(cur.toByteArray(), arch.toByteArray()) }
                    }
                } else false
            } else {
                @Suppress("DEPRECATION")
                val curSigs = installedInfo.signatures
                @Suppress("DEPRECATION")
                val archSigs = archiveInfo.signatures
                if (curSigs != null && archSigs != null) {
                    curSigs.any { cur ->
                        archSigs.any { arch -> MessageDigest.isEqual(cur.toByteArray(), arch.toByteArray()) }
                    }
                } else false
            }

            if (!signaturesMatch) {
                Log.e(TAG, "Signature mismatch between installed app and downloaded APK!")
                apkFile.delete()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed verifying downloaded APK signatures: ${e.message}", e)
            apkFile.delete()
            return false
        }

        return true
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = fetchReleaseJson(GITHUB_REPO_RELEASES_API) 
                ?: fetchReleaseJson(GITHUB_REPO_LATEST_API)
                ?: return@withContext null

            val tagName = json.get("tag_name")?.asString ?: ""
            val releaseNotes = json.get("body")?.asString ?: "Perbaikan bug dan peningkatan sistem stealth."
            val assets = json.getAsJsonArray("assets")

            var downloadUrl = ""
            var apkName = "app-release.apk"
            var fileSize = 0L

            if (assets != null && assets.size() > 0) {
                // First pass: prioritize production release APK
                for (i in 0 until assets.size()) {
                    val asset = assets[i].asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.contains("release", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.get("browser_download_url")?.asString ?: ""
                        apkName = name
                        fileSize = asset.get("size")?.asLong ?: 0L
                        break
                    }
                }
                // Second pass fallback: any available APK
                if (downloadUrl.isEmpty()) {
                    for (i in 0 until assets.size()) {
                        val asset = assets[i].asJsonObject
                        val name = asset.get("name")?.asString ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.get("browser_download_url")?.asString ?: ""
                            apkName = name
                            fileSize = asset.get("size")?.asLong ?: 0L
                            break
                        }
                    }
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val cleanTag = tagName.removePrefix("v").trim()
            val cleanCurrent = currentVersion.removePrefix("v").trim()

            // Extract optional SHA-256 hash from release notes (format: SHA256: <hash>)
            val shaRegex = Regex("""(?i)SHA-?256:\s*([a-fA-F0-9]{64})""")
            val shaMatch = shaRegex.find(releaseNotes)
            val sha256 = shaMatch?.groupValues?.get(1)?.lowercase() ?: ""

            val hasUpdate = downloadUrl.isNotEmpty() && isNewerVersion(cleanTag, cleanCurrent)

            AppUpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = tagName,
                currentVersion = currentVersion,
                downloadUrl = downloadUrl,
                apkFileName = apkName,
                releaseNotes = releaseNotes,
                fileSizeBytes = fileSize,
                sha256Checksum = sha256
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}")
            null
        }
    }

    private fun fetchReleaseJson(apiUrl: String): com.google.gson.JsonObject? {
        return try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "StealthSandbox-App")
            }

            if (connection.responseCode != 200) {
                Log.w(TAG, "GitHub API $apiUrl returned code: ${connection.responseCode}")
                return null
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = JsonParser.parseString(responseText)
            if (parsed.isJsonArray) {
                val array = parsed.asJsonArray
                if (array.size() == 0) return null
                var chosen: com.google.gson.JsonObject? = null
                for (i in 0 until array.size()) {
                    val item = array.get(i).asJsonObject
                    val assets = item.getAsJsonArray("assets")
                    if (assets != null) {
                        for (j in 0 until assets.size()) {
                            val assetName = assets.get(j).asJsonObject.get("name")?.asString ?: ""
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                chosen = item
                                break
                            }
                        }
                    }
                    if (chosen != null) break
                }
                chosen ?: array.get(0).asJsonObject
            } else if (parsed.isJsonObject) {
                parsed.asJsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching from $apiUrl: ${e.message}")
            null
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote == current) return false
        val remoteParts = remote.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return remote != current
    }

    /**
     * Downloads APK into secure internal cache directory (SEC-04).
     * Validates HTTPS scheme and prevents insecure HTTP downgrade redirects.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (!isTrustedUpdateUrl(downloadUrl)) {
                throw SecurityException("Insecure or untrusted download URL: $downloadUrl")
            }

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "stealth_update.apk")
            if (apkFile.exists()) apkFile.delete()

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            val maxRedirects = 5

            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "StealthSandbox-App")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307 || responseCode == 308) {

                    val newUrl = connection.getHeaderField("Location")
                        ?: throw SecurityException("Missing redirect Location header")

                    val resolvedNewUrl = URL(url, newUrl).toString()
                    if (!isTrustedUpdateUrl(resolvedNewUrl)) {
                        throw SecurityException("Insecure or untrusted redirect URL: $resolvedNewUrl")
                    }

                    connection.disconnect()
                    currentUrl = resolvedNewUrl
                    redirectCount++
                    if (redirectCount > maxRedirects) {
                        throw SecurityException("Too many redirects while downloading update")
                    }
                    continue
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    Log.w(TAG, "Download failed with HTTP status code: $responseCode")
                    return@withContext null
                }
            }

            val totalSize = connection.contentLength
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            val percent = ((downloaded * 100) / totalSize).toInt()
                            mainHandler.post { onProgress(percent) }
                        }
                    }
                    output.flush()
                }
            }

            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            null
        }
    }

    fun installApk(context: Context, apkFile: File, expectedSha256: String = "") {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            return
        }

        if (!verifyDownloadedApk(context, apkFile, expectedSha256)) {
            Log.e(TAG, "APK verification failed; installation aborted.")
            return
        }

        // On Android 8.0+ (API 26+), check unknown apps install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }
}
