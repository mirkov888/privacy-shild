package com.mora.shield.identity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.mora.shield.PerAppIdentityStore
import com.mora.shield.PerAppPrivacyManager

data class IdentityRecoveryResult(
    val success: Boolean,
    val message: String,
    val restoredAppProfiles: Int = 0
)

/**
 * Versioned backup/restore for Mora Identity Studio.
 * Exported data contains local browser profiles, protection preferences, and non-security
 * per-app compatibility labels only. Import is validated before any profile is changed.
 */
class IdentityProfileRecoveryManager(context: Context) {
    private val appContext = context.applicationContext
    private val identityEngine = IdentityEngine(appContext)
    private val protectionStore = IdentityProtectionStore(appContext)
    private val rollbackPreferences = appContext.getSharedPreferences(
        "mora_identity_recovery", Context.MODE_PRIVATE
    )

    fun hasRollbackPoint(): Boolean = rollbackPreferences.contains(KEY_LAST_ROLLBACK)

    fun exportBackupJson(): String {
        val selected = identityEngine.selectedProfile()
        val protection = protectionStore.load()
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("schema", BACKUP_SCHEMA)
            .put("profileCatalog", JSONObject(identityEngine.exportCustomProfilesJson()))
            .put("selectedProfile", selected?.let {
                JSONObject()
                    .put("platform", it.platform.name)
                    .put("model", it.model)
                    .put("userAgent", it.userAgent)
            } ?: JSONObject.NULL)
            .put("protection", JSONObject()
                .put("canvasProtection", protection.canvasProtection)
                .put("webGlProtection", protection.webGlProtection)
                .put("syncClientHints", protection.syncClientHints)
                .put("randomizePerSession", protection.randomizePerSession)
                .put("webRtcProtection", protection.webRtcProtection))
            .put("perAppProfiles", PerAppIdentityStore.exportCompatibilityProfilesJson(appContext))
        return root.toString(2)
    }

    fun restoreBackupJson(raw: String): IdentityRecoveryResult {
        val validation = validateBackupJson(raw)
        if (validation != null) return IdentityRecoveryResult(false, validation)
        val previous = exportBackupJson()
        rollbackPreferences.edit().putString(KEY_LAST_ROLLBACK, previous).apply()
        return applyValidatedBackup(JSONObject(raw))
    }

    /** Rollback toggles the last two states, making an accidental restore reversible. */
    fun rollbackLastChange(): IdentityRecoveryResult {
        val previous = rollbackPreferences.getString(KEY_LAST_ROLLBACK, null)
            ?: return IdentityRecoveryResult(false, "Belum ada rollback point")
        val validation = validateBackupJson(previous)
        if (validation != null) return IdentityRecoveryResult(false, "Rollback ditolak: $validation")
        val current = exportBackupJson()
        val result = applyValidatedBackup(JSONObject(previous))
        if (result.success) {
            rollbackPreferences.edit().putString(KEY_LAST_ROLLBACK, current).apply()
        }
        return result
    }

    private fun applyValidatedBackup(root: JSONObject): IdentityRecoveryResult = runCatching {
        val catalog = root.getJSONObject("profileCatalog")
        identityEngine.clearFavorites()
        identityEngine.importCustomProfilesJson(catalog.toString())

        val selected = root.optJSONObject("selectedProfile")
        if (selected == null || root.isNull("selectedProfile")) {
            identityEngine.clearSelection()
        } else {
            val platform = IdentityPlatform.valueOf(selected.getString("platform"))
            val model = selected.getString("model")
            val userAgent = selected.getString("userAgent")
            val match = identityEngine.profilesForPlatform(platform).firstOrNull {
                it.model.equals(model, ignoreCase = true) && it.userAgent == userAgent
            } ?: throw IllegalArgumentException("Profil terpilih tidak ditemukan setelah impor")
            identityEngine.select(match.id)
        }

        val p = root.getJSONObject("protection")
        protectionStore.save(
            IdentityProtectionSettings(
                canvasProtection = p.getBoolean("canvasProtection"),
                webGlProtection = p.getBoolean("webGlProtection"),
                syncClientHints = p.getBoolean("syncClientHints"),
                randomizePerSession = p.getBoolean("randomizePerSession"),
                webRtcProtection = p.getBoolean("webRtcProtection")
            )
        )
        val restored = PerAppIdentityStore.restoreCompatibilityProfilesJson(
            appContext, root.getJSONArray("perAppProfiles")
        )
        IdentityRecoveryResult(true, "Backup berhasil dipulihkan", restored)
    }.getOrElse {
        IdentityRecoveryResult(false, "Pemulihan gagal: ${it.message ?: "kesalahan tidak diketahui"}")
    }

    companion object {
        private const val BACKUP_FORMAT = "mora-identity-studio"
        private const val BACKUP_SCHEMA = 1
        private const val KEY_LAST_ROLLBACK = "last_identity_backup"

        /** Returns null when valid, otherwise a user-readable reason. Safe for unit tests. */
        fun validateBackupJson(raw: String): String? {
            if (raw.isBlank() || raw.length > MAX_BACKUP_CHARS) return "Ukuran backup harus 1 byte–1 MB"
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return "JSON backup tidak valid"
            if (root.optString("format") != BACKUP_FORMAT || root.optInt("schema") != BACKUP_SCHEMA) {
                return "Format atau versi backup Mora tidak didukung"
            }

            val catalog = root.optJSONObject("profileCatalog") ?: return "Katalog profil tidak ditemukan"
            val profiles = catalog.optJSONArray("profiles") ?: return "Daftar profil tidak ditemukan"
            if (profiles.length() > MAX_CATALOG_PROFILES) return "Backup berisi terlalu banyak profil browser"
            val seenProfiles = mutableSetOf<String>()
            for (index in 0 until profiles.length()) {
                val item = profiles.optJSONObject(index) ?: return "Profil browser ke-$index tidak valid"
                val platform = runCatching { IdentityPlatform.valueOf(item.optString("platform")) }.getOrNull()
                    ?: return "Platform profil ke-$index tidak valid"
                val model = item.optString("model")
                val userAgent = item.optString("userAgent")
                IdentityProfileValidator.validate(model, userAgent)?.let { return "Profil browser ke-$index: $it" }
                val key = platform.name + "|" + model.lowercase() + "|" + userAgent
                if (!seenProfiles.add(key)) return "Profil browser duplikat: $model"
            }
            val favorites = catalog.optJSONArray("favorites") ?: JSONArray()
            if (favorites.length() > MAX_CATALOG_PROFILES + 250) return "Daftar favorit terlalu besar"

            val selectedValue = root.opt("selectedProfile")
            if (selectedValue != null && selectedValue !== JSONObject.NULL) {
                val selected = selectedValue as? JSONObject ?: return "Profil terpilih tidak valid"
                runCatching { IdentityPlatform.valueOf(selected.optString("platform")) }.getOrNull()
                    ?: return "Platform profil terpilih tidak valid"
                IdentityProfileValidator.validate(
                    selected.optString("model"),
                    selected.optString("userAgent")
                )?.let { return "Profil terpilih: $it" }
            }

            val protection = root.optJSONObject("protection") ?: return "Pengaturan proteksi tidak ditemukan"
            listOf("canvasProtection", "webGlProtection", "syncClientHints", "randomizePerSession", "webRtcProtection")
                .forEach { key ->
                    if (protection.opt(key) !is Boolean) return "Pengaturan $key tidak valid"
                }

            val appProfiles = root.optJSONArray("perAppProfiles") ?: return "Profil per-app tidak ditemukan"
            if (appProfiles.length() > MAX_APP_PROFILES) return "Terlalu banyak profil per-app"
            val seenPackages = mutableSetOf<String>()
            for (index in 0 until appProfiles.length()) {
                val item = appProfiles.optJSONObject(index) ?: return "Profil per-app ke-$index tidak valid"
                val pkg = item.optString("packageName")
                if (!PerAppPrivacyManager.isSafePackage(pkg)) return "Package per-app tidak diizinkan: $pkg"
                if (!seenPackages.add(pkg)) return "Package per-app duplikat: $pkg"
                if (item.opt("enabled") !is Boolean) return "Status enabled per-app tidak valid: $pkg"
                listOf("manufacturer", "brand", "model", "device", "product").forEach { field ->
                    val value = item.optString(field)
                    if (value.length !in 1..120 || value.any { it.isISOControl() }) {
                        return "Field $field tidak valid untuk $pkg"
                    }
                }
            }
            return null
        }

        private const val MAX_BACKUP_CHARS = 1_000_000
        private const val MAX_CATALOG_PROFILES = 200
        private const val MAX_APP_PROFILES = 100
    }
}
