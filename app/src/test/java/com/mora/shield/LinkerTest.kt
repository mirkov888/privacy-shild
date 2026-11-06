package com.mora.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkerTest {
    @Test fun removesGlobalTrackingParametersAndKeepsContent() {
        val result = Linker.clean("https://example.com/watch?id=42&utm_source=news&fbclid=abc")
        assertEquals("https://example.com/watch?id=42", result.url)
        assertTrue(result.removed.contains("utm_source"))
        assertTrue(result.removed.contains("fbclid"))
    }

    @Test fun removesYoutubeShareTrackingButKeepsVideoId() {
        val result = Linker.clean("https://www.youtube.com/watch?v=abc123&si=tracking")
        assertEquals("https://www.youtube.com/watch?v=abc123", result.url)
    }

    @Test fun unwrapsGoogleRedirectAndCleansDestination() {
        val result = Linker.clean("https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fpage%3Futm_medium%3Dmail%26id%3D7")
        assertEquals("https://example.com/page?id=7", result.url)
        assertTrue(result.unwrapped)
    }

    @Test fun preservesPlainTextAndTrailingPunctuation() {
        assertEquals("halo dunia", Linker.clean("halo dunia").url)
        assertEquals("https://example.com/?id=1.", Linker.clean("https://example.com/?id=1.").url)
    }
}
