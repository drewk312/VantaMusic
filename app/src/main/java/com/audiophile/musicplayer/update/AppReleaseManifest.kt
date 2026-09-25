package com.audiophile.musicplayer.update

data class AppReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val donateUrl: String,
    val changelog: String
)
