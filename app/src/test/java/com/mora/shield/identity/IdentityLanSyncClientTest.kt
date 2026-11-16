package com.mora.shield.identity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityLanSyncClientTest {
    @Test
    fun privateIpv4RangeIsAccepted() {
        assertTrue(IdentityLanSyncClient.isPrivateIpv4("192.168.1.20"))
        assertTrue(IdentityLanSyncClient.isPrivateIpv4("10.0.0.8"))
        assertTrue(IdentityLanSyncClient.isPrivateIpv4("172.31.255.254"))
    }

    @Test
    fun publicAndMalformedHostsAreRejected() {
        listOf(
            "8.8.8.8", "1.1.1.1", "127.0.0.1", "192.169.1.4",
            "172.32.0.1", "192.168.1", "192.168.1.999", "localhost", "2001:db8::1"
        ).forEach { host ->
            assertFalse("Expected $host to be rejected", IdentityLanSyncClient.isPrivateIpv4(host))
        }
    }

    @Test
    fun certificatePinAcceptsColonSeparatedSha256() {
        val plain = "0123456789abcdef".repeat(4)
        val colonSeparated = plain.chunked(2).joinToString(":")
        assertArrayEquals(
            IdentityLanSyncClient.parseFingerprint(plain),
            IdentityLanSyncClient.parseFingerprint(colonSeparated)
        )
        assertEquals(32, IdentityLanSyncClient.parseFingerprint(plain).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun certificatePinRejectsWrongLength() {
        IdentityLanSyncClient.parseFingerprint("1234:5678")
    }

    @Test
    fun validPrivateLanEndpointBuildsHttpsUrl() {
        val pin = "ab".repeat(32)
        assertEquals(
            "https://192.168.1.20:8765/v1/profiles",
            IdentityLanSyncClient.validateInputs(
                host = "192.168.1.20",
                port = 8765,
                token = "short-lived-token",
                fingerprint = pin
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicInternetEndpointIsRejectedBeforeConnection() {
        IdentityLanSyncClient.validateInputs(
            host = "8.8.8.8",
            port = 8765,
            token = "short-lived-token",
            fingerprint = "ab".repeat(32)
        )
    }
}
