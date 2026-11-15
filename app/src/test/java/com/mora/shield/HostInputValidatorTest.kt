package com.mora.shield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostInputValidatorTest {
    @Test fun acceptsDomains() {
        assertTrue(HostInputValidator.domainOk("a.com"))
        assertTrue(HostInputValidator.domainOk("iklan.contoh.com"))
        assertTrue(HostInputValidator.domainOk("a-b.example.co.id"))
    }
    @Test fun rejectsShellInput() {
        listOf("a.com;id", "a.com && id", "a b.com", "-bad.com", "bad-.com", "a..com", "$(id).com", "a.com`id`", "nodot", "a.com|x", "").forEach {
            assertFalse(it, HostInputValidator.domainOk(it))
        }
    }
    @Test fun acceptsHttpsSources() {
        assertTrue(HostInputValidator.urlOk("https://adaway.org/hosts.txt"))
        assertTrue(HostInputValidator.urlOk("https://x.org/a?b=1&c=2"))
    }
    @Test fun rejectsUnsafeUrls() {
        listOf("http://x.org/a.txt", "https://x.org/a b", "https://x.org/a';id;'", "https://x.org/$(id)", "https://x.org/a`id`", "https://x.org/a|b", "ftp://x.org/a").forEach {
            assertFalse(it, HostInputValidator.urlOk(it))
        }
    }
}
