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

    @Test fun hiddenParserReadsOnlyUserZeroHiddenPackages() {
        val dump = """
            Package [com.example.hidden] (abc):
              User 0: ceDataInode=1 installed=true hidden=true suspended=false
            Package [com.example.visible] (def):
              User 0: ceDataInode=2 installed=true hidden=false suspended=false
            Package [com.example.otheruser] (ghi):
              User 10: ceDataInode=3 installed=true hidden=true suspended=false
        """.trimIndent()
        assertEquals(setOf("com.example.hidden"), AppControlManager.parseHiddenPackages(dump))
    }

    @Test fun parserStripsPackagePrefixAndFiltersInvalidEntries() {
        assertEquals(
            setOf("com.example.app", "org.mozilla.firefox"),
            AppControlManager.parsePackages("package:com.example.app\npackage:org.mozilla.firefox\npackage:android\ninvalid")
        )
    }
}
