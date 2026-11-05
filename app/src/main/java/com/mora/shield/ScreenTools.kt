@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

@Composable
fun ToolsScreen() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Store.loadKernel()
        Store.loadScripts()
    }
    var confirm by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        LinkCard()
        if (!Store.installed) {
            StatusCard()
        } else {
            SectionCard("Darurat") {
                ActionRow("PANIC", "Mode pesawat, Bluetooth/NFC mati, cache dibersihkan, layar dikunci", "Jalankan", danger = true) { confirm = "panic" }
                Divider()
                ActionRow("Lockdown", "Biometrik mati, hanya PIN", "Kunci") { confirm = "lockdown" }
                Divider()
                ActionRow("Reboot terkunci", "Data aman, terkunci sampai PIN diketik", "Reboot") { confirm = "reboot" }
                Divider()
                ActionRow("Perbaiki internet", "Pulihkan jaringan bila kill switch/firewall membuat internet mati", "Perbaiki") { confirm = "netfix" }
            }
            SectionCard("Pemindaian dan audit") {
                ActionRow("Pindai SDK pelacak", "Memeriksa semua aplikasi. Hasil di tab Aplikasi dalam beberapa menit.", "Mulai") {
                    Store.run("trkstart", "Pemindaian dimulai") { Store.refresh(ctx) }
                }
                Divider()
                ActionRow("Pindai akses sekarang", "Cek cepat siapa memakai sensor saat ini", "Pindai") {
                    Store.run("monscan", "Selesai") { o -> Store.showText("Hasil pindai", o.ifBlank { "Tidak ada akses aktif." }) }
                }
                Divider()
                ActionRow("Laporan audit", "Ringkasan keamanan perangkat; periksa sebelum dibagikan", "Buat") {
                    Store.run("report", "Laporan dibuat") { o ->
                        Store.showText("Laporan audit", o)
                        reportText = o
                    }
                }
                if (reportText.isNotEmpty()) {
                    ActionRow("Bagikan laporan", null, "Bagikan") {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reportText) }
                        val c = Intent.createChooser(i, "Bagikan laporan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(c)
                    }
                }
                Divider()
                ActionRow("Cek kompatibilitas", "Fitur mana yang didukung ROM ini", "Cek") {
                    Store.run("compat", "Selesai") { o -> Store.showText("Kompatibilitas", o) }
                }
                Divider()
                ActionRow("Mode MVT 30 menit", "Nyalakan ADB sementara untuk pemeriksaan spyware dari laptop", "Nyalakan") { confirm = "mvt" }
                ActionRow("Akhiri mode MVT", null, "Matikan") { Store.run("mvt off", "MVT dimatikan") { Store.refresh(ctx) } }
            }
            UltimateCard(ctx)
        }
        KernelCard()
        ScriptsCard()
        AboutCard()
    }
    when (confirm) {
        "panic" -> Confirm("Jalankan PANIC?", "Mode pesawat, Bluetooth dan NFC mati, cache dibersihkan, layar dikunci.", "Jalankan", { confirm = "" }) { Store.run("panic", "PANIC dijalankan") }
        "lockdown" -> Confirm("Lockdown sekarang?", "Sidik jari/wajah dinonaktifkan dan layar dikunci.", "Kunci", { confirm = "" }) { Store.run("lockdown", "Lockdown dikirim") }
        "reboot" -> Confirm("Reboot sekarang?", "HP akan reboot dan terkunci sampai PIN diketik.", "Reboot", { confirm = "" }) { thread { Root.sh("svc power reboot || reboot") } }
        "netfix" -> Confirm("Perbaiki internet?", "Mematikan kill switch, firewall, DNS pribadi lalu sambung ulang Wi-Fi.", "Perbaiki", { confirm = "" }) { Store.run("netfix", "Jaringan dipulihkan") { Store.refresh(ctx) } }
        "mvt" -> Confirm("Nyalakan ADB sementara?", "Debugging USB menyala 30 menit lalu otomatis dikembalikan. Hubungkan hanya ke laptop tepercaya.", "Nyalakan", { confirm = "" }) { Store.run("mvt 30", "ADB menyala 30 menit") }
    }
}

private var reportText by mutableStateOf("")

// ------------------------------------------------------------------ UltimatePrivacy

@Composable
fun UltimateCard(ctx: android.content.Context) {
    var confirm by remember { mutableStateOf("") }
    SectionCard("UltimatePrivacy", "Ekstensi terpadu di modul. Dukungan sensor tergantung ROM.") {
        ActionRow("Status", null, "Lihat") {
            thread { val r = Ps.ultimate("status"); Store.showText("UltimatePrivacy", r.out.ifBlank { "Tidak ada keluaran" }) }
        }
        Divider()
        ActionRow("Sensor privacy: matikan mikrofon dan kamera", "Lewat layanan sensor_privacy Android", "Aktifkan") { confirm = "sensor enable" }
        ActionRow("Sensor privacy: pulihkan", null, "Pulihkan") { confirm = "sensor disable" }
        Divider()
        ActionRow("Penanda lockdown", "Hanya penanda, bukan pemblokiran sistem. Untuk lockdown sungguhan pakai tombol di Darurat.", "Hidup") { confirm = "lockdown on" }
        ActionRow("Hapus penanda", null, "Mati") { confirm = "lockdown off" }
        Divider()
        ActionRow("Log tindakan", null, "Buka") {
            thread { val r = Ps.ultimate("log"); Store.showText("Log UltimatePrivacy", r.out.ifBlank { "Log kosong" }) }
        }
    }
    if (confirm.isNotEmpty()) {
        val cmd = confirm
        Confirm("Jalankan?", "Perintah: $cmd. Hasil bergantung dukungan ROM; verifikasi di Pengaturan > Privasi.", "Jalankan", { confirm = "" }) {
            thread {
                val r = Ps.ultimate(cmd)
                Store.message = r.out.take(160).ifBlank { if (r.ok) "Selesai" else "Gagal" }
                Store.refresh(ctx)
            }
        }
    }
}

// ------------------------------------------------------------------ Kernel

@Composable
fun KernelCard() {
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    SectionCard("Kernel Manager", "Hanya governor CPU. Tidak menyentuh voltase, thermal, atau GPU.") {
        if (Store.rootOk != true) {
            EmptyHint("Butuh akses root.")
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(if (Store.ram >= 0) "${Store.ram}%" else "—", "RAM terpakai", Modifier.weight(1f))
            StatTile(if (Store.batt.first >= 0) "${Store.batt.first}%" else "—", "Baterai", Modifier.weight(1f))
            StatTile(if (Store.batt.second >= 0) "${Store.batt.second}°C" else "—", "Suhu baterai", Modifier.weight(1f))
        }
        Text(Store.kernelInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in listOf("performance" to "Performa", "balanced" to "Seimbang", "battery" to "Hemat baterai")) {
                FilterChip(selected = false, onClick = { confirm = Pair("preset:$id", "Preset $label hanya memilih governor yang tersedia pada setiap inti CPU.") }, label = { Text(label) })
            }
        }
        if (Store.kernel.isEmpty()) EmptyHint("cpufreq tidak ditemukan atau belum dimuat.")
        for (p in Store.kernel) {
            Divider()
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("${p.name} · ${p.governor}", style = MaterialTheme.typography.titleSmall)
                Text("Kini ${p.cur} · min ${p.min} · maks ${p.max}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (g in p.available) {
                        FilterChip(selected = g == p.governor, onClick = { confirm = Pair("gov:${p.path}:$g", "Terapkan governor $g pada ${p.name}?") }, label = { Text(g) })
                    }
                }
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText("Pulihkan governor saat boot", "Menyimpan governor sekarang; modul memulihkannya setelah boot selesai", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = Store.kernelBoot, onCheckedChange = { on ->
                thread { Store.message = KernelManager.saveGovernorsForBoot(on); Store.loadKernel() }
            })
        }
        ActionRow("Detail GPU, memori, thermal", null, "Lihat") {
            thread {
                val t = "GPU:\n" + KernelManager.gpu().ifBlank { "tidak ditemukan" } + "\n\nThermal:\n" + KernelManager.thermal()
                Store.showText("Detail perangkat", t)
            }
        }
        ActionRow("Muat ulang", null, "Muat") { Store.loadKernel() }
    }
    val c = confirm
    if (c != null) Confirm("Terapkan?", c.second, "Terapkan", { confirm = null }) {
        thread {
            val parts = c.first.split(":")
            Store.message = if (parts[0] == "preset") KernelManager.setProfile(parts[1]).take(160)
            else KernelManager.setGovernor(parts[1], parts[2])
            Store.loadKernel()
        }
    }
}

// ------------------------------------------------------------------ Skrip

@Composable
fun ScriptsCard() {
    var newOpen by remember { mutableStateOf(false) }
    var run by remember { mutableStateOf<String?>(null) }
    var del by remember { mutableStateOf<String?>(null) }
    SectionCard("Script root", "Dijalankan sebagai root. Hanya jalankan yang kamu pahami.") {
        if (Store.rootOk != true) {
            EmptyHint("Butuh akses root.")
            return@SectionCard
        }
        ActionRow("Buat script baru", null, "Buat") { newOpen = true }
        if (Store.scripts.isEmpty()) EmptyHint("Belum ada script.")
        for (f in Store.scripts) {
            Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { run = f }) { Text("Jalankan") }
                TextButton(onClick = { del = f }) { Text("Hapus") }
            }
        }
    }
    if (newOpen) NewScriptDialog { newOpen = false }
    val r = run
    if (r != null) Confirm("Jalankan script root?", "$r akan berjalan dengan hak root.", "Jalankan", { run = null }) {
        thread { val out = ScriptManager.run(r); Store.showText(r, out) }
    }
    val d = del
    if (d != null) Confirm("Hapus script?", d, "Hapus", { del = null }) {
        thread { Store.message = ScriptManager.delete(d); Store.loadScripts() }
    }
}

@Composable
fun NewScriptDialog(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("#!/system/bin/sh\n") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Script baru") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("nama-script.sh") })
                OutlinedTextField(body, { body = it }, minLines = 6, maxLines = 10, label = { Text("Isi") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone()
                val n = name.trim().let { if (it.endsWith(".sh")) it else "$it.sh" }
                thread { Store.message = ScriptManager.save(n, body); Store.loadScripts() }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Batal") } }
    )
}

// ------------------------------------------------------------------ Tentang & diagnostik

@Composable
fun AboutCard() {
    SectionCard("Tentang dan diagnostik") {
        Text(
            "Mora Shield 3 adalah pendamping modul Mora Privacy Shield. Semua kerja berat ada di modul lewat root; aplikasi ini menampilkan statistik dan mengirim perintah. " +
                "Aplikasi tidak memakai izin internet. Kejadian berasal dari pemeriksaan berkala sehingga akses sangat singkat bisa terlewat, dan isi data tidak bisa dilihat.\n\n" +
                "Perubahan di WebUI otomatis muncul di sini (modul v3.2+), dan sebaliknya.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )
        ActionRow("Diagnostik", "Kirim hasilnya bila ada masalah", "Jalankan") {
            thread {
                val rootId = Root.sh("id").out
                val inst = Root.sh("ls -la ${Ps.MOD} 2>&1 | head -n 15").out
                val st = Ps.run("sync").out.lines().take(30).joinToString("\n")
                Store.showText("Diagnostik", "root: $rootId\nversi modul: ${Store.version}\n\n$inst\n\nsync (30 baris):\n$st")
            }
        }
    }
}
