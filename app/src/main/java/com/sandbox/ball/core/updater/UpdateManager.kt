package com.sandbox.ball.core.updater

import android.content.Context
import android.content.Intent
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

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_REPO_API = "https://api.github.com/repos/Dayat222/sanbox_ball/releases/latest"
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_REPO_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "StealthSandbox-App")
            }

            if (connection.responseCode != 200) {
                Log.w(TAG, "GitHub API returned response code: ${connection.responseCode}")
                return@withContext null
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JsonParser.parseString(responseText).asJsonObject

            val tagName = json.get("tag_name")?.asString ?: ""
            val releaseNotes = json.get("body")?.asString ?: "Perbaikan bug dan peningkatan sistem stealth."
            val assets = json.getAsJsonArray("assets")

            var downloadUrl = ""
            var apkName = "app-debug.apk"
            var fileSize = 0L

            if (assets != null && assets.size() > 0) {
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

            val currentVersion = BuildConfig.VERSION_NAME
            val cleanTag = tagName.removePrefix("v").trim()
            val cleanCurrent = currentVersion.removePrefix("v").trim()

            // Update is available if version string differs and download URL is available
            val hasUpdate = downloadUrl.isNotEmpty() && isNewerVersion(cleanTag, cleanCurrent)

            AppUpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = tagName,
                currentVersion = currentVersion,
                downloadUrl = downloadUrl,
                apkFileName = apkName,
                releaseNotes = releaseNotes,
                fileSizeBytes = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}")
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

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "stealth_update.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "StealthSandbox-App")
            }

            // Handle redirect if needed
            val responseCode = connection.responseCode
            val finalConnection = if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                val newUrl = connection.getHeaderField("Location")
                (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "StealthSandbox-App")
                }
            } else {
                connection
            }

            val totalSize = finalConnection.contentLength
            var downloaded = 0L

            finalConnection.inputStream.use { input ->
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

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
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
