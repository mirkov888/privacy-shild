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
