package com.mora.shield.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedAuditExportTest {
    private val pass = "mora-test-passphrase".toCharArray()

    @Test fun encryptDecryptRoundTrip() {
        val plain = "# Mora Shield backup\nads=1\ntelemetry=0\n".toByteArray()
        val encrypted = EncryptedAuditExport.encrypt(plain, pass)
        assertArrayEquals(plain, EncryptedAuditExport.decrypt(encrypted, pass))
    }

    @Test fun wrongPasswordIsRejected() {
        val encrypted = EncryptedAuditExport.encrypt("ads=1".toByteArray(), pass)
        assertThrows(Exception::class.java) {
            EncryptedAuditExport.decrypt(encrypted, "wrong-password".toCharArray())
        }
    }

    @Test fun invalidHeaderIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedAuditExport.decrypt(ByteArray(64), pass)
        }
    }
}
