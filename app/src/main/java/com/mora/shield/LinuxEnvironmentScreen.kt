package com.mora.shield

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class MoraDistro(val id: String, val name: String, val detail: String)
private val moraDistros = listOf(
    MoraDistro("debian", "Debian", "Stabil dan disarankan untuk instalasi pertama"),
    MoraDistro("ubuntu", "Ubuntu", "Kompatibilitas paket luas"),
    MoraDistro("archlinux", "Arch Linux", "Rolling release; pemeliharaan lebih aktif")
)

@Composable
fun LinuxEnvironmentScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mora_linux", Context.MODE_PRIVATE) }
    var selected by rememberSaveable { mutableStateOf("debian") }
    var refresh by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var termuxPresent by remember { mutableStateOf(TermuxBridge.installed(context)) }
    var runPermission by remember { mutableStateOf(TermuxBridge.hasRunPermission(context)) }
    val distro = moraDistros.first { it.id == selected }
    val managerReady = prefs.getBoolean("manager_ready", false)\n    val installed = prefs.getBoolean("installed_$selected", false)
    val session = prefs.getString("session_$selected", "unknown") ?: "unknown"
    val lastOutput = prefs.getString("last_output", "").orEmpty()
    val lastOperation = prefs.getString("last_operation", "").orEmpty()
    val lastError = prefs.getString("last_error", "").orEmpty()
    val lastExit = prefs.getInt("last_exit_code", -1)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        runPermission = TermuxBridge.hasRunPermission(context)
    }

    LaunchedEffect(selected, refresh) {
        termuxPresent = TermuxBridge.installed(context)
        runPermission = TermuxBridge.hasRunPermission(context)
        if (termuxPresent && runPermission && managerReady) {
            try { TermuxBridge.requestStatus(context, selected) } catch (_: Exception) { }
        }
    }
    LaunchedEffect(selected) {
        while (true) {
            delay(12000)
            refresh = (refresh + 1) % 100000
            runPermission = TermuxBridge.hasRunPermission(context)
            termuxPresent = TermuxBridge.installed(context)
        }
    }

    val action = confirm
    if (action != null) {
        val title = when (action) {
            "bootstrap" -> "Siapkan proot-distro di Termux?"\n            "install" -> "Instal distro ${distro.name}?"
            "desktop" -> "Instal XFCE di ${distro.name}?"
            "start" -> "Mulai sesi XFCE?"
            else -> "Hentikan sesi XFCE?"
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text("Perintah akan dikirim ke Termux setelah persetujuan ini. Instalasi desktop dapat memakai beberapa GB penyimpanan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    try {
                        when (action) {
                            "bootstrap" -> TermuxBridge.installManager(context)\n                            "install" -> TermuxBridge.installDistro(context, selected)
                            "desktop" -> TermuxBridge.installDesktop(context, selected)
                            "start" -> TermuxBridge.startDesktop(context, selected)
                            "stop" -> TermuxBridge.stopDesktop(context, selected)
                        }
                    } catch (e: Exception) {
                        prefs.edit().putString("last_error", e.message ?: "Gagal menjalankan perintah").apply()
                    }
                    refresh++
                }) { Text("Lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Mora Linux Environment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Kontrol distro Linux Android melalui Termux + proot-distro memakai API RUN_COMMAND resmi Termux.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (termuxPresent && runPermission) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Koneksi Termux", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(when {
                        !termuxPresent -> "Termux belum terpasang"
                        !runPermission -> "Termux terdeteksi · izin Run commands belum diberikan"
                        !managerReady -> "Termux terhubung · proot-distro belum disiapkan"\n                        else -> "Termux terhubung · kontrol langsung tersedia"
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (termuxPresent) {
                                context.packageManager.getLaunchIntentForPackage(TermuxBridge.TERMUX_PACKAGE)?.let { context.startActivity(it) }
                            } else {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases")))
                            }
                        }) { Text(if (termuxPresent) "Buka Termux" else "Unduh Termux") }
                        if (termuxPresent && !runPermission) {
                            Button(onClick = { permissionLauncher.launch(TermuxBridge.RUN_PERMISSION) }) { Text("Minta izin") }
                        }
                    }
                    if (termuxPresent && !runPermission) {
                        Text("Jika dialog tidak muncul: Info aplikasi Mora Shield → Izin → Izin tambahan → Run commands in Termux environment.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Di Termux, aktifkan allow-external-apps=true pada ~/.termux/termux.properties. Pengaturan keamanan ini tidak diubah otomatis.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Pilih distribusi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moraDistros.forEach { item ->
                    FilterChip(selected = selected == item.id, onClick = { selected = item.id }, label = { Text(item.name) })
                }
            }
            Text(distro.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status ${distro.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (installed) "● Terpasang" else "○ Belum terdeteksi terpasang", color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sesi XFCE: " + when (session) {
                        "running" -> "Berjalan"
                        "stopped" -> "Berhenti"
                        "start_requested" -> "Perintah start terkirim"
                        else -> "Belum diketahui"
                    })
                    OutlinedButton(enabled = termuxPresent && runPermission, onClick = { refresh++ }) { Text("Periksa status") }
                }
            }
        }
        item {\n            LinuxActionCard("Siapkan proot-distro", "Perbarui daftar paket Termux dan pasang pengelola distro Linux.", "Install proot-distro", termuxPresent && runPermission && !managerReady) { confirm = "bootstrap" }\n        }\n        item { Text("Kontrol distro & desktop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { LinuxActionCard("Install distro", "Unduh root filesystem ${distro.name}.", "Install ${distro.name}", termuxPresent && runPermission && managerReady && !installed) { confirm = "install" } }
        item { LinuxActionCard("Install XFCE + VNC", "Pasang XFCE, D-Bus, dan TigerVNC di dalam distro.", "Install desktop", termuxPresent && runPermission && managerReady && installed) { confirm = "desktop" } }
        item { LinuxActionCard("Start XFCE", "Mulai VNC display :1 pada localhost (127.0.0.1:5901).", "Start desktop", termuxPresent && runPermission && installed) { confirm = "start" } }
        item { LinuxActionCard("Stop XFCE", "Hentikan VNC display :1.", "Stop desktop", termuxPresent && runPermission && installed) { confirm = "stop" } }
        item {
            Text("Hasil perintah terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$lastOperation · exit code $lastExit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lastError.isNotBlank()) Text(lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (lastOutput.isNotBlank()) Text(lastOutput.takeLast(1800), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("PRoot berbagi kernel Android, bukan VM/kernel terpisah. systemd, Docker, modul kernel, dan akselerasi GPU penuh tidak dijamin. Izinkan Termux berjalan di latar belakang agar sesi tidak mudah dihentikan HyperOS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinuxActionCard(title: String, detail: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, enabled = enabled) { Text(action) }
        }
    }
}
