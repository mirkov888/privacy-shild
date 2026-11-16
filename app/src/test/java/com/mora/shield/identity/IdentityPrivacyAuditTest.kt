package com.mora.shield.identity

import android.Manifest
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityPrivacyAuditTest {
    @Test
    fun collectPermissionFindingsOnlyReturnsTrackedPermissions() {
        val requested = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            "android.permission.INTERNET",
            "android.permission.READ_PRIVILEGED_PHONE_STATE",
            Manifest.permission.READ_CONTACTS
        )
        val flags = intArrayOf(
            PackageInfo.REQUESTED_PERMISSION_GRANTED,
            0,
            0,
            0
        )

        val findings = IdentityPrivacyAudit.collectPermissionFindings(requested, flags)

        assertEquals(
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                "android.permission.READ_PRIVILEGED_PHONE_STATE",
                Manifest.permission.READ_CONTACTS
            ),
            findings.map { it.permission }
        )
        assertTrue(findings[0].granted)
        assertFalse(findings[1].granted)
        assertFalse(findings[2].granted)
    }

    @Test
    fun missingPermissionMetadataProducesNoFindings() {
        assertTrue(
            IdentityPrivacyAudit.collectPermissionFindings(null, null).isEmpty()
        )
        assertTrue(
            IdentityPrivacyAudit.collectPermissionFindings(emptyArray(), intArrayOf()).isEmpty()
        )
    }

    @Test
    fun missingGrantFlagsAreTreatedAsNotGranted() {
        val findings = IdentityPrivacyAudit.collectPermissionFindings(
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            null
        )

        assertEquals(1, findings.size)
        assertFalse(findings.single().granted)
    }
}
