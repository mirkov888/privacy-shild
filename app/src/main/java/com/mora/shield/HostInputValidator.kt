package com.mora.shield

object HostInputValidator {
    private val domainPattern = Regex("^(?=.{4,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$")
    private val urlPattern = Regex("^https://[A-Za-z0-9._~:/?#@!&=+%,-]+$")

    fun domainOk(value: String): Boolean = domainPattern.matches(value)

    fun urlOk(value: String): Boolean =
        value.length in 12..500 && urlPattern.matches(value) && !value.contains("..")

    fun quoteUrl(value: String): String = "'$value'"
}
