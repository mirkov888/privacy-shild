package com.mora.shield.identity

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

data class IdentityPermissionFinding(
    val permission: String,
    val label: String,
    val granted: Boolean
)

data class IdentityPrivacyAppFinding(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val permissions: List<IdentityPermissionFinding>
)

data class IdentityPrivacyAuditReport(
    val androidIdAvailable: Boolean,
    val apps: List<IdentityPrivacyAppFinding>
)

/**
 * Read-only privacy audit. It never reads IMEI/serial and never changes device identifiers.
 * The Android ID check only determines whether Mora's app-scoped value is available;
 * the value itself is intentionally neither returned nor persisted.
 */
object IdentityPrivacyAudit {
    private data class PermissionDefinition(val name: String, val label: String)

    private val trackedPermissions = listOf(
        PermissionDefinition(android.Manifest.permission.READ_PHONE_STATE, "Status telepon"),
        PermissionDefinition("android.permission.READ_BASIC_PHONE_STATE", "Status telepon dasar"),
        PermissionDefinition("android.permission.READ_PRIVILEGED_PHONE_STATE", "Status telepon istimewa"),
        PermissionDefinition(android.Manifest.permission.READ_PHONE_NUMBERS, "Nomor telepon"),
        PermissionDefinition(android.Manifest.permission.READ_SMS, "SMS"),
        PermissionDefinition(android.Manifest.permission.READ_CALL_LOG, "Riwayat panggilan"),
        PermissionDefinition(android.Manifest.permission.READ_CONTACTS, "Kontak")
    )

    internal fun collectPermissionFindings(
        requestedPermissions: Array<String>?,
        requestedPermissionFlags: IntArray?
    ): List<IdentityPermissionFinding> {
        if (requestedPermissions.isNullOrEmpty()) return emptyList()

        return trackedPermissions.mapNotNull { definition ->
            val index = requestedPermissions.indexOf(definition.name)
            if (index < 0) {
                null
            } else {
                val flags = requestedPermissionFlags?.getOrNull(index) ?: 0
                IdentityPermissionFinding(
                    permission = definition.name,
                    label = definition.label,
                    granted = flags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                )
            }
        }
    }

    fun scan(context: Context): IdentityPrivacyAuditReport {
        val androidIdAvailable = runCatching {
            !Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ).isNullOrBlank()
        }.getOrDefault(false)

        val appFindings = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .mapNotNull { packageInfo ->
                    val permissions = collectPermissionFindings(
                        packageInfo.requestedPermissions,
                        packageInfo.requestedPermissionsFlags
                    )
                    if (permissions.isEmpty()) {
                        null
                    } else {
                        val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                        val label = runCatching {
                            context.packageManager.getApplicationLabel(appInfo).toString()
                        }.getOrDefault(packageInfo.packageName)
                        val systemFlags = ApplicationInfo.FLAG_SYSTEM or
                            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                        IdentityPrivacyAppFinding(
                            label = label,
                            packageName = packageInfo.packageName,
                            isSystemApp = appInfo.flags and systemFlags != 0,
                            permissions = permissions
                        )
                    }
                }
                .sortedWith(
                    compareBy<IdentityPrivacyAppFinding> { it.isSystemApp }
                        .thenByDescending { app -> app.permissions.count { it.granted } }
                        .thenBy { it.label.lowercase() }
                )
                .toList()
        }.getOrDefault(emptyList())

        return IdentityPrivacyAuditReport(
            androidIdAvailable = androidIdAvailable,
            apps = appFindings
        )
    }

    fun openApplicationSettings(context: Context, packageName: String): Boolean {
        if (!packageName.matches(Regex("[A-Za-z0-9_.]+"))) return false
        return runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun openPrivacySettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_PRIVACY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
