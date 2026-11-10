package com.mora.shield

import android.content.Context

data class DeviceIdentityProfile(
    val packageName: String,
    val enabled: Boolean = false,
    val manufacturer: String = "Google",
    val brand: String = "google",
    val model: String = "Pixel 9 Pro",
    val device: String = "komodo",
    val product: String = "komodo",
    val fingerprint: String = "google/komodo/komodo:16/BP2A.250605.031/12345678:user/release-keys",
    val release: String = "16",
    val sdk: Int = 36,
    val securityPatch: String = "2026-10-05"
)

object PerAppIdentityStore {
    private const val PREFS = "per_app_identity_profiles"
    private fun key(pkg: String, field: String) = "$pkg:$field"

    fun load(context: Context, pkg: String): DeviceIdentityProfile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DeviceIdentityProfile(
            packageName = pkg,
            enabled = p.getBoolean(key(pkg, "enabled"), false),
            manufacturer = p.getString(key(pkg, "manufacturer"), "Google") ?: "Google",
            brand = p.getString(key(pkg, "brand"), "google") ?: "google",
            model = p.getString(key(pkg, "model"), "Pixel 9 Pro") ?: "Pixel 9 Pro",
            device = p.getString(key(pkg, "device"), "komodo") ?: "komodo",
            product = p.getString(key(pkg, "product"), "komodo") ?: "komodo",
            fingerprint = p.getString(key(pkg, "fingerprint"), "google/komodo/komodo:16/BP2A.250605.031/12345678:user/release-keys") ?: "",
            release = p.getString(key(pkg, "release"), "16") ?: "16",
            sdk = p.getInt(key(pkg, "sdk"), 36),
            securityPatch = p.getString(key(pkg, "patch"), "2026-10-05") ?: "2026-10-05"
        )
    }

    fun save(context: Context, profile: DeviceIdentityProfile) {
        require(profile.packageName.matches(Regex("[A-Za-z0-9_.]+")))
        listOf(profile.manufacturer, profile.brand, profile.model, profile.device, profile.product,
            profile.fingerprint, profile.release, profile.securityPatch).forEach {
            require(it.length <= 180 && !it.contains('\n') && !it.contains('\r'))
        }
        require(profile.sdk in 26..99)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(key(profile.packageName, "enabled"), profile.enabled)
            .putString(key(profile.packageName, "manufacturer"), profile.manufacturer)
            .putString(key(profile.packageName, "brand"), profile.brand)
            .putString(key(profile.packageName, "model"), profile.model)
            .putString(key(profile.packageName, "device"), profile.device)
            .putString(key(profile.packageName, "product"), profile.product)
            .putString(key(profile.packageName, "fingerprint"), profile.fingerprint)
            .putString(key(profile.packageName, "release"), profile.release)
            .putInt(key(profile.packageName, "sdk"), profile.sdk)
            .putString(key(profile.packageName, "patch"), profile.securityPatch)
            .apply()
    }

    fun disableAll(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e = p.edit()
        p.all.keys.filter { it.endsWith(":enabled") }.forEach { e.putBoolean(it, false) }
        e.apply()
    }
}
