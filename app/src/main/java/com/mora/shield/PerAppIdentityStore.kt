package com.mora.shield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
    private const val ROLLBACK_PREFS = "per_app_identity_rollback"
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
        validateProfile(profile)
        val previous = load(context, profile.packageName)
        context.getSharedPreferences(ROLLBACK_PREFS, Context.MODE_PRIVATE).edit()
            .putString(profile.packageName, encodeCompatibilityProfile(previous).toString())
            .apply()
        writeProfile(context, profile)
    }

    fun hasRollback(context: Context, pkg: String): Boolean =
        context.getSharedPreferences(ROLLBACK_PREFS, Context.MODE_PRIVATE).contains(pkg)

    /** Restore one previous per-app compatibility profile without creating another rollback layer. */
    fun rollback(context: Context, pkg: String): Boolean {
        if (!PerAppPrivacyManager.isSafePackage(pkg)) return false
        val rollbackPrefs = context.getSharedPreferences(ROLLBACK_PREFS, Context.MODE_PRIVATE)
        val raw = rollbackPrefs.getString(pkg, null) ?: return false
        val current = load(context, pkg)
        val previous = runCatching {
            val item = JSONObject(raw)
            DeviceIdentityProfile(
                packageName = pkg,
                enabled = item.optBoolean("enabled", false),
                manufacturer = item.optString("manufacturer", current.manufacturer),
                brand = item.optString("brand", current.brand),
                model = item.optString("model", current.model),
                device = item.optString("device", current.device),
                product = item.optString("product", current.product),
                fingerprint = current.fingerprint,
                release = current.release,
                sdk = current.sdk,
                securityPatch = current.securityPatch
            )
        }.getOrNull() ?: return false
        if (runCatching { validateProfile(previous) }.isFailure) return false
        writeProfile(context, previous)
        rollbackPrefs.edit().remove(pkg).apply()
        return true
    }

    fun disableAll(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packages = p.all.keys.mapNotNull { key ->
            key.substringBeforeLast(':', "").takeIf { it.isNotBlank() && PerAppPrivacyManager.isSafePackage(it) }
        }.toSet()
        packages.forEach { pkg -> save(context, load(context, pkg).copy(enabled = false)) }
    }

    /**
     * Export only compatibility labels. Security/build identifiers (fingerprint, SDK,
     * release, patch level, IMEI, serial, Android ID and attestation) are deliberately omitted.
     */
    fun exportCompatibilityProfilesJson(context: Context): JSONArray {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packages = p.all.keys.mapNotNull { rawKey ->
            rawKey.substringBeforeLast(':', "").takeIf {
                it.isNotBlank() && PerAppPrivacyManager.isSafePackage(it)
            }
        }.toSet()
        return JSONArray().apply {
            packages.sorted().forEach { pkg ->
                put(encodeCompatibilityProfile(load(context, pkg)))
            }
        }
    }

    fun validateCompatibilityProfilesJson(source: JSONArray): String? {
        if (source.length() > MAX_BACKUP_PROFILES) return "Backup berisi terlalu banyak profil aplikasi"
        val seen = mutableSetOf<String>()
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: return "Format profil aplikasi ke-$index tidak valid"
            val pkg = item.optString("packageName")
            if (!PerAppPrivacyManager.isSafePackage(pkg)) return "Package target tidak diizinkan: $pkg"
            if (!seen.add(pkg)) return "Package duplikat pada backup: $pkg"
            if (!item.has("enabled") || item.opt("enabled") !is Boolean) return "Status enabled tidak valid untuk $pkg"
            listOf("manufacturer", "brand", "model", "device", "product").forEach { field ->
                val value = item.optString(field)
                if (value.length !in 1..120 || value.any { it.isISOControl() }) {
                    return "Field $field tidak valid untuk $pkg"
                }
            }
        }
        return null
    }

    /** Restore a validated portable list while preserving local legacy security/build fields. */
    fun restoreCompatibilityProfilesJson(context: Context, source: JSONArray): Int {
        validateCompatibilityProfilesJson(source)?.let { throw IllegalArgumentException(it) }
        val profiles = (0 until source.length()).map { index ->
            val item = source.getJSONObject(index)
            val pkg = item.getString("packageName")
            val old = load(context, pkg)
            old.copy(
                enabled = item.getBoolean("enabled"),
                manufacturer = item.getString("manufacturer"),
                brand = item.getString("brand"),
                model = item.getString("model"),
                device = item.getString("device"),
                product = item.getString("product")
            )
        }
        profiles.forEach { save(context, it) }
        return profiles.size
    }

    private fun encodeCompatibilityProfile(profile: DeviceIdentityProfile) = JSONObject().apply {
        put("packageName", profile.packageName)
        put("enabled", profile.enabled)
        put("manufacturer", profile.manufacturer)
        put("brand", profile.brand)
        put("model", profile.model)
        put("device", profile.device)
        put("product", profile.product)
    }

    private fun validateProfile(profile: DeviceIdentityProfile) {
        require(PerAppPrivacyManager.isSafePackage(profile.packageName)) { "Paket aplikasi tidak diizinkan" }
        listOf(profile.manufacturer, profile.brand, profile.model, profile.device, profile.product,
            profile.fingerprint, profile.release, profile.securityPatch).forEach {
            require(it.length <= 180 && it.none(Char::isISOControl)) { "Nilai profil mengandung karakter tidak valid" }
        }
        require(profile.sdk in 26..99)
    }

    private fun writeProfile(context: Context, profile: DeviceIdentityProfile) {
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

    private const val MAX_BACKUP_PROFILES = 100
}
