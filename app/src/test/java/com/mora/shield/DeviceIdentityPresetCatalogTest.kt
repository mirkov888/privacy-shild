package com.mora.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityPresetCatalogTest {
    @Test fun presetIdsAreUnique() {
        val ids = DeviceIdentityPresetCatalog.presets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun catalogContainsBroadDeviceCoverage() {
        assertTrue("Expected at least 30 device presets", DeviceIdentityPresetCatalog.presets.size >= 30)
        listOf("Google", "Samsung", "Xiaomi", "OnePlus", "OPPO", "vivo").forEach { maker ->
            assertTrue("Missing manufacturer $maker", DeviceIdentityPresetCatalog.presets.any { it.manufacturer.equals(maker, true) })
        }
    }

    @Test fun presetsDoNotInventBuildFingerprintsOrSecurityPatches() {
        DeviceIdentityPresetCatalog.presets.forEach { preset ->
            val profile = preset.toProfile("com.example.compatibility")
            assertTrue(preset.id, profile.fingerprint.isEmpty())
            assertTrue(preset.id, profile.securityPatch.isEmpty())
            assertTrue(preset.id, profile.sdk in 26..99)
        }
    }
}
