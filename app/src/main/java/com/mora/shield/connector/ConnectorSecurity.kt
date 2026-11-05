package com.mora.shield.connector

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.mora.shield.core.ConnectorPrincipal
import com.mora.shield.core.MoraCore
import java.security.MessageDigest

/** Caller identity is derived from the Binder Messenger UID, never from Bundle input. */
internal object ConnectorSecurity {
    suspend fun principalForUid(context: Context, uid: Int): ConnectorPrincipal? {
        if (uid < 0 || uid == Process.myUid()) return null
        val packages = context.packageManager.getPackagesForUid(uid)?.distinct().orEmpty()
        // Shared UIDs are rejected to avoid ambiguous package attribution.
        if (packages.size != 1) return null
        val packageName = packages.single()
        if (packageName == context.packageName) return null

        val registered = runCatching { MoraCore.registeredApps() }.getOrNull().orEmpty()
        if (registered.none { it.packageName == packageName }) return null

        val digest = certificateSha256(context, packageName) ?: return null
        return ConnectorPrincipal(packageName, digest)
    }

    @Suppress("DEPRECATION")
    private fun certificateSha256(context: Context, packageName: String): String? {
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
            } ?: return null
            if (signatures.isEmpty()) return null
            signatures.map { signature ->
                val hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                hash.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
            }.sorted().joinToString(":")
        } catch (_: Exception) {
            null
        }
    }
}
