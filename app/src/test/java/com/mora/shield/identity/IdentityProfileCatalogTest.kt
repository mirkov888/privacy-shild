package com.mora.shield.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityProfileCatalogTest {
    @Test fun profileIdsAreUnique() {
        val ids = IdentityProfileCatalog.profiles.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everyPlatformHasProfiles() {
        IdentityPlatform.values().forEach { platform ->
            assertTrue("${platform.label} missing profiles", IdentityProfileCatalog.forPlatform(platform).isNotEmpty())
        }
    }

    @Test fun iphoneSafariProfilesCoverRequestedGenerations() {
        val iphoneProfiles = IdentityProfileCatalog.forPlatform(IdentityPlatform.IOS)
            .filter { it.model.startsWith("iPhone") }
        assertTrue("Expected a broad iPhone model catalogue", iphoneProfiles.size >= 15)
        assertTrue(iphoneProfiles.any { it.model == "iPhone 16 Pro Max" })
        assertTrue(iphoneProfiles.any { it.model == "iPhone 16e" })
        assertTrue(iphoneProfiles.any { it.model == "iPhone SE (3rd generation)" })
        iphoneProfiles.forEach { assertTrue(it.id, it.userAgent.contains("(iPhone; CPU iPhone OS")) }
    }

    @Test fun everyProfileHasUserAgent() {
        IdentityProfileCatalog.profiles.forEach { profile ->
            assertTrue(profile.id, profile.userAgent.startsWith("Mozilla/5.0"))
        }
    }
}
