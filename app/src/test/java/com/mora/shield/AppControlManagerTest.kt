package com.mora.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControlManagerTest {
    @Test fun rejectsOwnPackageAndInvalidNames() {
        assertFalse(AppControlManager.isSafeThirdPartyPackage("com.mora.shield"))
        assertFalse(AppControlManager.isSafeThirdPartyPackage("android"))
        assertFalse(AppControlManager.isSafeThirdPartyPackage("com.android.settings"))
        assertFalse(AppControlManager.isSafeThirdPartyPackage("com.google.android.gms"))
        assertFalse(AppControlManager.isSafeThirdPartyPackage("com.miui.securitycenter"))
    }

    @Test fun acceptsValidPackageNames() {
        assertTrue(AppControlManager.isSafeThirdPartyPackage("com.example.app"))
        assertTrue(AppControlManager.isSafeThirdPartyPackage("org.mozilla.firefox"))
    }

    @Test fun launcherParserKeepsOnlyComponentsFromRequestedPackage() {
        val output = """
            com.example.hidden/.MainActivity
            com.example.hidden/com.example.hidden.SettingsActivity
            com.other.app/.MainActivity
            not-a-component
        """.trimIndent()
        assertEquals(
            setOf("com.example.hidden/.MainActivity", "com.example.hidden/com.example.hidden.SettingsActivity"),
            AppControlManager.parseLauncherComponents(output, "com.example.hidden")
        )
    }

    @Test fun parserStripsPackagePrefixAndFiltersInvalidEntries() {
        assertEquals(
            setOf("com.example.app", "org.mozilla.firefox"),
            AppControlManager.parsePackages("package:com.example.app\npackage:org.mozilla.firefox\npackage:android\ninvalid")
        )
    }
}
