package com.mora.shield

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.shield.core.AppPolicy
import com.mora.shield.core.MoraCore
import com.mora.shield.core.RegisteredAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIELD_PACKAGE = "com.mora.shield"
private const val DOWNLOADER_PACKAGE = "com.mora.nsfwdownloader"
private const val SYSTEM_MANAGER_PACKAGE = "com.mora.systemmanager"

@Composable
fun MoraCoreScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        MoraCoreCard()
        MoraCoreRegistryCard()
        MoraDownloaderControlCard()
        MoraSystemManagerPolicyCard()
        MoraCoreCommandAuditCard()
    }
}

@Composable
private fun MoraCoreRegistryCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<RegisteredAppEntity>>(emptyList()) }
    var policies by remember { mutableStateOf<Map<String, AppPolicy>>(emptyMap()) }
    var saving by remember { mutableStateOf(setOf<String>()) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        MoraCore.initialize(context.applicationContext)
        delay(350)
        runCatching { MoraCore.refreshRegistry() }
        while (true) {
            runCatching {
                val currentApps = MoraCore.registeredApps()
                val currentPolicies = currentApps.mapNotNull { app ->
                    MoraCore.appPolicy(app.packageName)?.let { app.packageName to it }
                }.toMap()
                apps = currentApps
                policies = currentPolicies
            }
            delay(5000)
        }
    }

    fun save(next: AppPolicy) {
        scope.launch {
            saving = saving + next.packageName
            val ok = withContext(Dispatchers.IO) {
                runCatching { MoraCore.updateAppPolicy(next) }.getOrDefault(false)
            }
            if (ok) {
                policies = policies + (next.packageName to next)
                message = "Kebijakan ${next.packageName} tersimpan."
            } else message = "Gagal menyimpan kebijakan ${next.packageName}."
            saving = saving - next.packageName
        }
    }

    SectionCard("App Registry · Mora Ecosystem", "Status koneksi, izin komunikasi, dan kebijakan tiap aplikasi.") {
        if (apps.isEmpty()) EmptyHint("Registry sedang dimuat.")
        apps.forEachIndexed { index, app ->
            val policy = policies[app.packageName]
            val isSaving = app.packageName in saving
            if (index > 0) Divider()
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(app.connectionState, style = MaterialTheme.typography.labelLarge)
                }
                Text(statusDescription(app.connectionState), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Versi ${app.versionName} · API ${app.apiVersion}" +
                        if (app.lastSeenAtMs > 0L) " · Aktif ${java.text.DateFormat.getTimeInstance().format(java.util.Date(app.lastSeenAtMs))}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (policy == null) {
                    Text("Memuat kebijakan…", style = MaterialTheme.typography.bodySmall)
                } else {
                    RegistrySwitch("Kebijakan aplikasi aktif", "Master switch kebijakan aplikasi", policy.enabled, isSaving) {
                        save(policy.copy(enabled = it))
                    }
                    if (app.packageName != DOWNLOADER_PACKAGE && app.packageName != SYSTEM_MANAGER_PACKAGE) {
                        RegistrySwitch("Monitoring aktivitas", "Izinkan event masuk ke timeline Mora Core", policy.monitoringEnabled, isSaving) {
                            save(policy.copy(monitoringEnabled = it))
                        }
                    }
                    if (app.packageName == SYSTEM_MANAGER_PACKAGE && app.connectionState != "NOT_INSTALLED") {
                        val caps = app.capabilities.split('\n').map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                        val canEnable = app.connectionState == "CONNECTED" && caps.isNotEmpty()
                        RegistrySwitch(
                            "Akses perintah connector",
                            "Default mati; hanya capability yang dipilih yang dapat dijalankan",
                            policy.commandAccessEnabled,
                            isSaving || !canEnable
                        ) {
                            save(policy.copy(commandAccessEnabled = it, approvalRequiredCommands = setOf("*")))
                        }
                        if (caps.isEmpty()) {
                            Text(
                                if (app.connectionState == "CONNECTED") "Connector belum mengiklankan capability." else "Capability muncul setelah connector terhubung.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Capability allowlist", style = MaterialTheme.typography.labelLarge)
                            caps.forEach { capability ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = capability in policy.allowedCommands,
                                        enabled = !isSaving,
                                        onCheckedChange = { checked ->
                                            val allowed = if (checked) policy.allowedCommands + capability else policy.allowedCommands - capability
                                            save(policy.copy(allowedCommands = allowed, approvalRequiredCommands = setOf("*")))
                                        }
                                    )
                                    Text(capability, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(
                            "Semua perintah connector tetap memerlukan persetujuan pengguna. Tidak ada remote shell bebas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RegistrySwitch(title: String, subtitle: String, checked: Boolean, disabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = !disabled)
    }
}

private fun statusDescription(value: String): String = when (value) {
    "INTERNAL" -> "Komponen internal Mora Shield; tidak membutuhkan konektor eksternal."
    "NOT_INSTALLED" -> "Aplikasi belum terpasang."
    "INSTALLED" -> "APK terpasang, tetapi belum memiliki sesi connector aktif."
    "CONNECTED" -> "Sesi connector terautentikasi dan heartbeat aktif."
    else -> "Status belum diketahui."
}

@Composable
private fun MoraCoreCommandAuditCard() {
    var rows by remember { mutableStateOf(emptyList<com.mora.shield.core.CommandAuditEntity>()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MoraCore.initialize(context.applicationContext)
        while (true) {
            rows = runCatching { MoraCore.recentCommandAudit(30) }.getOrDefault(emptyList())
            delay(5000)
        }
    }
    SectionCard("Command Audit", "Riwayat perintah connector yang disimpan lokal.") {
        if (rows.isEmpty()) EmptyHint("Belum ada perintah connector yang tercatat.")
        rows.take(15).forEachIndexed { index, item ->
            if (index > 0) Divider()
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${item.commandId} · ${item.status}", style = MaterialTheme.typography.bodyMedium)
                Text(item.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.resultSummary, style = MaterialTheme.typography.bodySmall)
                Text(
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.createdAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
