package com.mora.shield.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

internal class AppRegistry(
    private val context: Context,
    private val dao: CoreDao
) {
    suspend fun seedKnownApps() {
        val apps = listOf(
            "com.mora.shield" to "Mora Shield",
            "com.mora.nsfwdownloader" to "MoraDownloader",
            "com.mora.systemmanager" to "MoraSystemManager"
        )
        val now = System.currentTimeMillis()
        for ((packageName, fallbackName) in apps) {
            val appInfo = try {
                context.packageManager.getApplicationInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val displayName = if (appInfo != null) {
                context.packageManager.getApplicationLabel(appInfo).toString().ifBlank { fallbackName }
            } else fallbackName
            val versionName = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
            } catch (_: Exception) {
                "not-installed"
            }
            val certDigest = if (appInfo == null) "" else certificateSha256(packageName)
            val previous = dao.getApp(packageName)
            val state = when {
                packageName == context.packageName -> "INTERNAL"
                appInfo == null -> "NOT_INSTALLED"
                // A registry refresh for another companion must not silently disconnect this one.
                previous?.connectionState == "CONNECTED" -> "CONNECTED"
                else -> "INSTALLED"
            }
            dao.upsertApp(
                RegisteredAppEntity(
                    packageName = packageName,
                    displayName = displayName,
                    versionName = versionName,
                    apiVersion = previous?.apiVersion ?: 1,
                    connectionState = state,
                    capabilities = previous?.capabilities ?: "",
                    certificateSha256 = certDigest,
                    lastSeenAtMs = previous?.lastSeenAtMs ?: 0L,
                    updatedAtMs = now
                )
            )
            if (dao.getPolicy(packageName) == null) {
                // Safe defaults: monitoring on, command execution disabled, all commands require approval.
                dao.upsertPolicy(AppPolicyEntity(packageName = packageName, updatedAtMs = now))
            }
        }
    }

    suspend fun apps(): List<RegisteredAppEntity> = dao.getApps()

    suspend fun markConnected(
        principal: ConnectorPrincipal,
        apiVersion: Int,
        capabilities: Set<String>
    ): Boolean {
        if (!CoreSanitizer.validPackage(principal.packageName) || apiVersion != 1) return false
        val app = dao.getApp(principal.packageName) ?: return false
        if (app.packageName == context.packageName) return false
        // Never trust a connector unless its current APK signer matches the installed package.
        if (app.certificateSha256.isBlank() || !app.certificateSha256.equals(principal.certificateSha256, true)) return false
        val safeCapabilities = capabilities.filter { CoreSanitizer.validCommandId(it) }.toSet()
        val now = System.currentTimeMillis()
        dao.upsertApp(
            app.copy(
                connectionState = "CONNECTED",
                apiVersion = apiVersion,
                capabilities = safeCapabilities.sorted().joinToString("\n"),
                lastSeenAtMs = now,
                updatedAtMs = now
            )
        )
        return true
    }

    suspend fun heartbeat(principal: ConnectorPrincipal): Boolean {
        val app = dao.getApp(principal.packageName) ?: return false
        if (app.certificateSha256.isBlank() || !app.certificateSha256.equals(principal.certificateSha256, true)) return false
        return dao.updateConnection(principal.packageName, "CONNECTED", System.currentTimeMillis()) > 0
    }

    suspend fun markDisconnected(packageName: String) {
        dao.updateConnection(packageName, "INSTALLED", System.currentTimeMillis())
    }

    suspend fun verifiedApp(principal: ConnectorPrincipal): RegisteredAppEntity? {
        val app = dao.getApp(principal.packageName) ?: return null
        if (app.certificateSha256.isBlank() || !app.certificateSha256.equals(principal.certificateSha256, true)) return null
        return app
    }

    @Suppress("DEPRECATION")
    private fun certificateSha256(packageName: String): String {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageInfo(packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            } ?: return ""
            signatures.map { signature ->
                val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                digest.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            }.sorted().joinToString(":")
        } catch (_: Exception) {
            ""
        }
    }
}
