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

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, 210_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plainText: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 10) { "Gunakan sandi minimal 10 karakter" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val keyBytes = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        } finally {
            keyBytes.fill(0)
        }
        val encrypted = cipher.doFinal(plainText)
        return ByteArrayOutputStream().apply { write(magic); write(salt); write(iv); write(encrypted) }.toByteArray()
    }

    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 10) { "Gunakan sandi minimal 10 karakter" }
        require(payload.size >= magic.size + 16 + 12 + 16) { "Berkas cadangan tidak lengkap" }
        require(payload.copyOfRange(0, magic.size).contentEquals(magic)) { "Format cadangan tidak dikenal" }
        val saltStart = magic.size
        val ivStart = saltStart + 16
        val dataStart = ivStart + 12
        val salt = payload.copyOfRange(saltStart, ivStart)
        val iv = payload.copyOfRange(ivStart, dataStart)
        val keyBytes = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        } finally {
            keyBytes.fill(0)
        }
        return cipher.doFinal(payload.copyOfRange(dataStart, payload.size))
    }
}
