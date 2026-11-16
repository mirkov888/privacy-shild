package com.mora.shield.identity

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.json.JSONObject

data class IdentityLanSyncResult(
    val sentCreated: Int,
    val sentUnchanged: Int,
    val receivedCreated: Int
) {
    fun summary(): String =
        "Kirim ke Linux: $sentCreated profil baru, $sentUnchanged sudah ada. " +
            "Ambil dari Linux: $receivedCreated profil baru."
}

/**
 * Explicit Android ↔ Linux profile sync over a trusted private LAN.
 *
 * The endpoint is restricted to private IPv4 addresses. TLS uses a certificate
 * fingerprint displayed by the paired Linux session; hostname verification is
 * replaced only for this connection because the short-lived self-signed cert is
 * trusted by its exact SHA-256 pin. Credentials are never persisted by this class.
 */
object IdentityLanSyncClient {
    private const val MAX_RESPONSE_BYTES = 256 * 1024
    private const val MAX_TOKEN_LENGTH = 256

    fun synchronize(
        host: String,
        port: Int,
        token: String,
        certificateFingerprint: String,
        engine: IdentityEngine
    ): IdentityLanSyncResult {
        val endpoint = validateInputs(host, port, token, certificateFingerprint)

        val outbound = engine.exportCustomProfilesJson()
        val pushResponse = request(
            endpoint = endpoint,
            method = "POST",
            token = token,
            pin = certificateFingerprint,
            body = outbound
        )
        val push = JSONObject(pushResponse)
        require(push.optBoolean("ok", false)) {
            push.optString("error", "Linux menolak data sinkronisasi.")
        }

        val inbound = request(
            endpoint = endpoint,
            method = "GET",
            token = token,
            pin = certificateFingerprint,
            body = null
        )
        require(inbound.isNotBlank() && inbound.length <= MAX_RESPONSE_BYTES) {
            "Data profil Linux kosong atau melewati batas ukuran."
        }
        val received = engine.importCustomProfilesJson(inbound)

        return IdentityLanSyncResult(
            sentCreated = push.optInt("created", 0),
            sentUnchanged = push.optInt("unchanged", 0),
            receivedCreated = received
        )
    }

    private fun validateInputs(
        host: String,
        port: Int,
        token: String,
        fingerprint: String
    ): String {
        require(isPrivateIpv4(host)) {
            "Gunakan alamat IPv4 private dari komputer Linux pada Wi-Fi yang sama."
        }
        require(port in 1..65535) { "Port harus 1–65535." }
        require(token.isNotBlank() && token.length <= MAX_TOKEN_LENGTH) {
            "Token sesi kosong atau terlalu panjang."
        }
        parseFingerprint(fingerprint)
        return "https://$host:$port/v1/profiles"
    }

    private fun isPrivateIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return false
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
        }
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun parseFingerprint(value: String): ByteArray {
        val normalized = value.filterNot { it == ':' || it.isWhitespace() }
        require(normalized.length == 64 && normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "SHA-256 certificate pin harus berisi 64 digit hexadecimal."
        }
        return ByteArray(32) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun request(
        endpoint: String,
        method: String,
        token: String,
        pin: String,
        body: String?
    ): String {
        val expectedPin = parseFingerprint(pin)
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                throw CertificateException("Client certificate tidak digunakan.")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull()
                    ?: throw CertificateException("Server tidak mengirim sertifikat.")
                leaf.checkValidity()
                val actualPin = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
                if (!MessageDigest.isEqual(expectedPin, actualPin)) {
                    throw CertificateException("Certificate fingerprint Linux tidak cocok.")
                }
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }

        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 10_000
            doInput = true
            instanceFollowRedirects = false
            sslSocketFactory = ssl.socketFactory
            // Exact certificate pin above replaces CA/hostname trust for this LAN session.
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                require(body.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES) {
                    "Profil terlalu besar untuk sinkronisasi LAN (maksimal 256 KB)."
                }
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { readBounded(it, MAX_RESPONSE_BYTES) }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrNull()
                throw IllegalStateException(
                    message?.takeIf { it.isNotBlank() } ?: "Server Linux merespons HTTP $status."
                )
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream, maximum: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximum) { "Respons server melewati batas 256 KB." }
            output.write(buffer, 0, count)
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }
}
