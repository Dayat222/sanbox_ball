package com.sandbox.ball.core.updater

import java.io.Serializable

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val apkFileName: String,
    val releaseNotes: String = "",
    val fileSizeBytes: Long = 0L,
    val sha256Checksum: String = ""
) : Serializable
