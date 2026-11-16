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

    @Test fun leakageSummaryFlagsUserAgentClientHintsPlatformMismatch() {
        val report = """{"collectedAt":"2026-11-16T00:00:00Z","userAgent":"Mozilla/5.0 (Linux; Android 15) Chrome/131.0.0.0","platform":"Linux armv8l","clientHints":{"platform":"Windows","mobile":false},"canvas":{"available":true,"hash":"abcd"},"webgl":{"available":true,"debugExtension":false},"webRtc":{"peerConnection":false,"mediaDevices":true},"locale":{"language":"id-ID"},"timezone":"Asia/Jakarta","screen":{"width":1080,"height":2400,"innerWidth":412,"innerHeight":915}}"""
        val summary = IdentityDiagnostics.leakageSummary(report)
        assertTrue(summary.contains("PERLU DICEK"))
        assertTrue(summary.contains("Canvas: readback tersedia"))
        assertTrue(summary.contains("RTCPeerConnection tidak tersedia"))
        assertTrue(summary.contains("bukan jaminan anonimitas"))
    }

    @Test fun leakageSummaryDoesNotTreatMissingClientHintsAsFailure() {
        val report = """{"collectedAt":"2026-11-16T00:00:00Z","userAgent":"Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)","platform":"iPhone","canvas":{"available":false},"webgl":{"available":false},"webRtc":{"peerConnection":false},"timezone":"Asia/Jakarta"}"""
        val summary = IdentityDiagnostics.leakageSummary(report)
        assertTrue(summary.contains("Client Hints: tidak tersedia"))
        assertTrue(summary.contains("WebGL: API tidak tersedia"))
    }

    @Test fun unchangedReportsHaveNoDiff() {
        val report = """{"userAgent":"A","platform":"Linux","timezone":"UTC"}"""
        assertTrue(IdentityDiagnostics.compare(report, report).isEmpty())
    }
}
