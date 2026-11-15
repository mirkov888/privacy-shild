package com.mora.shield

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import com.mora.shield.Root

data class Phase15Permission(
    val permission: String,
    val label: String,
    val group: String,
    val granted: Boolean
)

data class Phase15App(
    val packageName: String,
    val label: String
)

/**
 * Per-app privacy controls. Every root command is built only from validated package,
 * permission, AppOp and mode values. No clipboard or sensor payload is ever read.
 */
object PerAppPrivacyManager {
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val permissionPattern = Regex("^android\\.permission\\.[A-Z0-9_]+$")
    private val allowedPermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.SEND_SMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    )
    private val allowedOps = setOf(
        "READ_CLIPBOARD", "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND",
        "CAMERA", "RECORD_AUDIO", "COARSE_LOCATION", "FINE_LOCATION",
        "READ_CONTACTS", "WRITE_CONTACTS", "READ_SMS", "RECEIVE_SMS", "SEND_SMS"
    )
    private val protectedPackages = setOf(
        "com.mora.shield", "android", "com.android.systemui", "com.android.settings",
        "com.android.phone", "com.android.server.telecom", "com.android.launcher3",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.providers.settings", "com.android.vpndialogs"
    )
    private val permissionSpecs = listOf(
        Triple("android.permission.CAMERA", "Kamera", "Kamera"),
        Triple("android.permission.RECORD_AUDIO", "Mikrofon", "Mikrofon"),
        Triple("android.permission.ACCESS_FINE_LOCATION", "Lokasi presisi", "Lokasi"),
        Triple("android.permission.ACCESS_COARSE_LOCATION", "Lokasi perkiraan", "Lokasi"),
        Triple("android.permission.ACCESS_BACKGROUND_LOCATION", "Lokasi latar belakang", "Lokasi"),
        Triple("android.permission.READ_CONTACTS", "Baca kontak", "Kontak"),
        Triple("android.permission.WRITE_CONTACTS", "Ubah kontak", "Kontak"),
        Triple("android.permission.READ_SMS", "Baca SMS", "SMS"),
        Triple("android.permission.RECEIVE_SMS", "Terima SMS", "SMS"),
        Triple("android.permission.RECEIVE_MMS", "Terima MMS", "SMS"),
        Triple("android.permission.RECEIVE_WAP_PUSH", "Terima WAP push", "SMS"),
        Triple("android.permission.SEND_SMS", "Kirim SMS", "SMS"),
        Triple("android.permission.READ_EXTERNAL_STORAGE", "Baca penyimpanan lama", "Penyimpanan"),
        Triple("android.permission.WRITE_EXTERNAL_STORAGE", "Tulis penyimpanan lama", "Penyimpanan"),
        Triple("android.permission.READ_MEDIA_IMAGES", "Baca foto", "Penyimpanan"),
        Triple("android.permission.READ_MEDIA_VIDEO", "Baca video", "Penyimpanan"),
        Triple("android.permission.READ_MEDIA_AUDIO", "Baca audio", "Penyimpanan"),
        Triple("android.permission.READ_MEDIA_VISUAL_USER_SELECTED", "Foto pilihan pengguna", "Penyimpanan")
    )

    fun isSafePackage(packageName: String): Boolean =
        packagePattern.matches(packageName) && packageName !in protectedPackages &&
            !packageName.startsWith("android.")

    fun isAllowedPermission(permission: String): Boolean =
        permissionPattern.matches(permission) && permission in allowedPermissions

    fun isAllowedOp(op: String): Boolean = op in allowedOps

    fun isAllowedFirewallMode(mode: String): Boolean = mode in setOf("none", "wifi", "mobile", "both")

    fun listApps(context: Context): List<Phase15App> {
        val pm = context.packageManager
        val protected = protectedTargets(context)
        @Suppress("DEPRECATION")
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { isSafePackage(it.packageName) && it.packageName !in protected }
            .map {
                Phase15App(it.packageName, runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName))
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun protectedTargets(context: Context): Set<String> {
        val pm = context.packageManager
        val result = protectedPackages.toMutableSet()
        runCatching {
            pm.queryIntentServices(Intent("com.mora.shield.action.CONNECTOR"), PackageManager.MATCH_ALL)
                .mapNotNullTo(result) { it.serviceInfo?.packageName }
        }
        runCatching {
            pm.queryIntentServices(Intent("android.net.VpnService"), PackageManager.MATCH_ALL)
                .mapNotNullTo(result) { it.serviceInfo?.packageName }
        }
        return result
    }

    fun permissions(context: Context, packageName: String): List<Phase15Permission> {
        if (!isSafePackage(packageName) || packageName in protectedTargets(context)) return emptyList()
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val requested = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        }.getOrDefault(emptySet())
        return permissionSpecs.filter { it.first in requested }.map { spec ->
            Phase15Permission(
                spec.first, spec.second, spec.third,
                pm.checkPermission(spec.first, packageName) == PackageManager.PERMISSION_GRANTED
            )
        }
    }

    fun setPermission(packageName: String, permission: String, granted: Boolean): Root.Result {
        if (!isSafePackage(packageName) || !isAllowedPermission(permission)) {
            return Root.Result(2, "Paket atau izin tidak diizinkan")
        }
        val verb = if (granted) "grant" else "revoke"
        return Root.sh("pm $verb --user 0 '$packageName' '$permission'")
    }

    fun appOpMode(packageName: String, op: String): String {
        if (!isSafePackage(packageName) || !isAllowedOp(op)) return "unavailable"
        val r = Root.sh("cmd appops get '$packageName' '$op' 2>&1")
        if (!r.ok && r.out.contains("Unknown operation", true)) return "unsupported"
        val text = r.out.lowercase()
        return when {
            Regex("(^|\\s|:)ignore([;\\s]|$)").containsMatchIn(text) -> "ignore"
            Regex("(^|\\s|:)deny([;\\s]|$)").containsMatchIn(text) -> "deny"
            Regex("(^|\\s|:)allow([;\\s]|$)").containsMatchIn(text) -> "allow"
            Regex("(^|\\s|:)foreground([;\\s]|$)").containsMatchIn(text) -> "foreground"
            text.contains("default") -> "default"
            r.ok -> "default"
            else -> "unsupported"
        }
    }

    fun setAppOp(context: Context, packageName: String, op: String, mode: String): Root.Result {
        if (!isSafePackage(packageName) || packageName in protectedTargets(context) ||
            !isAllowedOp(op) || mode !in setOf("ignore", "default", "allow")) {
            return Root.Result(2, "Target AppOps tidak diizinkan")
        }
        val prefs = context.getSharedPreferences("phase15_restore", Context.MODE_PRIVATE)
        val key = "op:$packageName:$op"
        if (!prefs.contains(key)) {
            val original = appOpMode(packageName, op)
            if (original == "unsupported") return Root.Result(2, "AppOp $op tidak didukung ROM ini")
            prefs.edit().putString(key, original).apply()
        }
        val result = Root.sh("cmd appops set '$packageName' '$op' '$mode'")
        if (!result.ok) prefs.edit().remove(key).apply()
        return result
    }

    fun restoreAppOps(context: Context, packageName: String, ops: List<String>): Root.Result {
        if (!isSafePackage(packageName) || packageName in protectedTargets(context) || ops.any { !isAllowedOp(it) }) {
            return Root.Result(2, "Target AppOps restore tidak diizinkan")
        }
        val prefs = context.getSharedPreferences("phase15_restore", Context.MODE_PRIVATE)
        val errors = mutableListOf<String>()
        for (op in ops) {
            val key = "op:$packageName:$op"
            if (!prefs.contains(key)) continue
            val mode = prefs.getString(key, "default") ?: "default"
            val result = if (mode in setOf("ignore", "deny", "allow", "foreground", "default")) {
                Root.sh("cmd appops set '$packageName' '$op' '$mode'")
            } else Root.Result(2, "Snapshot AppOps tidak valid")
            if (result.ok) prefs.edit().remove(key).apply() else errors += result.out
        }
        return if (errors.isEmpty()) Root.Result(0, "AppOps dikembalikan ke nilai awal")
        else Root.Result(1, errors.joinToString("; ").take(180))
    }

    fun setPermissionWithSnapshot(context: Context, packageName: String, permission: String, granted: Boolean): Root.Result {
        if (!isSafePackage(packageName) || packageName in protectedTargets(context) ||
            !isAllowedPermission(permission)) return Root.Result(2, "Target izin tidak diizinkan")
        val prefs = context.getSharedPreferences("phase15_restore", Context.MODE_PRIVATE)
        val key = "perm:$packageName:$permission"
        if (!prefs.contains(key)) {
            val original = context.packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean(key, original).apply()
        }
        val result = setPermission(packageName, permission, granted)
        if (!result.ok) prefs.edit().remove(key).apply()
        return result
    }

    fun firewallMode(packageName: String, config: Map<String, String>): String {
        fun contains(key: String) = (config[key] ?: "").split(",").any { it == packageName }
        return when {
            contains("fw_pkgs") -> "both"
            contains("fw_wifi") && contains("fw_mobile") -> "both"
            contains("fw_wifi") -> "wifi"
            contains("fw_mobile") -> "mobile"
            else -> "none"
        }
    }

    fun setFirewall(context: Context, packageName: String, mode: String, config: Map<String, String>): Root.Result {
        if (!isSafePackage(packageName) || packageName in protectedTargets(context) || !isAllowedFirewallMode(mode)) {
            return Root.Result(2, "Firewall menolak target sistem/VPN/connector atau mode tidak valid")
        }
        val prefs = context.getSharedPreferences("phase15_restore", Context.MODE_PRIVATE)
        val key = "fw:$packageName"
        if (!prefs.contains(key)) prefs.edit().putString(key, firewallMode(packageName, config)).apply()
        val result = Ps.run("fwset $packageName $mode")
        if (!result.ok) prefs.edit().remove(key).apply()
        return result
    }

    fun sensorMetadata(packageName: String): Root.Result {
        if (!isSafePackage(packageName)) return Root.Result(2, "Paket tidak valid")
        val result = Root.sh("cmd appops get '$packageName' 2>&1")
        if (!result.ok) return result
        val relevant = result.out.lineSequence().filter { line ->
            val s = line.lowercase()
            listOf("camera", "record_audio", "microphone", "location", "contacts", "sms", "body_sensors", "clipboard").any { it in s }
        }.take(40).joinToString("\n")
        return Root.Result(0, relevant.ifBlank { "Belum ada metadata AppOps relevan yang dikembalikan ROM." })
    }

    fun restore(context: Context, packageName: String, config: Map<String, String>): Root.Result {
        if (!isSafePackage(packageName) || packageName in protectedTargets(context)) {
            return Root.Result(2, "Target restore dilindungi")
        }
        val prefs = context.getSharedPreferences("phase15_restore", Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith("op:$packageName:") || it.startsWith("perm:$packageName:") || it == "fw:$packageName" }
        val errors = mutableListOf<String>()
        for (key in keys) {
            val result = when {
                key.startsWith("op:$packageName:") -> {
                    val op = key.removePrefix("op:$packageName:")
                    val mode = prefs.getString(key, "default") ?: "default"
                    if (isAllowedOp(op) && mode in setOf("ignore", "deny", "allow", "foreground", "default")) {
                        Root.sh("cmd appops set '$packageName' '$op' '$mode'")
                    } else Root.Result(2, "Data AppOps restore tidak valid")
                }
                key.startsWith("perm:$packageName:") -> {
                    val permission = key.removePrefix("perm:$packageName:")
                    val wasGranted = prefs.getBoolean(key, false)
                    if (isAllowedPermission(permission)) setPermission(packageName, permission, wasGranted)
                    else Root.Result(2, "Data izin restore tidak valid")
                }
                else -> {
                    val mode = prefs.getString(key, "none") ?: "none"
                    if (isAllowedFirewallMode(mode)) Ps.run("fwset $packageName $mode")
                    else Root.Result(2, "Data firewall restore tidak valid")
                }
            }
            if (result.ok) prefs.edit().remove(key).apply() else errors += result.out.take(100)
        }
        return if (errors.isEmpty()) Root.Result(0, "Semua perubahan Phase 15 dipulihkan untuk $packageName")
        else Root.Result(1, "Sebagian pemulihan gagal: " + errors.joinToString("; "))
    }
}
