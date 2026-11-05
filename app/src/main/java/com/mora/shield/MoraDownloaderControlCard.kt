package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.shield.connector.MoraConnectorService
import com.mora.shield.core.AppPolicy
import com.mora.shield.core.MoraCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DOWNLOADER_PACKAGE = "com.mora.nsfwdownloader"
private val DOWNLOADER_COMMANDS = listOf(
    "download.status.read",
    "download.start",
    "download.pause",
    "download.resume",
    "download.cancel"
)

/** Central policy and command surface for MoraDownloader. All controls are opt-in. */
@Composable
fun MoraDownloaderControlCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appState by remember { mutableStateOf("Memeriksa aplikasi…") }
    var policy by remember { mutableStateOf<AppPolicy?>(null) }
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        MoraCore.initialize(context.applicationContext)
        while (true) {
            runCatching {
                val registered = MoraCore.registeredApps().firstOrNull { it.packageName == DOWNLOADER_PACKAGE }
                appState = when (registered?.connectionState) {
                    "CONNECTED" -> "Terhubung"
                    "INSTALLED" -> "Terpasang · belum terhubung"
                    "NOT_INSTALLED" -> "Belum terpasang"
                    null -> "Belum terdaftar"
                    else -> registered.connectionState
                }
                policy = MoraCore.appPolicy(DOWNLOADER_PACKAGE)
            }.onFailure {
                appState = "Core belum siap"
            }
            delay(3000)
        }
    }

    val currentPolicy = policy
    SectionCard(
        "MoraDownloader · Central Control",
        "Atur monitoring, capability dan perintah Downloader dari Mora Shield. Perintah tetap ditolak sampai diizinkan di sini."
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Koneksi", style = MaterialTheme.typography.bodyMedium)
            Text(appState, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Divider()
        if (currentPolicy == null) {
            Text("Kebijakan sedang dimuat atau aplikasi belum terdaftar.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Monitoring aktivitas", style = MaterialTheme.typography.bodyMedium)
                    Text("Izinkan event download masuk ke timeline Shield", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = currentPolicy.monitoringEnabled,
                    onCheckedChange = { value -> updatePolicy(scope, currentPolicy.copy(monitoringEnabled = value)) { policy = it; result = "Monitoring diperbarui" } }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Akses perintah jarak jauh", style = MaterialTheme.typography.bodyMedium)
                    Text("Aktifkan hanya capability yang diperlukan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = currentPolicy.commandAccessEnabled,
                    onCheckedChange = { value -> updatePolicy(scope, currentPolicy.copy(commandAccessEnabled = value, approvalRequiredCommands = setOf("download.start", "download.cancel"))) { policy = it; result = if (value) "Akses perintah aktif" else "Akses perintah dimatikan" } }
                )
            }
            Text("Capability yang diizinkan", style = MaterialTheme.typography.titleSmall)
            DOWNLOADER_COMMANDS.forEach { command ->
                val checked = command in currentPolicy.allowedCommands
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { value ->
                            val allowed = if (value) currentPolicy.allowedCommands + command else currentPolicy.allowedCommands - command
                            updatePolicy(
                                scope,
                                currentPolicy.copy(
                                    allowedCommands = allowed,
                                    approvalRequiredCommands = setOf("download.start", "download.cancel")
                                )
                            ) { policy = it; result = "Capability diperbarui: $command" }
                        }
                    )
                    Text(command, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Start dan cancel selalu memerlukan persetujuan per tindakan. Pause/resume/status mengikuti capability yang kamu izinkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(240) },
                label = { Text("Filter pencarian opsional") },
                supportingText = { Text("Kosongkan untuk memakai konfigurasi channel Downloader yang sudah tersimpan.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !busy && canRun(currentPolicy, "download.status.read"),
                    onClick = { dispatch(scope, "download.status.read", "", false, { busy = it }, { result = it }) },
                    modifier = Modifier.weight(1f)
                ) { Text("Status") }
                Button(
                    enabled = !busy && canRun(currentPolicy, "download.start"),
                    onClick = { pendingCommand = "download.start" },
                    modifier = Modifier.weight(1f)
                ) { Text("Mulai") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !busy && canRun(currentPolicy, "download.pause"),
                    onClick = { dispatch(scope, "download.pause", "", true, { busy = it }, { result = it }) },
                    modifier = Modifier.weight(1f)
                ) { Text("Jeda") }
                Button(
                    enabled = !busy && canRun(currentPolicy, "download.resume"),
                    onClick = { dispatch(scope, "download.resume", "", true, { busy = it }, { result = it }) },
                    modifier = Modifier.weight(1f)
                ) { Text("Lanjut") }
                Button(
                    enabled = !busy && canRun(currentPolicy, "download.cancel"),
                    onClick = { pendingCommand = "download.cancel" },
                    modifier = Modifier.weight(1f)
                ) { Text("Batal") }
            }
        }
        if (busy) Text("Mengirim perintah…", style = MaterialTheme.typography.bodySmall)
        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val commandToConfirm = pendingCommand
    if (commandToConfirm != null) {
        val isStart = commandToConfirm == "download.start"
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text(if (isStart) "Mulai download dari MoraDownloader?" else "Batalkan download?") },
            text = { Text(if (isStart) "Downloader akan memakai channel dan konfigurasi yang sudah tersimpan. Filter pencarian yang diisi akan diteruskan ke Downloader." else "Pembatalan bersifat kooperatif. Transfer file yang sedang berlangsung dapat menyelesaikan file aktif terlebih dahulu.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCommand = null
                    val payload = if (isStart) JSONObject().put("query", query.trim()).put("channel", "").toString() else ""
                    dispatch(scope, commandToConfirm, payload, true, { busy = it }, { result = it })
                }) { Text(if (isStart) "Mulai" else "Batalkan") }
            },
            dismissButton = { TextButton(onClick = { pendingCommand = null }) { Text("Kembali") } }
        )
    }
}

private fun canRun(policy: AppPolicy, command: String): Boolean =
    policy.enabled && policy.commandAccessEnabled && command in policy.allowedCommands

private fun updatePolicy(
    scope: kotlinx.coroutines.CoroutineScope,
    next: AppPolicy,
    onDone: (AppPolicy) -> Unit
) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) { MoraCore.updateAppPolicy(next) }
        if (ok) onDone(next)
    }
}

private fun dispatch(
    scope: kotlinx.coroutines.CoroutineScope,
    command: String,
    payload: String,
    approvedByUser: Boolean,
    setBusy: (Boolean) -> Unit,
    setResult: (String) -> Unit
) {
    scope.launch {
        setBusy(true)
        val result = withContext(Dispatchers.IO) {
            MoraConnectorService.dispatchCommand(
                packageName = DOWNLOADER_PACKAGE,
                commandId = command,
                payload = payload,
                approvedByUser = approvedByUser
            )
        }
        setResult("${result.status}: ${result.summary}")
        setBusy(false)
    }
}
