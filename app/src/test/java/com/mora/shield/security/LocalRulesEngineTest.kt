package com.mora.shield.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRulesEngineTest {
    @Test
    fun highThresholdOnlyMatchesHighAndCritical() {
        assertFalse(LocalRulesEngine.matchesThreshold("info", LocalRulesEngine.MIN_HIGH))
        assertFalse(LocalRulesEngine.matchesThreshold("low", LocalRulesEngine.MIN_HIGH))
        assertFalse(LocalRulesEngine.matchesThreshold("medium", LocalRulesEngine.MIN_HIGH))
        assertTrue(LocalRulesEngine.matchesThreshold("high", LocalRulesEngine.MIN_HIGH))
        assertTrue(LocalRulesEngine.matchesThreshold("critical", LocalRulesEngine.MIN_HIGH))
    }

    @Test
    fun mediumThresholdMatchesMediumAndAbove() {
        assertFalse(LocalRulesEngine.matchesThreshold("info", LocalRulesEngine.MIN_MEDIUM))
        assertFalse(LocalRulesEngine.matchesThreshold("low", LocalRulesEngine.MIN_MEDIUM))
        assertTrue(LocalRulesEngine.matchesThreshold("medium", LocalRulesEngine.MIN_MEDIUM))
        assertTrue(LocalRulesEngine.matchesThreshold("high", LocalRulesEngine.MIN_MEDIUM))
        assertTrue(LocalRulesEngine.matchesThreshold("critical", LocalRulesEngine.MIN_MEDIUM))
    }

    @Test
    fun unknownSeverityDoesNotTrigger() {
        assertFalse(LocalRulesEngine.matchesThreshold("unknown", LocalRulesEngine.MIN_MEDIUM))
    }
}
