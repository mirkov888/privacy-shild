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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    val clipboard = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("mora_linux", Context.MODE_PRIVATE) }
    var selected by rememberSaveable { mutableStateOf("debian") }
    var refresh by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var termuxPresent by remember { mutableStateOf(TermuxBridge.installed(context)) }
    var x11AppPresent by remember { mutableStateOf(TermuxBridge.x11AppInstalled(context)) }
    var runPermission by remember { mutableStateOf(TermuxBridge.hasRunPermission(context)) }
    val distro = moraDistros.first { it.id == selected }
    val managerReady = prefs.getBoolean("manager_ready", false)
    val installed = prefs.getBoolean("installed_$selected", false)
    val rootfsBroken = prefs.getBoolean("rootfs_broken_$selected", false)
    val desktopReady = prefs.getBoolean("desktop_ready_$selected", false)
    val x11PackageReady = prefs.getBoolean("x11_package_ready", false)
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
        x11AppPresent = TermuxBridge.x11AppInstalled(context)
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
            x11AppPresent = TermuxBridge.x11AppInstalled(context)
        }
    }

    val action = confirm
    if (action != null) {
        val title = when (action) {
            "bootstrap" -> "Siapkan proot-distro di Termux?"
            "install" -> "Instal distro ${distro.name}?"
            "repair" -> "Perbaiki instalasi ${distro.name}?"
            "desktop" -> "Instal XFCE di ${distro.name}?"
            "x11_package" -> "Pasang komponen Termux:X11?"
            "start" -> "Mulai desktop XFCE lewat Termux:X11?"
            else -> "Hentikan desktop XFCE?"
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = {
                Text(when (action) {
                    "repair" -> "Pemulihan akan menghapus seluruh data di dalam distro ${distro.name}, lalu mengunduh ulang rootfs. File di luar distro tidak dihapus. Pastikan data penting sudah dicadangkan."
                    "install" -> "Mora Shield akan mengunduh dan memasang rootfs ${distro.name}. Jika koneksi terputus, aplikasi akan mencoba ulang hingga 3 kali."
                    else -> "Perintah akan dikirim ke Termux setelah persetujuan ini. Instalasi desktop dapat memakai beberapa GB penyimpanan."
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    try {
                        when (action) {
                            "bootstrap" -> TermuxBridge.installManager(context)
                            "install" -> TermuxBridge.installDistro(context, selected)
                            "repair" -> TermuxBridge.repairDistro(context, selected)
                            "desktop" -> TermuxBridge.installDesktop(context, selected)
                            "password" -> TermuxBridge.setupVncPassword(context, selected)
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
                        !managerReady -> "Termux terhubung · proot-distro belum disiapkan"
                        else -> "Termux terhubung · kontrol langsung tersedia"
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
                    Text(if (installed) "● Terdaftar" else "○ Belum terpasang", color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when {
                        rootfsBroken -> "Rootfs rusak / tidak lengkap · perlu pemulihan"
                        installed -> "Rootfs valid · shell dan os-release ditemukan"
                        else -> "Rootfs belum tersedia"
                    }, color = if (rootfsBroken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (desktopReady) "XFCE: siap" else "XFCE: belum dipasang")
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
        item {
            LinuxActionCard(
                "Siapkan proot-distro",
                if (managerReady) "proot-distro sudah ditandai siap. Jalankan ulang pemeriksaan/perbaikan jika instalasi sebelumnya bermasalah."
                else "Perbarui daftar paket Termux dan pasang pengelola distro Linux.",
                if (managerReady) "Periksa / perbaiki proot-distro" else "Install proot-distro",
                termuxPresent && runPermission
            ) { confirm = "bootstrap" }
        }
        item { Text("Kontrol distro & desktop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { LinuxActionCard("Install distro", "Unduh root filesystem ${distro.name}. Mora Shield akan mencoba ulang unduhan yang gagal.", "Install ${distro.name}", termuxPresent && runPermission && managerReady && !installed) { confirm = "install" } }
        item {
            if (rootfsBroken) {
                LinuxActionCard("Pulihkan rootfs", "Rootfs terdaftar tetapi file sistem penting tidak ditemukan. Instalasi ulang otomatis akan memperbaikinya.", "Perbaiki ${distro.name}", termuxPresent && runPermission && managerReady) { confirm = "repair" }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tampilan desktop · Termux:X11", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !x11AppPresent -> "Aplikasi pendamping Termux:X11 belum terdeteksi di Android."
                            !x11PackageReady -> "Aplikasi terpasang · komponen perintah Termux:X11 belum terdeteksi."
                            else -> "Termux:X11 siap digunakan untuk menampilkan XFCE dari Debian."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (x11AppPresent) TermuxBridge.openX11(context)
                            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-x11/releases/tag/nightly")))
                        }) { Text(if (x11AppPresent) "Buka Termux:X11" else "Install aplikasi X11") }
                        if (x11AppPresent && termuxPresent && runPermission && !x11PackageReady) {
                            Button(onClick = { confirm = "x11_package" }) { Text("Install komponen") }
                        }
                    }
                    Text("Keduanya diperlukan: aplikasi Termux:X11 di Android dan paket termux-x11-nightly di Termux. Setelah pemasangan paket, tekan Periksa status.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { LinuxActionCard("Install XFCE", "Pasang XFCE dan D-Bus di dalam Debian. TigerVNC tidak diperlukan.", "Install desktop", termuxPresent && runPermission && managerReady && installed && !desktopReady) { confirm = "desktop" } }
        item { LinuxActionCard("Start Desktop", "Nyalakan server Termux:X11, buka aplikasinya, lalu jalankan sesi XFCE dari Debian melalui DISPLAY=:0.", "Start desktop", termuxPresent && runPermission && installed && desktopReady && x11AppPresent && x11PackageReady) { confirm = "start" } }
        item { LinuxActionCard("Stop Desktop", "Hentikan sesi XFCE di Debian. Server Termux:X11 tidak dipaksa ditutup agar aman untuk aplikasi X11 lain.", "Stop desktop", termuxPresent && runPermission && installed && desktopReady) { confirm = "stop" } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Diagnostik / hasil perintah", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("$lastOperation · exit code $lastExit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lastError.isNotBlank()) {
                        Text("Error", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        Text(lastError.takeLast(3000), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (lastOutput.isNotBlank()) {
                        Text("Output", style = MaterialTheme.typography.labelMedium)
                        Text(lastOutput.takeLast(4000), style = MaterialTheme.typography.bodySmall)
                    }
                    val diagnosticText = buildString {
                        appendLine("Mora Shield / Termux diagnostic")
                        appendLine("Operation: $lastOperation")
                        appendLine("Exit code: $lastExit")
                        appendLine("Error:")
                        appendLine(lastError.ifBlank { "(none captured)" })
                        appendLine("Output:")
                        appendLine(lastOutput.ifBlank { "(empty)" })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = lastOperation.isNotBlank() || lastError.isNotBlank() || lastOutput.isNotBlank(),
                            onClick = { clipboard.setText(AnnotatedString(diagnosticText)) }
                        ) { Text("Salin error & output") }
                        OutlinedButton(
                            enabled = lastError.isNotBlank(),
                            onClick = { clipboard.setText(AnnotatedString(lastError)) }
                        ) { Text("Salin error saja") }
                    }
                    if (lastOperation.isBlank() && lastError.isBlank() && lastOutput.isBlank()) {
                        Text("Belum ada hasil perintah. Jalankan aksi lagi setelah Termux siap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
