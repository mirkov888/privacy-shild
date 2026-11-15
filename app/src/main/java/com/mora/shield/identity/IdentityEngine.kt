package com.mora.shield.identity

import android.content.Context

/**
 * Local compatibility-profile controller.
 * Does not mutate Build properties, system identity, hardware identifiers,
 * attestation, Play Integrity, or third-party applications.
 */
class IdentityEngine(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mora_identity_studio", Context.MODE_PRIVATE)

    fun selectedProfile(): IdentityProfile? = IdentityProfileCatalog.byId(prefs.getString(KEY_SELECTED_PROFILE, null))

    fun select(profileId: String): Boolean {
        val profile = IdentityProfileCatalog.byId(profileId) ?: return false
        prefs.edit().putString(KEY_SELECTED_PROFILE, profile.id).apply()
        return true
    }

    fun clearSelection() { prefs.edit().remove(KEY_SELECTED_PROFILE).apply() }

    fun randomProfile(platform: IdentityPlatform): IdentityProfile? {
        val candidates = IdentityProfileCatalog.forPlatform(platform)
        if (candidates.isEmpty()) return null
        val pool = candidates.filterNot { it.id == selectedProfile()?.id }.ifEmpty { candidates }
        return pool.random()
    }

    /** UA from the user's selected bundled profile, for opt-in Mora-owned WebViews only. */
    fun selectedUserAgent(): String? = selectedProfile()?.userAgent
    fun currentDevice(): DeviceSnapshot = DeviceSnapshot.capture()

    companion object { private const val KEY_SELECTED_PROFILE = "selected_profile_id" }
}
