package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.mora.shield.core.AppPolicy
import com.mora.shield.core.MoraCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SYSTEM_MANAGER_PACKAGE = "com.mora.systemmanager"

/** Policy-only controls. This card intentionally exposes no root/package command endpoint. */
@Composable
fun MoraSystemManagerPolicyCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("Memeriksa aplikasi…") }
    var policy by remember { mutableStateOf<AppPolicy?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        MoraCore.initialize(context.applicationContext)
        while (true) {
            runCatching {
                val app = MoraCore.registeredApps().firstOrNull { it.packageName == SYSTEM_MANAGER_PACKAGE }
                state = when (app?.connectionState) {
                    "CONNECTED" -> "Terhubung"
                    "INSTALLED" -> "Terpasang · belum terhubung"
                    "NOT_INSTALLED" -> "Belum terpasang"
                    null -> "Belum terdaftar"
                    else -> app.connectionState
                }
                policy = MoraCore.appPolicy(SYSTEM_MANAGER_PACKAGE)
            }.onFailure { state = "Core belum siap" }
            delay(3000)
        }
    }

    SectionCard(
        "MoraSystemManager · Central Policy",
        "Kebijakan monitoring, jaringan, dan aktivitas latar. Operasi Root/Shizuku tetap dijalankan dan dikonfirmasi dari aplikasi worker."
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Koneksi", style = MaterialTheme.typography.bodyMedium)
            Text(state, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        val current = policy
        if (current == null) {
            Text("Kebijakan belum tersedia. Pastikan MoraSystemManager terpasang dan pernah membuka aplikasi agar connector mendaftar.", style = MaterialTheme.typography.bodySmall)
        } else {
            PolicySwitch("Aktifkan kebijakan", "Master enable/disable untuk kebijakan worker", current.enabled, saving) { value ->
                savePolicy(scope, current.copy(enabled = value), { saving = it }, { policy = it; message = "Kebijakan aplikasi diperbarui" })
            }
            PolicySwitch("Monitoring event", "Izinkan event SystemManager masuk ke timeline Shield", current.monitoringEnabled, saving) { value ->
                savePolicy(scope, current.copy(monitoringEnabled = value), { saving = it }, { policy = it; message = "Monitoring diperbarui" })
            }
            Text("Mode jaringan", style = MaterialTheme.typography.titleSmall)
            PolicyChoices(
                selected = current.networkMode,
                options = listOf("inherit" to "Ikuti sistem", "allow" to "Izinkan", "block" to "Blokir", "wifi_only" to "Wi-Fi saja", "mobile_only" to "Seluler saja"),
                enabled = !saving
            ) { value -> savePolicy(scope, current.copy(networkMode = value), { saving = it }, { policy = it; message = "Mode jaringan diperbarui" }) }
            Text("Aktivitas latar", style = MaterialTheme.typography.titleSmall)
            PolicyChoices(
                selected = current.backgroundMode,
                options = listOf("normal" to "Normal", "restricted" to "Batasi", "allowed" to "Izinkan"),
                enabled = !saving
            ) { value -> savePolicy(scope, current.copy(backgroundMode = value), { saving = it }, { policy = it; message = "Kebijakan latar diperbarui" }) }
            Text("Catatan: pilihan jaringan/latar disimpan sebagai kebijakan pusat. Penerapan OS-level memerlukan handler khusus yang aman pada worker; kartu ini tidak menjalankan perintah shell/root.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (saving) Text("Menyimpan kebijakan…", style = MaterialTheme.typography.bodySmall)
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PolicySwitch(title: String, subtitle: String, checked: Boolean, disabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = !disabled)
    }
}

@Composable
private fun PolicyChoices(selected: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.forEach { (value, label) ->
                    FilterChip(selected = selected == value, onClick = { onSelect(value) }, enabled = enabled, label = { Text(label) })
                }
            }
        }
    }
}

private fun savePolicy(
    scope: kotlinx.coroutines.CoroutineScope,
    next: AppPolicy,
    setSaving: (Boolean) -> Unit,
    onSaved: (AppPolicy) -> Unit
) {
    scope.launch {
        setSaving(true)
        val ok = withContext(Dispatchers.IO) { runCatching { MoraCore.updateAppPolicy(next) }.getOrDefault(false) }
        if (ok) onSaved(next)
        setSaving(false)
    }
}
