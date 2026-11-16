package com.mora.shield.identity

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.webkit.WebView
import androidx.webkit.WebViewFeature
import com.mora.shield.Root

data class IdentityBrowserTarget(
    val label: String,
    val packageName: String
)

data class IdentityAndroidIntegrationReport(
    val webViewProvider: String,
    val webViewVersion: String,
    val userAgentMetadataSupported: Boolean,
    val documentStartScriptSupported: Boolean,
    val browsers: List<IdentityBrowserTarget>,
    val rootGranted: Boolean,
    val rootManager: String,
    val lsPosedStatus: String
)

/**
 * Read-only capability checks for supported Android integrations.
 * External browsers are discovered, never modified or injected into.
 */
object IdentityAndroidIntegration {
    fun scan(context: Context): IdentityAndroidIntegrationReport {
        val webViewPackage = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        val providerName = webViewPackage?.packageName ?: "Provider WebView belum diketahui"
        val providerVersion = webViewPackage?.versionName ?: "Tidak tersedia"

        val browsers = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org"))
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    val pkg = activity.packageName
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = runCatching {
                        context.packageManager.getApplicationLabel(activity.applicationInfo).toString()
                    }.getOrDefault(pkg)
                    IdentityBrowserTarget(label, pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())

        // This command only reads root UID and known manager/module footprints.
        // It does not install, enable, patch, or change any module or device property.
        val rootProbe = runCatching { Root.sh("id -u") }.getOrNull()
        val rootGranted = rootProbe?.ok == true && rootProbe.out.trim() == "0"
        val footprints = if (rootGranted) {
            runCatching {
                Root.sh(
                    "command -v magisk >/dev/null 2>&1 && echo MAGISK; " +
                        "command -v ksud >/dev/null 2>&1 && echo KERNELSU; " +
                        "[ -d /data/adb/ksu ] && echo KERNELSU; " +
                        "[ -d /data/adb/modules/zygisk_lsposed ] && echo LSPOSED; " +
                        "[ -d /data/adb/modules/riru_lsposed ] && echo LSPOSED; " +
                        "[ -d /data/adb/lspd ] && echo LSPOSED"
                ).out
            }.getOrDefault("")
        } else ""

        val managers = buildList {
            if ("MAGISK" in footprints) add("Magisk")
            if ("KERNELSU" in footprints) add("KernelSU")
        }.distinct()
        val hookDetected = "LSPOSED" in footprints

        return IdentityAndroidIntegrationReport(
            webViewProvider = providerName,
            webViewVersion = providerVersion,
            userAgentMetadataSupported = runCatching {
                WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
            }.getOrDefault(false),
            documentStartScriptSupported = runCatching {
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            }.getOrDefault(false),
            browsers = browsers,
            rootGranted = rootGranted,
            rootManager = when {
                !rootGranted -> "Akses root belum tersedia atau belum diizinkan"
                managers.isEmpty() -> "Root aktif; Magisk/KernelSU tidak teridentifikasi"
                else -> managers.joinToString(" + ")
            },
            lsPosedStatus = when {
                !rootGranted -> "Status LSPosed belum dapat diperiksa"
                hookDetected -> "Jejak modul LSPosed ditemukan; status aktif tetap perlu diverifikasi di Manager"
                else -> "Jejak modul LSPosed tidak ditemukan"
            }
        )
    }

    fun openWebViewSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_WEBVIEW_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
