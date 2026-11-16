package com.mora.shield.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityRandomizationTest {
    @Test fun sameSeedScopeAndRunAreReproducible() {
        val first = IdentityRandomizationStore.stableIndex("seed-a", "browser:ANDROID", 4, 37)
        val second = IdentityRandomizationStore.stableIndex("seed-a", "browser:ANDROID", 4, 37)
        assertEquals(first, second)
    }

    @Test fun differentScopesRemainIndependent() {
        val browser = IdentityRandomizationStore.stableIndex("seed-a", "browser:ANDROID", 4, 37)
        val app = IdentityRandomizationStore.stableIndex("seed-a", "app:com.example.app", 4, 37)
        assertNotEquals(browser, app)
    }

    @Test fun selectedIndexAlwaysFitsCandidateList() {
        repeat(100) { run ->
            val index = IdentityRandomizationStore.stableIndex("reusable", "scope", run, 13)
            assertTrue(index in 0 until 13)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyCandidateListIsRejected() {
        IdentityRandomizationStore.stableIndex("seed", "scope", 1, 0)
    }

    @Test
    fun emptyCandidateSetIsHandledWithoutSelection() {
        assertNull(IdentityRandomizationStore.stableIndexOrNull("seed", "scope", 1, 0))
    }
}
