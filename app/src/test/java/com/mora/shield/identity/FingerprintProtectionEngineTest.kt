package com.mora.shield.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintProtectionEngineTest {
    @Test
    fun canvasProtectionAddsReadAndExportGuards() {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(
            IdentityProtectionSettings(canvasProtection = true, webGlProtection = false),
            "session-one"
        )
        assertTrue(script.contains("getImageData"))
        assertTrue(script.contains("toDataURL"))
        assertTrue(script.contains("toBlob"))
        assertFalse(script.contains("WebGLRenderingContext"))
    }

    @Test
    fun webGlProtectionMasksRendererAndDebugExtension() {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(
            IdentityProtectionSettings(canvasProtection = false, webGlProtection = true),
            "session-two"
        )
        assertTrue(script.contains("webgl_debug_renderer_info"))
        assertTrue(script.contains("WebGL Renderer"))
        assertTrue(script.contains("WebGL2RenderingContext"))
        assertFalse(script.contains("CanvasRenderingContext2D"))
    }

    @Test
    fun disabledProtectionProducesNoInjection() {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(
            IdentityProtectionSettings(
                canvasProtection = false,
                webGlProtection = false,
                syncClientHints = false,
                randomizePerSession = false
            ),
            "session-three"
        )
        assertTrue(script.isEmpty())
    }

    @Test
    fun sessionSeedsAreRandomAndOpaque() {
        val first = FingerprintProtectionEngine.newSessionSeed()
        val second = FingerprintProtectionEngine.newSessionSeed()
        assertNotEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{32}")))
        assertTrue(second.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun injectedSeedIsEscapedToSafeCharacters() {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(
            IdentityProtectionSettings(),
            "ok';alert(1);//"
        )
        assertFalse(script.contains("alert(1)"))
        assertTrue(script.contains("const moraSeed='okalert1'"))
    }
}
