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

    @Test fun everyProfileHasUserAgent() {
        IdentityProfileCatalog.profiles.forEach { profile ->
            assertTrue(profile.id, profile.userAgent.startsWith("Mozilla/5.0"))
        }
    }
}
