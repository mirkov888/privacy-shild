package com.mora.shield.identity

import android.os.Build

enum class IdentityPlatform(val label: String) {
    ANDROID("Android"), IOS("iOS / iPadOS"), WINDOWS("Windows"), MACOS("macOS"), LINUX("Linux")
}

data class IdentityProfile(
    val id: String,
    val platform: IdentityPlatform,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val browser: String,
    val userAgent: String,
    val purpose: String
)

data class DeviceSnapshot(
    val manufacturer: String, val brand: String, val model: String,
    val device: String, val product: String, val androidRelease: String,
    val sdkInt: Int, val securityPatch: String
) {
    companion object {
        fun capture() = DeviceSnapshot(
            Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty(), Build.MODEL.orEmpty(),
            Build.DEVICE.orEmpty(), Build.PRODUCT.orEmpty(), Build.VERSION.RELEASE.orEmpty(),
            Build.VERSION.SDK_INT, Build.VERSION.SECURITY_PATCH.orEmpty()
        )
    }
}
