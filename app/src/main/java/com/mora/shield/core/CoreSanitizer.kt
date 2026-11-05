package com.mora.shield.core

internal object CoreSanitizer {
    private val packagePattern = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    private val tokenPattern = Regex(
        "(?i)\\b(api[_ -]?hash|api[_ -]?id|access[_ -]?token|refresh[_ -]?token|password|passwd|otp|2fa|session|string[_ -]?session|secret|authorization)\\b\\s*[:=]\\s*(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)"
    )
    private val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    fun validPackage(value: String): Boolean = value.isNotBlank() && packagePattern.matches(value)

    fun cleanText(value: String, maxLength: Int): String {
        var clean = value.trim()
        clean = tokenPattern.replace(clean) { match -> "${match.groupValues[1]}=[REDACTED]" }
        // URLs can contain private channel IDs, tokens and query parameters; do not persist them.
        clean = urlPattern.replace(clean, "[URL REDACTED]")
        return clean.take(maxLength)
    }

    fun validEventType(value: String): Boolean =
        value.matches(Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$"))

    fun validCommandId(value: String): Boolean =
        value.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z0-9_-]+){1,4}$"))
}
