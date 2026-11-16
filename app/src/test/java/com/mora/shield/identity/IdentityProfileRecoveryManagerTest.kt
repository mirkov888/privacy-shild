package com.mora.shield.identity

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityProfileRecoveryManagerTest {
    @Test
    fun rejectsMalformedJson() {
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson("{not-json"))
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val raw = """{"format":"mora-identity-studio","schema":99}"""
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson(raw))
    }

    @Test
    fun acceptsMinimalValidBackup() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":1,
              "profileCatalog":{"schema":1,"profiles":[],"favorites":[]},
              "selectedProfile":null,
              "protection":{
                "canvasProtection":true,
                "webGlProtection":true,
                "syncClientHints":true,
                "randomizePerSession":true,
                "webRtcProtection":true
              },
              "perAppProfiles":[]
            }
        """.trimIndent()
        val validation = IdentityProfileRecoveryManager.validateBackupJson(raw)
        assertNull("Unexpected validation error: $validation", validation)
    }

    @Test
    fun acceptsSchemaTwoRandomizationSettings() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":2,
              "profileCatalog":{"schema":1,"profiles":[],"favorites":[]},
              "selectedProfile":null,
              "protection":{
                "canvasProtection":true,
                "webGlProtection":true,
                "syncClientHints":true,
                "randomizePerSession":true,
                "webRtcProtection":true
              },
              "randomization":{
                "enabled":true,
                "intervalHours":24,
                "seed":"reusable-seed",
                "platform":"ANDROID",
                "randomizeEnabledApps":false
              },
              "perAppProfiles":[]
            }
        """.trimIndent()
        val validation = IdentityProfileRecoveryManager.validateBackupJson(raw)
        assertNull("Unexpected validation error: $validation", validation)
    }

    @Test
    fun rejectsOutOfRangeRandomizationInterval() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":2,
              "profileCatalog":{"schema":1,"profiles":[],"favorites":[]},
              "selectedProfile":null,
              "protection":{
                "canvasProtection":true,
                "webGlProtection":true,
                "syncClientHints":true,
                "randomizePerSession":true,
                "webRtcProtection":true
              },
              "randomization":{
                "enabled":true,
                "intervalHours":1,
                "seed":"reusable-seed",
                "platform":"ANDROID",
                "randomizeEnabledApps":false
              },
              "perAppProfiles":[]
            }
        """.trimIndent()
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson(raw))
    }

    @Test
    fun rejectsEmptyRandomizationSeed() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":2,
              "profileCatalog":{"schema":1,"profiles":[],"favorites":[]},
              "selectedProfile":null,
              "protection":{
                "canvasProtection":true,
                "webGlProtection":true,
                "syncClientHints":true,
                "randomizePerSession":true,
                "webRtcProtection":true
              },
              "randomization":{
                "enabled":true,
                "intervalHours":24,
                "seed":"",
                "platform":"ANDROID",
                "randomizeEnabledApps":false
              },
              "perAppProfiles":[]
            }
        """.trimIndent()
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson(raw))
    }

    @Test
    fun rejectsSystemPackageTargets() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":1,
              "profileCatalog":{"schema":1,"profiles":[],"favorites":[]},
              "selectedProfile":null,
              "protection":{
                "canvasProtection":true,
                "webGlProtection":true,
                "syncClientHints":true,
                "randomizePerSession":true,
                "webRtcProtection":true
              },
              "perAppProfiles":[{
                "packageName":"com.android.systemui",
                "enabled":true,
                "manufacturer":"Google",
                "brand":"google",
                "model":"Pixel 9",
                "device":"tokay",
                "product":"tokay"
              }]
            }
        """.trimIndent()
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson(raw))
    }

    @Test
    fun rejectsUnsafeUserAgentInCatalog() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":1,
              "profileCatalog":{
                "schema":1,
                "profiles":[{
                  "id":"custom-test",
                  "platform":"ANDROID",
                  "model":"Broken profile",
                  "userAgent":"not-a-browser"
                }],
                "favorites":[]
              },
              "selectedProfile":null,
              "protection":{
                "canvasProtection":true,
                "webGlProtection":true,
                "syncClientHints":true,
                "randomizePerSession":true,
                "webRtcProtection":true
              },
              "perAppProfiles":[]
            }
        """.trimIndent()
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson(raw))
    }

    @Test
    fun rejectsMissingProtectionSwitch() {
        val raw = """
            {
              "format":"mora-identity-studio",
              "schema":1,
              "profileCatalog":{"schema":1,"profiles":[],"favorites":[]},
              "selectedProfile":null,
              "protection":{"canvasProtection":true},
              "perAppProfiles":[]
            }
        """.trimIndent()
        assertNotNull(IdentityProfileRecoveryManager.validateBackupJson(raw))
    }
}
