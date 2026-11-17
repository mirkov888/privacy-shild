package com.mora.shield.identity

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityStudioInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun catalogSelectionAndCustomProfileRoundTripPersistOnAndroid() {
        val preferences = context.getSharedPreferences("mora_identity_studio", 0)
        preferences.edit().clear().commit()
        val engine = IdentityEngine(context)

        IdentityPlatform.values().forEach { platform ->
            assertTrue("$platform has no compatibility profiles", engine.profilesForPlatform(platform).isNotEmpty())
        }

        val builtIn = engine.profilesForPlatform(IdentityPlatform.ANDROID).first()
        assertTrue(engine.select(builtIn.id))
        assertEquals(builtIn.id, engine.selectedProfile()?.id)

        val custom = engine.saveCustomProfile(
            IdentityPlatform.ANDROID,
            "Mora QA Profile",
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        )
        assertNotNull(custom)
        custom!!
        assertTrue(engine.toggleFavorite(custom.id))
        assertTrue(engine.isFavorite(custom.id))

        val exported = engine.exportCustomProfilesJson()
        assertTrue(engine.deleteCustomProfile(custom.id))
        assertEquals(1, engine.importCustomProfilesJson(exported))

        val restored = engine.customProfiles().single()
        assertEquals("Mora QA Profile", restored.model)
        assertTrue(engine.isFavorite(restored.id))
    }

    @Test
    fun fingerprintProtectionScriptMatchesEnabledSettings() {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(
            IdentityProtectionSettings(),
            "android-instrumentation-seed"
        )
        assertTrue(script.contains("getImageData"))
        assertTrue(script.contains("WEBGL_DEBUG_RENDERER_INFO") || script.contains("webgl_debug_renderer_info"))
        assertTrue(script.contains("RTCPeerConnection"))

        val disabled = FingerprintProtectionEngine.buildDocumentStartScript(
            IdentityProtectionSettings(
                canvasProtection = false,
                webGlProtection = false,
                syncClientHints = false,
                randomizePerSession = false,
                webRtcProtection = false
            ),
            "android-instrumentation-seed"
        )
        assertEquals("", disabled)
    }

    @Test
    fun backupAndDiagnosticsValidationWorkOnAndroidRuntime() {
        val manager = IdentityProfileRecoveryManager(context)
        val backup = manager.exportBackupJson()
        assertNull(IdentityProfileRecoveryManager.validateBackupJson(backup))
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson("{invalid"))

        val before = JSONObject()
            .put("userAgent", "Mozilla/5.0 Android")
            .put("platform", "Android")
            .put("collectedAt", 1L)
            .put("canvas", JSONObject().put("available", true).put("hash", "before"))
        val after = JSONObject(before.toString())
            .put("canvas", JSONObject().put("available", true).put("hash", "after"))

        assertTrue(IdentityDiagnostics.isValidReport(before.toString()))
        assertTrue(IdentityDiagnostics.compare(before.toString(), after.toString())
            .any { it.startsWith("canvas:") })
        assertFalse(IdentityDiagnostics.compare(before.toString(), JSONObject(before.toString()).toString())
            .any { it.startsWith("canvas:") })
    }

    @Test
    fun deterministicRandomizerAndClientHintsAreConsistent() {
        val first = IdentityRandomizationStore.stableIndex("mora-seed", "browser:ANDROID", 12, 31)
        val second = IdentityRandomizationStore.stableIndex("mora-seed", "browser:ANDROID", 12, 31)
        assertEquals(first, second)

        val chromium = IdentityProfileCatalog.forPlatform(IdentityPlatform.ANDROID)
            .first { it.userAgent.contains("Chrome/") }
        assertNotNull(FingerprintProtectionEngine.clientHintsFor(chromium))
        val firefox = IdentityProfileCatalog.forPlatform(IdentityPlatform.ANDROID)
            .firstOrNull { it.userAgent.contains("Firefox/") }
        if (firefox != null) assertNull(FingerprintProtectionEngine.clientHintsFor(firefox))
    }
}
