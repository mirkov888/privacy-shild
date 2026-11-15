package com.mora.shield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerAppPrivacyManagerTest {
    @Test fun onlyWellFormedThirdPartyPackageNamesAreAccepted() {
        assertTrue(PerAppPrivacyManager.isSafePackage("com.example.reader"))
        assertFalse(PerAppPrivacyManager.isSafePackage("com.mora.shield"))
        assertFalse(PerAppPrivacyManager.isSafePackage("com.android.systemui"))
        assertFalse(PerAppPrivacyManager.isSafePackage("com.example;reboot"))
        assertFalse(PerAppPrivacyManager.isSafePackage("not a package"))
    }

    @Test fun permissionMutationsAreAllowlisted() {
        assertTrue(PerAppPrivacyManager.isAllowedPermission("android.permission.CAMERA"))
        assertTrue(PerAppPrivacyManager.isAllowedPermission("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(PerAppPrivacyManager.isAllowedPermission("android.permission.READ_SMS"))
        assertFalse(PerAppPrivacyManager.isAllowedPermission("android.permission.WRITE_SETTINGS"))
        assertFalse(PerAppPrivacyManager.isAllowedPermission("com.example.PERMISSION"))
    }

    @Test fun appOpsAreAllowlisted() {
        assertTrue(PerAppPrivacyManager.isAllowedOp("READ_CLIPBOARD"))
        assertTrue(PerAppPrivacyManager.isAllowedOp("RUN_ANY_IN_BACKGROUND"))
        assertFalse(PerAppPrivacyManager.isAllowedOp("SYSTEM_ALERT_WINDOW"))
        assertFalse(PerAppPrivacyManager.isAllowedOp(" arbitrary "))
    }

    @Test fun firewallModesAreClosedSet() {
        assertTrue(PerAppPrivacyManager.isAllowedFirewallMode("none"))
        assertTrue(PerAppPrivacyManager.isAllowedFirewallMode("wifi"))
        assertTrue(PerAppPrivacyManager.isAllowedFirewallMode("mobile"))
        assertTrue(PerAppPrivacyManager.isAllowedFirewallMode("both"))
        assertFalse(PerAppPrivacyManager.isAllowedFirewallMode("iptables; reboot"))
    }
}
