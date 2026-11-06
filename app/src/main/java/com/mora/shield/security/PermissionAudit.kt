package com.mora.shield.security

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager

object PermissionAudit {
    private val sensitive = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_CALENDAR
    )

    data class AppPermissions(val packageName: String, val label: String, val granted: List<String>, val lastUsedMs: Long?, val enabled: Boolean, val isSystem: Boolean)

    fun collect(context: Context): List<AppPermissions> {
        val pm = context.packageManager
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val usageByPackage = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 90L * 24 * 60 * 60 * 1000, now)
                .associate { it.packageName to it.lastTimeUsed }
        }.getOrDefault(emptyMap())
        return pm.getInstalledApplications(PackageManager.MATCH_ALL).mapNotNull { app ->
            val granted = sensitive.filter { permission ->
                runCatching { pm.checkPermission(permission, app.packageName) == PackageManager.PERMISSION_GRANTED &&
                    pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.contains(permission) == true
                }.getOrDefault(false)
            }
            if (granted.isEmpty()) null else AppPermissions(
                app.packageName,
                runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName),
                granted,
                usageByPackage[app.packageName],
                app.enabled,
                app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            )
        }.sortedByDescending { it.granted.size }
    }

}
