package com.mora.shield

object HostInputValidator {
    private val domainPattern = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]{1,251}[A-Za-z0-9])?\\.[A-Za-z]{2,63}$")
    private val urlPattern = Regex("^https://[A-Za-z0-9._~:/?#@!&=+%,-]+$")

    fun domainOk(value: String): Boolean =
        value.length in 4..253 && domainPattern.matches(value) &&
            value.split('.').all { it.isNotEmpty() && !it.startsWith('-') && !it.endsWith('-') }

    fun urlOk(value: String): Boolean = value.length in 12..500 && urlPattern.matches(value) &&
        !value.contains("..") && !value.contains("..")

    fun quoteUrl(value: String): String = "'$value'"
}
