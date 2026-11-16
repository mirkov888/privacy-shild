package com.mora.shield.identity
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local compatibility-profile controller; all changes are scoped to Mora-owned preview WebViews. */
class IdentityEngine(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mora_identity_studio", Context.MODE_PRIVATE)
    fun selectedProfile(): IdentityProfile? = profileById(prefs.getString(KEY_SELECTED_PROFILE, null))
    fun profileById(id: String?): IdentityProfile? {
        if (id.isNullOrBlank()) return null
        return IdentityProfileCatalog.byId(id) ?: customProfiles().firstOrNull { it.id == id }
    }
    fun profilesForPlatform(platform: IdentityPlatform): List<IdentityProfile> =
        (IdentityProfileCatalog.forPlatform(platform) + customProfiles().filter { it.platform == platform })
            .sortedWith(compareByDescending<IdentityProfile> { isFavorite(it.id) }.thenBy { it.model.lowercase() })
    fun customProfiles(): List<IdentityProfile> {
        val raw = prefs.getString(KEY_CUSTOM_PROFILES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { profileFromJson(array.optJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
    fun select(profileId: String): Boolean {
        if (profileById(profileId) == null) return false
        prefs.edit().putString(KEY_SELECTED_PROFILE, profileId).apply()
        return true
    }
    fun clearSelection() { prefs.edit().remove(KEY_SELECTED_PROFILE).apply() }

    /** Clear local catalog state before applying a complete, validated recovery snapshot. */
    fun clearForRestore() {
        prefs.edit()
            .putString(KEY_CUSTOM_PROFILES, "[]")
            .remove(KEY_SELECTED_PROFILE)
            .remove(KEY_FAVORITES)
            .apply()
    }
    fun randomProfile(platform: IdentityPlatform): IdentityProfile? {
        val candidates = profilesForPlatform(platform)
        if (candidates.isEmpty()) return null
        return candidates.filterNot { it.id == selectedProfile()?.id }.ifEmpty { candidates }.random()
    }
    fun isCustomProfile(profileId: String): Boolean = customProfiles().any { it.id == profileId }
    fun saveCustomProfile(platform: IdentityPlatform, label: String, userAgent: String): IdentityProfile? {
        if (IdentityProfileValidator.validate(label, userAgent) != null) return null
        val profile = IdentityProfile(
            id = "custom-${UUID.randomUUID()}", platform = platform, manufacturer = "Custom",
            model = label.trim(), osVersion = "Custom", browser = "Custom UA",
            userAgent = userAgent.trim(), purpose = "User-defined browser compatibility profile"
        )
        persistCustomProfiles(customProfiles() + profile)
        return profile
    }
    fun deleteCustomProfile(profileId: String): Boolean {
        val current = customProfiles()
        if (current.none { it.id == profileId }) return false
        persistCustomProfiles(current.filterNot { it.id == profileId })
        prefs.edit().putStringSet(KEY_FAVORITES, favoriteIds().toMutableSet().apply { remove(profileId) }).apply()
        if (prefs.getString(KEY_SELECTED_PROFILE, null) == profileId) clearSelection()
        return true
    }
    fun favoriteIds(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
    fun clearFavorites() { prefs.edit().remove(KEY_FAVORITES).apply() }
    fun isFavorite(profileId: String): Boolean = profileId in favoriteIds()
    fun toggleFavorite(profileId: String): Boolean {
        if (profileById(profileId) == null) return false
        val favorites = favoriteIds().toMutableSet()
        if (!favorites.add(profileId)) favorites.remove(profileId)
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
        return true
    }
    fun exportCustomProfilesJson(): String {
        val root = JSONObject().put("schema", 1)
        val array = JSONArray()
        customProfiles().forEach { array.put(profileToJson(it)) }
        root.put("profiles", array).put("favorites", JSONArray(favoriteIds().toList()))
        return root.toString(2)
    }
    /** Strict validation for the authenticated LAN sync path; file import keeps its tolerant behavior. */
    fun importSyncedProfilesJson(raw: String): Int {
        validateSyncPayload(raw)
        return importCustomProfilesJson(raw)
    }

    fun importCustomProfilesJson(raw: String): Int {
        if (raw.length > MAX_IMPORT_CHARS) return 0
        return runCatching {
            val root = JSONObject(raw)
            val source = root.optJSONArray("profiles") ?: JSONArray()
            val existing = customProfiles().toMutableList()
            // Treat bundled presets as already-known records so LAN/file import does not
            // create a custom duplicate when the receiving platform already has that preset.
            val keys = (IdentityProfileCatalog.profiles + existing)
                .associateBy { it.platform.name + "|" + it.model.lowercase() + "|" + it.userAgent }
                .toMutableMap()
            val importedIdMap = mutableMapOf<String, String>()
            var added = 0
            for (index in 0 until minOf(source.length(), MAX_IMPORTED_PROFILES)) {
                val item = source.optJSONObject(index) ?: continue
                val platform = runCatching { IdentityPlatform.valueOf(item.optString("platform")) }.getOrNull() ?: continue
                val label = item.optString("model").trim()
                val ua = item.optString("userAgent").trim()
                if (IdentityProfileValidator.validate(label, ua) != null) continue
                val key = platform.name + "|" + label.lowercase() + "|" + ua
                val oldId = item.optString("id")
                val existingProfile = keys[key]
                if (existingProfile != null) {
                    if (oldId.isNotBlank()) importedIdMap[oldId] = existingProfile.id
                    continue
                }
                val newId = "custom-" + UUID.randomUUID()
                val newProfile = IdentityProfile(
                    id = newId, platform = platform, manufacturer = "Custom",
                    model = label, osVersion = "Imported", browser = "Custom UA", userAgent = ua,
                    purpose = "Imported browser compatibility profile"
                )
                existing += newProfile
                keys[key] = newProfile
                if (oldId.isNotBlank()) importedIdMap[oldId] = newId
                added++
            }
            if (added > 0) persistCustomProfiles(existing)

            val sourceFavorites = root.optJSONArray("favorites") ?: JSONArray()
            if (sourceFavorites.length() > 0) {
                val allowedIds = (IdentityProfileCatalog.profiles.map { it.id } + existing.map { it.id }).toSet()
                val favorites = favoriteIds().toMutableSet()
                for (index in 0 until sourceFavorites.length()) {
                    val oldId = sourceFavorites.optString(index)
                    val mappedId = importedIdMap[oldId] ?: oldId
                    if (mappedId in allowedIds) favorites.add(mappedId)
                }
                prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
            }
            added
        }.getOrDefault(0)
    }
    private fun persistCustomProfiles(profiles: List<IdentityProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(profileToJson(it)) }
        prefs.edit().putString(KEY_CUSTOM_PROFILES, array.toString()).apply()
    }
    private fun profileToJson(profile: IdentityProfile) = JSONObject().apply {
        put("id", profile.id); put("platform", profile.platform.name); put("model", profile.model); put("userAgent", profile.userAgent)
    }
    private fun profileFromJson(item: JSONObject?): IdentityProfile? {
        if (item == null) return null
        val platform = runCatching { IdentityPlatform.valueOf(item.optString("platform")) }.getOrNull() ?: return null
        val model = item.optString("model").trim()
        val ua = item.optString("userAgent").trim()
        if (IdentityProfileValidator.validate(model, ua) != null) return null
        val id = item.optString("id").takeIf { it.startsWith("custom-") } ?: "custom-${UUID.randomUUID()}"
        return IdentityProfile(id, platform, "Custom", model, "Custom", "Custom UA", ua, "User-defined browser compatibility profile")
    }
    fun selectedUserAgent(): String? = selectedProfile()?.userAgent
    fun currentDevice(): DeviceSnapshot = DeviceSnapshot.capture()
    companion object {
        private const val KEY_SELECTED_PROFILE = "selected_profile_id"
        private const val KEY_CUSTOM_PROFILES = "custom_profiles_json"
        private const val KEY_FAVORITES = "favorite_profile_ids"
        private const val MAX_IMPORT_CHARS = 256_000
        private const val MAX_IMPORTED_PROFILES = 100

        internal fun validateSyncPayload(raw: String): Int {
            require(raw.length <= MAX_IMPORT_CHARS) { "Payload sinkronisasi melewati 256 KB." }
            val root = JSONObject(raw)
            require(root.optInt("schema", -1) == 1) { "Schema profil tidak didukung." }
            val source = root.optJSONArray("profiles")
                ?: throw IllegalArgumentException("Daftar profil tidak ditemukan.")
            require(source.length() <= MAX_IMPORTED_PROFILES) { "Maksimal 100 profil per sinkronisasi." }
            val ids = mutableSetOf<String>()
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index)
                    ?: throw IllegalArgumentException("Entri profil ke-${index + 1} tidak valid.")
                val id = item.optString("id").trim()
                require(id.isNotBlank() && id.length <= 128 && ids.add(id)) {
                    "ID profil kosong, terlalu panjang, atau duplikat."
                }
                val platform = runCatching {
                    IdentityPlatform.valueOf(item.optString("platform"))
                }.getOrNull()
                require(platform != null) { "Platform profil ke-${index + 1} tidak didukung." }
                val model = item.optString("model").trim()
                val userAgent = item.optString("userAgent").trim()
                val validationError = IdentityProfileValidator.validate(model, userAgent)
                require(validationError == null) {
                    "Profil ke-${index + 1} tidak valid: ${validationError ?: "data tidak sesuai"}"
                }
            }
            return source.length()
        }
    }
}
