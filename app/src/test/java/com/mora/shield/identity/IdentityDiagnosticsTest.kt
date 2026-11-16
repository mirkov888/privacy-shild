package com.mora.shield.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityDiagnosticsTest {
    @Test fun reportValidityRequiresCoreFields() {
        assertTrue(IdentityDiagnostics.isValidReport(
            """{"collectedAt":"2026-11-16T00:00:00Z","userAgent":"UA","platform":"Linux"}"""
        ))
    }

    @Test fun beforeAfterComparisonNamesChangedFields() {
        val before = """{"userAgent":"A","platform":"Linux","timezone":"UTC","canvas":{"hash":"1"}}"""
        val after = """{"userAgent":"B","platform":"Linux","timezone":"Asia/Jakarta","canvas":{"hash":"2"}}"""
        val changes = IdentityDiagnostics.compare(before, after)
        assertEquals(3, changes.size)
        assertTrue(changes.any { it.startsWith("userAgent:") })
        assertTrue(changes.any { it.startsWith("timezone:") })
        assertTrue(changes.any { it.startsWith("canvas:") })
    }

    @Test fun unchangedReportsHaveNoDiff() {
        val report = """{"userAgent":"A","platform":"Linux","timezone":"UTC"}"""
        assertTrue(IdentityDiagnostics.compare(report, report).isEmpty())
    }
}
