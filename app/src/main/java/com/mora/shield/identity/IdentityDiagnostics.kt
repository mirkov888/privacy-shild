package com.mora.shield.identity

import android.content.Context
import org.json.JSONObject

/** Local before/after comparison for the Mora-owned diagnostic preview. */
object IdentityDiagnostics {
    private val fields = listOf(
        "userAgent", "clientHints", "platform", "locale", "timezone",
        "screen", "canvas", "webgl", "webRtc"
    )

    fun compare(beforeJson: String, afterJson: String): List<String> {
        val before = JSONObject(beforeJson)
        val after = JSONObject(afterJson)
        return fields.mapNotNull { field ->
            val oldValue = before.opt(field)?.toString() ?: "unavailable"
            val newValue = after.opt(field)?.toString() ?: "unavailable"
            if (oldValue == newValue) null else field + ": " + oldValue + " → " + newValue
        }
    }

    /** Summarizes observable browser surfaces without claiming that API visibility proves a network leak. */
    fun leakageSummary(raw: String): String {
        require(isValidReport(raw)) { "Laporan diagnostik tidak valid" }
        val report = JSONObject(raw)
        val userAgent = report.optString("userAgent", "")
        val hints = report.optJSONObject("clientHints")
        val uaPlatform = when {
            userAgent.contains("Android", ignoreCase = true) -> "android"
            userAgent.contains("iPhone", ignoreCase = true) || userAgent.contains("iPad", ignoreCase = true) -> "ios"
            userAgent.contains("Windows NT", ignoreCase = true) -> "windows"
            userAgent.contains("Macintosh", ignoreCase = true) -> "macos"
            userAgent.contains("Linux", ignoreCase = true) -> "linux"
            else -> null
        }
        val hintPlatform = when (hints?.optString("platform", "")?.lowercase()) {
            "android" -> "android"
            "ios" -> "ios"
            "windows" -> "windows"
            "macos" -> "macos"
            "linux" -> "linux"
            else -> null
        }
        val output = mutableListOf("MORA IDENTITY SURFACE REPORT")
        output += "User-Agent: " + if (userAgent.isBlank()) "tidak tersedia" else userAgent
        output += when {
            hints == null -> "Client Hints: tidak tersedia pada engine/profil ini."
            uaPlatform != null && hintPlatform != null && uaPlatform != hintPlatform ->
                "Client Hints: PERLU DICEK — platform UA ($uaPlatform) berbeda dari CH ($hintPlatform)."
            else -> "Client Hints: tersedia; platform=" + hints.optString("platform", "tidak dilaporkan") +
                ", mobile=" + hints.opt("mobile")
        }
        val canvas = report.optJSONObject("canvas")
        output += if (canvas?.optBoolean("available", false) == true) {
            "Canvas: readback tersedia; hash sesi=" + canvas.optString("hash", "tidak tersedia")
        } else {
            "Canvas: readback tidak tersedia atau diblokir."
        }
        val webgl = report.optJSONObject("webgl")
        output += when {
            webgl == null -> "WebGL: tidak ada data."
            !webgl.optBoolean("available", false) -> "WebGL: API tidak tersedia atau diblokir."
            webgl.optBoolean("debugExtension", false) ->
                "WebGL: renderer detail/debug extension masih terlihat."
            else -> "WebGL: API tersedia; debug renderer extension tidak terlihat."
        }
        val webRtc = report.optJSONObject("webRtc")
        output += when {
            webRtc == null -> "WebRTC: tidak ada data."
            webRtc.optBoolean("peerConnection", false) ->
                "WebRTC: RTCPeerConnection masih tersedia (bukan bukti IP bocor)."
            else -> "WebRTC: RTCPeerConnection tidak tersedia pada preview."
        }
        val locale = report.optJSONObject("locale")
        output += "Locale: " + (locale?.optString("language", "tidak tersedia") ?: "tidak tersedia")
        output += "Timezone: " + report.optString("timezone", "tidak tersedia")
        output += "Screen: " + (report.optJSONObject("screen")?.let {
            it.optInt("width").toString() + "×" + it.optInt("height") +
                " (viewport " + it.optInt("innerWidth") + "×" + it.optInt("innerHeight") + ")"
        } ?: "tidak tersedia")
        output += "Catatan: laporan ini memeriksa API dan metadata lokal. Ini tidak menguji IP publik, DNS, atau rute jaringan, dan bukan jaminan anonimitas."
        return output.joinToString("\n")
    }

    fun isValidReport(raw: String): Boolean = runCatching {
        val report = JSONObject(raw)
        report.has("userAgent") && report.has("platform") && report.has("collectedAt")
    }.getOrDefault(false)
}

class IdentityDiagnosticsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("mora_identity_diagnostics", Context.MODE_PRIVATE)

    fun baseline(): String? = prefs.getString(KEY_BASELINE, null)
    fun latest(): String? = prefs.getString(KEY_LATEST, null)

    fun saveBaseline(report: String) {
        require(IdentityDiagnostics.isValidReport(report)) { "Laporan diagnostik tidak valid" }
        prefs.edit().putString(KEY_BASELINE, report).apply()
    }

    fun saveLatest(report: String) {
        require(IdentityDiagnostics.isValidReport(report)) { "Laporan diagnostik tidak valid" }
        prefs.edit().putString(KEY_LATEST, report).apply()
    }

    fun compareWithBaseline(report: String): List<String> {
        require(IdentityDiagnostics.isValidReport(report)) { "Laporan diagnostik tidak valid" }
        val previous = baseline() ?: return listOf("Baseline belum disimpan.")
        return IdentityDiagnostics.compare(previous, report)
    }

    companion object {
        private const val KEY_BASELINE = "baseline_json"
        private const val KEY_LATEST = "latest_json"
    }
}
