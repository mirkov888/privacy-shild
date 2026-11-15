@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

private data class Phase15Pending(
    val title: String,
    val body: String,
    val confirm: String,
    val action: () -> Root.Result
)

@Composable
fun PerAppPrivacyScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Phase15App>>(emptyList()) }
    var selected by remember { mutableStateOf<Phase15App?>(null) }
    var query by remember { mutableStateOf("") }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf<List<Phase15Permission>>(emptyList()) }
    var modes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var metadata by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Phase15Pending?>(null) }
    var status by remember { mutableStateOf("") }

    fun loadApps() {
        loading = true
        thread {
            val root = Root.available()
            val result = if (root) PerAppPrivacyManager.listApps(context.applicationContext) else emptyList()
            rootAvailable = root
            apps = result
            loading = false
        }
    }

    fun loadDetail(pkg: String) {
        thread {
            permissions = PerAppPrivacyManager.permissions(context.applicationContext, pkg)
            val ops = listOf("READ_CLIPBOARD", "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND")
            modes = ops.associateWith { PerAppPrivacyManager.appOpMode(pkg, it) }
            val sensor = PerAppPrivacyManager.sensorMetadata(pkg)
            metadata = if (sensor.ok) sensor.out else sensor.out
        }
    }

    fun execute(action: Phase15Pending) {
        pending = null
        busy = true
        thread {
            val result = action.action()
            status = if (result.ok) result.out.ifBlank { "Perubahan diterapkan." }
                else "Gagal: " + result.out.take(180)
            if (result.ok) Store.refresh(context.applicationContext)
            selected?.let { loadDetail(it.packageName) }
            busy = false
        }
    }

    LaunchedEffect(Unit) { loadApps() }

    val current = selected
    Column(Modifier.fillMaxSize()) {
        PageIntro(
            "Per-App Permission & Sensor Control",
            "Izin sensitif, metadata sensor, clipboard, aktivitas latar belakang, dan firewall per aplikasi."
        )
        if (rootAvailable == false) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Akses root belum tersedia", fontWeight = FontWeight.SemiBold)
                    Text("Izinkan Mora Shield melalui KernelSU/Magisk untuk kontrol izin, AppOps, dan firewall.")
                    Button(onClick = { loadApps() }) { Text("Periksa root lagi") }
                }
            }
        } else if (current == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Cari aplikasi atau package") },
                singleLine = true
            )
            if (loading) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Memuat aplikasi…", Modifier.padding(top = 8.dp))
                }
            } else {
                val visible = apps.filter {
                    it.label.contains(query, true) || it.packageName.contains(query, true)
                }
                Text("${visible.size} aplikasi pihak ketiga", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        Card(
                            onClick = {
                                selected = app
                                permissions = emptyList()
                                modes = emptyMap()
                                metadata = ""
                                status = ""
                                loadDetail(app.packageName)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = null; status = ""; loadApps() }) { Text("‹ Kembali") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = { loadDetail(current.packageName) }) { Text("Muat ulang") }
                }
                Text(current.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(current.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Target dibatasi ke aplikasi pihak ketiga. Mora Shield, komponen sistem, layanan VPN, dan connector dilindungi dari perubahan firewall.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionCard("Izin sensitif") {
                    if (permissions.isEmpty()) {
                        EmptyHint("Tidak ada izin sensitif yang diminta aplikasi ini, atau informasi sedang dimuat.")
                    }
                    for (permission in permissions) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(permission.label, fontWeight = FontWeight.Medium)
                                Text(
                                    if (permission.granted) "Diizinkan" else "Ditolak",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (permission.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(enabled = !busy, onClick = {
                                val grant = !permission.granted
                                pending = Phase15Pending(
                                    if (grant) "Berikan izin?" else "Cabut izin?",
                                    "${permission.label} untuk ${current.label} akan ${if (grant) "diizinkan" else "dicabut"}. Android/ROM dapat menolak perubahan untuk izin tertentu.",
                                    if (grant) "Berikan" else "Cabut"
                                ) {
                                    PerAppPrivacyManager.setPermissionWithSnapshot(context.applicationContext, current.packageName, permission.permission, grant)
                                }
                            }) { Text(if (permission.granted) "Cabut" else "Izinkan") }
                        }
                    }
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    }) { Text("Buka pengaturan izin Android") }
                }

                SectionCard("Clipboard protection") {
                    val mode = modes["READ_CLIPBOARD"] ?: "memuat"
                    Text(
                        "Menggunakan AppOps READ_CLIPBOARD bila tersedia. Tidak membaca, menyimpan, atau mengirim isi clipboard.",
                        Modifier.padding(horizontal = 18.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Status: $mode", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && mode != "unsupported" && mode != "ignore", onClick = {
                            pending = Phase15Pending("Batasi clipboard?", "AppOps READ_CLIPBOARD akan diatur ke ignore untuk aplikasi ini. ROM dapat tidak mendukung AppOp ini.", "Blokir") {
                                PerAppPrivacyManager.setAppOp(context.applicationContext, current.packageName, "READ_CLIPBOARD", "ignore")
                            }
                        }) { Text("Blokir") }
                        TextButton(enabled = !busy && mode == "ignore", onClick = {
                            pending = Phase15Pending("Pulihkan clipboard?", "AppOps akan dikembalikan ke default; nilai awal disimpan untuk Emergency Restore.", "Pulihkan") {
                                PerAppPrivacyManager.restoreAppOps(context.applicationContext, current.packageName, listOf("READ_CLIPBOARD"))
                            }
                        }) { Text("Pulihkan") }
                    }
                }

                SectionCard("Aktivitas latar belakang") {
                    val bg1 = modes["RUN_IN_BACKGROUND"] ?: "memuat"
                    val bg2 = modes["RUN_ANY_IN_BACKGROUND"] ?: "memuat"
                    Text("RUN_IN_BACKGROUND: $bg1 · RUN_ANY_IN_BACKGROUND: $bg2", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.labelSmall)
                    Text("Pembatasan memakai AppOps Android. Perilaku dapat berbeda menurut versi Android dan ROM.", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && bg1 != "unsupported" && bg2 != "unsupported" && (bg1 != "ignore" || bg2 != "ignore"), onClick = {
                            pending = Phase15Pending("Batasi aktivitas latar belakang?", "Dua AppOps background untuk aplikasi ini akan diatur ke ignore. Notifikasi dan sinkronisasi aplikasi bisa tertunda.", "Batasi") {
                                val first = PerAppPrivacyManager.setAppOp(context.applicationContext, current.packageName, "RUN_IN_BACKGROUND", "ignore")
                                if (first.ok) PerAppPrivacyManager.setAppOp(context.applicationContext, current.packageName, "RUN_ANY_IN_BACKGROUND", "ignore") else first
                            }
                        }) { Text("Batasi") }
                        TextButton(enabled = !busy && (bg1 == "ignore" || bg2 == "ignore"), onClick = {
                            pending = Phase15Pending("Pulihkan aktivitas latar?", "AppOps background akan dikembalikan ke default. Nilai awal tersimpan untuk Emergency Restore.", "Pulihkan") {
                                PerAppPrivacyManager.restoreAppOps(context.applicationContext, current.packageName, listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND"))
                            }
                        }) { Text("Pulihkan") }
                    }
                }

                SectionCard("Pemantauan akses sensor") {
                    Text("Metadata AppOps saja: jenis operasi dan status yang dilaporkan Android. Tidak merekam gambar, audio, lokasi, atau payload sensor.", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActionRow("Periksa metadata akses", "Kamera, mikrofon, lokasi, kontak, SMS, dan clipboard bila tersedia", "Pindai") {
                        thread {
                            val r = PerAppPrivacyManager.sensorMetadata(current.packageName)
                            metadata = r.out
                        }
                    }
                    if (metadata.isNotEmpty()) {
                        Text(metadata, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                SectionCard("Firewall internet per aplikasi") {
                    Text("Backend aktif: modul Mora Privacy Shield melalui fwset (root). Komponen sistem, VPN, dan connector dikecualikan.", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val mode = PerAppPrivacyManager.firewallMode(current.packageName, Store.cfg)
                    Text("Mode saat ini: $mode", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((m, label) in listOf("none" to "Bebas", "wifi" to "Wi-Fi", "mobile" to "Seluler", "both" to "Semua")) {
                            FilterChip(
                                selected = mode == m,
                                onClick = {
                                    pending = Phase15Pending(
                                        if (m == "none") "Pulihkan internet?" else "Ubah firewall aplikasi?",
                                        if (m == "none") "Firewall aplikasi akan dikembalikan ke mode Bebas." else "Akses jaringan ${current.label} akan diubah ke mode $label.",
                                        "Terapkan"
                                    ) {
                                        PerAppPrivacyManager.setFirewall(context.applicationContext, current.packageName, m, Store.cfg)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                SectionCard("Emergency Restore") {
                    Text("Kembalikan izin, AppOps clipboard/background, dan mode firewall ke nilai sebelum perubahan Phase 15 terakhir yang tersimpan.", Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = !busy, onClick = {
                        pending = Phase15Pending(
                            "Pulihkan semua perubahan?",
                            "Perubahan Phase 15 untuk ${current.label} akan dikembalikan ke snapshot awal. Jika Android/ROM menolak suatu operasi, hasilnya akan ditampilkan.",
                            "Pulihkan"
                        ) {
                            PerAppPrivacyManager.restore(context.applicationContext, current.packageName, Store.cfg)
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) { Text("Emergency Restore") }
                }

                if (status.isNotEmpty()) {
                    Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) CircularProgressIndicator()
            }
        }
    }

    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(item.title) },
            text = { Text(item.body) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { execute(item) }) { Text(item.confirm) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Batal") } }
        )
    }
}
