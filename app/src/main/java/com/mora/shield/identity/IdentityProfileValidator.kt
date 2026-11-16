package com.mora.shield.identity
object IdentityProfileValidator {
    fun validate(label: String, userAgent: String): String? {
        val name = label.trim()
        val ua = userAgent.trim()
        if (name.length !in 2..80) return "Nama profil harus 2–80 karakter"
        if (ua.length !in 40..512) return "User-Agent harus 40–512 karakter"
        if (!ua.startsWith("Mozilla/5.0")) return "User-Agent harus diawali Mozilla/5.0"
        if (ua.any { it == '\n' || it == '\r' || it == '\u0000' }) return "User-Agent mengandung karakter baris yang tidak valid"
        return null
    }
}
