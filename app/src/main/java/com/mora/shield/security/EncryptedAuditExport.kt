package com.mora.shield.security

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Format: magic MORA1 | 16-byte salt | 12-byte IV | AES-GCM ciphertext. */
object EncryptedAuditExport {
    private val magic = byteArrayOf(0x4d, 0x4f, 0x52, 0x41, 0x31)

    fun encrypt(plainText: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 10) { "Gunakan sandi minimal 10 karakter" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val spec = PBEKeySpec(passphrase, salt, 210_000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        keyBytes.fill(0)
        val encrypted = cipher.doFinal(plainText)
        return ByteArrayOutputStream().apply { write(magic); write(salt); write(iv); write(encrypted) }.toByteArray()
    }
}
