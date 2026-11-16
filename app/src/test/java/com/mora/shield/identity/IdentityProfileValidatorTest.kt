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
}
