package com.sandbox.ball.model

import java.io.Serializable

data class ClonedAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceApkPath: String,
    val containerDir: String,
    val cloneDate: Long = System.currentTimeMillis(),
    var profileId: String = "",
    var profileName: String = "",
    var isStealthShieldEnabled: Boolean = true
) : Serializable
