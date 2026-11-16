package com.mora.shield

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class LinuxDistro(
    val id: String,
    val title: String,
    val family: String,
    val note: String
)

private val moraLinuxDistros = listOf(
    LinuxDistro("debian", "Debian", "apt", "Pilihan awal yang ringan dan stabil"),
    LinuxDistro("ubuntu", "Ubuntu", "apt", "Kompatibilitas paket luas; butuh ruang lebih"),
    LinuxDistro("archlinux", "Arch Linux", "pacman", "Rolling release; perlu pemeliharaan lebih aktif")
)

@Composable
fun LinuxEnvironmentScreen() {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf("debian") }
    val distro = moraLinuxDistros.firstOrNull { it.id == selectedId } ?: moraLinuxDistros.first()
    val termuxInstalled = remember {
        try {
            context.packageManager.getPackageInfo("com.termux", PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }

    val installCommand = "pkg update -y && pkg upgrade -y && pkg install -y proot-distro && proot-distro install ${distro.id}"
    val enterCommand = "proot-distro login ${distro.id}"
    val desktopCommand = if (distro.family == "pacman") {
        "pacman -Syu --noconfirm xfce4 xfce4-goodies tigervnc dbus"
    } else {
        "apt update && apt install -y xfce4 dbus-x11 tigervnc-standalone-server"
    }
    val vncSetupCommand = "mkdir -p ~/.vnc && printf '%s\\n' '#!/bin/sh' 'unset SESSION_MANAGER' 'unset DBUS_SESSION_BUS_ADDRESS' 'exec dbus-run-session startxfce4' > ~/.vnc/xstartup && chmod +x ~/.vnc/xstartup && vncserver :1 -localhost yes -geometry 1280x720 -depth 24"
    val stopCommand = "vncserver -kill :1"

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Mora Linux Environment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Kelola Linux userspace Android melalui Termux + proot-distro. Distro berjalan terisolasi di ruang pengguna, bukan sebagai kernel atau VM terpisah.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(colors = CardDefaults.cardColors(containerColor = if (termuxInstalled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (termuxInstalled) "Termux terdeteksi" else "Termux belum terdeteksi", fontWeight = FontWeight.SemiBold)
                Text(
                    if (termuxInstalled) "Mora Shield akan membuka Termux untuk menjalankan perintah yang kamu salin." else "Pasang Termux dari sumber resminya terlebih dahulu. Jangan mencampur APK Termux dan plugin dari sumber tanda tangan berbeda.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                    if (intent != null) context.startActivity(intent)
                    else {
                        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/termux/termux-app/releases"))
                        context.startActivity(web)
                    }
                }) { Text(if (termuxInstalled) "Buka Termux" else "Panduan unduh Termux") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pilih distro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moraLinuxDistros.forEach { item ->
                    FilterChip(
                        selected = selectedId == item.id,
                        onClick = { selectedId = item.id },
                        label = { Text(item.title) }
                    )
                }
            }
            Text(distro.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LinuxCommandCard(
            title = "1. Pasang distro",
            description = "Jalankan di terminal Termux, bukan di dalam shell distro.",
            command = installCommand,
            action = "Salin perintah"
        )
        LinuxCommandCard(
            title = "2. Masuk ke distro",
            description = "Setelah instalasi selesai, jalankan perintah ini di Termux.",
            command = enterCommand,
            action = "Salin perintah"
        )
        LinuxCommandCard(
            title = "3. Pasang XFCE + VNC",
            description = "Jalankan setelah masuk ke shell distro. Instalasi desktop dapat memerlukan ruang penyimpanan cukup besar.",
            command = desktopCommand,
            action = "Salin perintah"
        )
        LinuxCommandCard(
            title = "4. Buat sesi desktop",
            description = "Masih di shell distro. VNC dibatasi ke localhost agar tidak membuka desktop ke jaringan Wi-Fi.",
            command = vncSetupCommand,
            action = "Salin perintah"
        )
        LinuxCommandCard(
            title = "Hentikan desktop",
            description = "Jalankan dari shell distro saat sesi XFCE tidak digunakan.",
            command = stopCommand,
            action = "Salin perintah"
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Hubungkan aplikasi VNC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Setelah server berjalan, buka aplikasi VNC Viewer dan buat koneksi ke host 127.0.0.1 dengan port 5901 (display :1). Atur password VNC saat pertama kali diminta oleh server.", style = MaterialTheme.typography.bodyMedium)
                Text("Untuk menghentikan desktop, jalankan perintah stop di atas lalu ketik exit untuk keluar dari shell distro. Tutup sesi Termux jika proses lain masih berjalan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            "Batasan: PRoot berbagi kernel Android; systemd, Docker, modul kernel, akselerasi GPU desktop, dan aplikasi yang membutuhkan akses kernel penuh mungkin tidak berjalan. Ketersediaan distro mengikuti versi proot-distro. Backup data distro secara berkala.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinuxCommandCard(title: String, description: String, command: String, action: String) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(command, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(title, command))
            }) { Text(action) }
        }
    }
}
