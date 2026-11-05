@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Context
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

@Composable
fun statusColor(s: String): Color = when (s) {
    "PASS" -> MaterialTheme.colorScheme.primary
    "FAIL", "high" -> MaterialTheme.colorScheme.error
    "warn" -> Color(0xFFE8833A)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun DiagCard() {
    val ctx = LocalContext.current
    var knownTick by remember { mutableStateOf(0) }
    val known = Known.set(ctx).also { knownTick }
    SectionCard("Uji sistem", "Memeriksa fitur modul mana yang benar-benar jalan di HP ini", collapsible = true, startOpen = false) {
        ActionRow("Jalankan uji sistem", "Hanya membaca; tidak mengubah pengaturan", "Uji") { Store.runSelftest() }
        if (Store.testSum.isNotEmpty()) {
            Text(Store.testSum, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        }
        for (t in Store.tests) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Text(if (t.status == "PASS") "✔" else if (t.status == "FAIL") "✖" else "–", color = statusColor(t.status), modifier = Modifier.width(22.dp))
                Column {
                    Text(t.name, style = MaterialTheme.typography.bodyMedium)
                    Text(t.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    SectionCard("Pemeriksa tanda mencurigakan", "Mencari tanda umum penyadapan. Heuristik, bukan antivirus", collapsible = true, startOpen = false) {
        ActionRow("Periksa sekarang", "Aksesibilitas, akses notifikasi, device admin, sertifikat CA, VPN", "Periksa") { Store.runSus() }
        if (Store.susRan) {
            val shown = Store.sus.filter { (it.kind + ":" + it.id) !in known }
            val bad = shown.count { it.sev == "high" || it.sev == "warn" }
            Text(
                if (bad == 0) "Tidak ada yang perlu dicurigai (di luar yang sudah kamu tandai dikenal)." else "$bad temuan perlu dicek.",
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
            for (r in shown) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = statusColor(r.sev), modifier = Modifier.width(20.dp))
                        Text(kindName(r.kind) + " · " + r.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(r.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = { openSystemFor(ctx, r) }) { Text("Buka pengaturan") }
                        if (r.sev != "info") TextButton(onClick = { Known.add(ctx, r); knownTick++ }) { Text("Saya kenal ini") }
                    }
                }
            }
            if (known.isNotEmpty()) TextButton(onClick = { Known.clear(ctx); knownTick++ }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Reset tanda dikenal") }
        }
        EmptyHint("Daftar indikator sendiri: taruh nama paket (satu per baris) di /data/adb/privacyshield/indicators.txt. Aplikasi ini tidak punya internet, jadi daftar harus kamu bawa sendiri.")
    }
}

@Composable
fun BackupCard() {
    val ctx = LocalContext.current
    var askRestore by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) thread {
            val r = Ps.run("backup")
            if (!r.ok) { Store.message = "Cadangan gagal: " + r.out.take(100); return@thread }
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(r.out.toByteArray()) }
                Store.message = "Cadangan disimpan"
            } catch (e: Exception) {
                Store.message = "Gagal menulis berkas"
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) thread {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.isEmpty() || bytes.size > 20000) { Store.message = "Berkas kosong atau terlalu besar"; return@thread }
                val text = String(bytes, Charsets.UTF_8)
                val ok = Regex("^(#.*|[A-Za-z0-9_]+=[A-Za-z0-9._,:/-]*)?$")
                if (!text.lines().all { ok.matches(it.trimEnd('\r')) }) { Store.message = "Bukan berkas cadangan Mora Shield"; return@thread }
                val b64 = Base64.encodeToString(text.replace("\r", "").toByteArray(), Base64.NO_WRAP)
                val r = Root.sh("mkdir -p ${Ps.DATA} && printf '%s' '$b64' | base64 -d > ${Ps.DATA}/restore.in && sh ${Ps.MOD}/bin/ps.sh restore")
                Store.showText("Hasil pemulihan", if (r.ok) r.out.lines().firstOrNull { it.startsWith("RESTORE|") }?.let {
                    val p = it.split("|"); "${p.getOrNull(1)} pengaturan dipulihkan, ${p.getOrNull(2)} gagal, ${p.getOrNull(3)} dilewati (PIN, persetujuan hapus data, perlindungan terkunci, dan mode uji tidak ikut dipulihkan)."
                } ?: r.out else "Gagal: " + r.out.take(200))
                Store.refresh(ctx)
            } catch (e: Exception) {
                Store.message = "Gagal membaca berkas"
            }
        }
    }
    SectionCard("Cadangan pengaturan", "Simpan ke file, pulihkan setelah flash ulang ROM", collapsible = true, startOpen = false) {
        ActionRow("Simpan cadangan", "Tanpa PIN dan tanpa persetujuan hapus data", "Simpan") { save.launch("mora-shield-config.txt") }
        ActionRow("Pulihkan dari file", "Mode uji selalu tetap menyala setelah pulih", "Pilih") { askRestore = true }
    }
    if (askRestore) Confirm(
        "Pulihkan pengaturan?", "Pengaturan modul ditimpa dari file. Kill switch atau firewall di file bisa langsung memutus internet; pakai 'Perbaiki internet' bila perlu.",
        "Pilih berkas", { askRestore = false }
    ) { open.launch(arrayOf("text/plain", "*/*")) }
}
