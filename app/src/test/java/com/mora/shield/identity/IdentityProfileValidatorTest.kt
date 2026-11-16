package com.mora.shield.identity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
class IdentityProfileValidatorTest {
    private val validUa = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    @Test fun acceptsWellFormedCustomProfile() { assertNull(IdentityProfileValidator.validate("Pixel QA profile", validUa)) }
    @Test fun rejectsShortNamesAndMalformedUserAgents() {
        assertNotNull(IdentityProfileValidator.validate("A", validUa))
        assertNotNull(IdentityProfileValidator.validate("Custom", "Chrome/131"))
    }
    @Test fun rejectsOversizedOrMultilineUserAgent() {
        assertNotNull(IdentityProfileValidator.validate("Custom profile", "Mozilla/5.0 " + "x".repeat(600)))
        assertNotNull(IdentityProfileValidator.validate("Custom profile", validUa + "\nInjected"))
    }

    @Test fun acceptsValidLanSyncPayload() {
        val payload = org.json.JSONObject()
            .put("schema", 1)
            .put("profiles", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("id", "linux-profile-1")
                    .put("platform", "ANDROID")
                    .put("model", "Android test profile")
                    .put("userAgent", validUa)
            ))
        org.junit.Assert.assertEquals(1, IdentityEngine.validateSyncPayload(payload.toString()))
    }

    @Test fun rejectsInvalidLanSyncPayloadBeforeImport() {
        val invalidPlatform = org.json.JSONObject()
            .put("schema", 1)
            .put("profiles", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("id", "profile-1")
                    .put("platform", "UNKNOWN")
                    .put("model", "Android test profile")
                    .put("userAgent", validUa)
            ))
        val malformedUa = org.json.JSONObject()
            .put("schema", 1)
            .put("profiles", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("id", "profile-2")
                    .put("platform", "ANDROID")
                    .put("model", "Android test profile")
                    .put("userAgent", "Chrome/131")
            ))
        listOf("{}", invalidPlatform.toString(), malformedUa.toString()).forEach { raw ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                IdentityEngine.validateSyncPayload(raw)
            }
        }
    }

    @Test fun rejectsDuplicateIdsAndOversizedProfileLists() {
        val entry = org.json.JSONObject()
            .put("id", "same-id")
            .put("platform", "ANDROID")
            .put("model", "Android test profile")
            .put("userAgent", validUa)
        val duplicate = org.json.JSONObject()
            .put("schema", 1)
            .put("profiles", org.json.JSONArray().put(entry).put(entry))
        val oversized = org.json.JSONArray()
        repeat(101) { index ->
            oversized.put(org.json.JSONObject(entry.toString()).put("id", "profile-$index"))
        }
        val tooMany = org.json.JSONObject().put("schema", 1).put("profiles", oversized)
        listOf(duplicate.toString(), tooMany.toString()).forEach { raw ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                IdentityEngine.validateSyncPayload(raw)
            }
        }
    }
}
