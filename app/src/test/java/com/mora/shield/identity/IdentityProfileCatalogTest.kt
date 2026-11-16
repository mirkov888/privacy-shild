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

    @Test fun androidBrowserPickerIncludesCuratedPerAppDeviceModels() {
        val androidProfiles = IdentityProfileCatalog.forPlatform(IdentityPlatform.ANDROID)
        assertTrue("Expected Android device preset coverage in the browser picker", androidProfiles.size >= 30)
        assertTrue(androidProfiles.any { it.model == "SM-S938B" })
        assertTrue(androidProfiles.any { it.model == "Pixel 8 Pro" })
        assertTrue(androidProfiles.all { it.userAgent.contains("Android ") && it.userAgent.contains("Chrome/") })
    }

    @Test fun expandedCatalogueCoversOlderAndroidOemsAndAppleTablets() {
        val android = IdentityProfileCatalog.forPlatform(IdentityPlatform.ANDROID)
        assertTrue(android.any { it.manufacturer == "Huawei" && it.model == "P40 Pro" })
        assertTrue(android.any { it.manufacturer == "Infinix" && it.model == "GT 20 Pro" })
        assertTrue(android.any { it.manufacturer == "TECNO" && it.model == "CAMON 30 Pro 5G" })
        val tablets = IdentityProfileCatalog.forPlatform(IdentityPlatform.IOS).filter { it.model.startsWith("iPad") }
        assertTrue("Expected multiple iPad generations", tablets.size >= 5)
        assertTrue(tablets.any { it.model.contains("iPad mini") })
    }

    @Test fun desktopCatalogueIncludesLaptopAndLegacyTargets() {
        assertTrue(IdentityProfileCatalog.forPlatform(IdentityPlatform.WINDOWS).any { it.model == "Windows 7 PC" })
        assertTrue(IdentityProfileCatalog.forPlatform(IdentityPlatform.MACOS).any { it.model == "MacBook Air M1" })
        assertTrue(IdentityProfileCatalog.forPlatform(IdentityPlatform.LINUX).any { it.manufacturer == "Debian" })
    }

    @Test fun everyProfileHasUserAgent() {
        IdentityProfileCatalog.profiles.forEach { profile ->
            assertTrue(profile.id, profile.userAgent.startsWith("Mozilla/5.0"))
        }
    }
}
