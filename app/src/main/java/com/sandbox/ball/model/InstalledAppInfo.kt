package com.sandbox.ball.model

import java.io.Serializable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceDir: String,
    val isSystemApp: Boolean = false,
    var isAlreadyCloned: Boolean = false
) : Serializable
