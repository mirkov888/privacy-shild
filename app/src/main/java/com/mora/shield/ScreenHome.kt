@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    val s = Store.stats()
    var confirmLock by remember { mutableStateOf(false) }
    var confirmFix by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        StatusCard()
        if (Store.installed) {
            SectionCard("Skor perlindungan", "Berdasarkan ${SCORE_KEYS.size} proteksi inti yang aktif") {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(s.score)
                    Spacer(Modifier.width(18.dp))
                    Column {
                        val on = SCORE_KEYS.count { Store.cfg.on(it) }
                        Text("$on dari ${SCORE_KEYS.size} aktif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                s.score >= 70 -> "Perlindungan kuat."
                                s.score >= 40 -> "Cukup. Aktifkan lebih banyak di tab Pengaturan."
                                else -> "Masih rendah. Coba profil Jurnalis di Pengaturan."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${s.total}", "kejadian 7 hari", Modifier.weight(1f))
                StatTile("${s.blockedApps}", "app diblokir", Modifier.weight(1f))
                StatTile("${s.newApps}", "app baru ditahan", Modifier.weight(1f))
            }
            SectionCard("Jam rawan", "Kapan aplikasi paling sering mengakses sensor/data (7 hari)") {
                Hourly(s.hourly, MaterialTheme.colorScheme.primary)
            }
            SectionCard("Jenis kejadian", "7 hari terakhir") {
                if (s.byType.isEmpty()) EmptyHint("Belum ada kejadian. Nyalakan pemantauan di Pengaturan.")
                else BarList(s.byType.map { Pair(typeName(it.first), it.second) }, MaterialTheme.colorScheme.tertiary)
            }
            SectionCard("Aplikasi paling aktif", "Berdasarkan jumlah kejadian") {
                if (s.topApps.isEmpty()) EmptyHint("Belum ada data.")
                else BarList(s.topApps, MaterialTheme.colorScheme.primary)
            }
            SectionCard("Tingkat pelacak", "Hasil pindai SDK pada aplikasi terpasang") {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val cols = listOf(
                        MaterialTheme.colorScheme.primary, Color(0xFFE0B341),
                        Color(0xFFE8833A), MaterialTheme.colorScheme.error
                    )
                    Donut(s.levels, cols)
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val names = listOf("Bersih", "Rendah", "Sedang", "Tinggi")
                        for (i in 0..3) {
                            Text("${names[i]}: ${s.levels[i]}", style = MaterialTheme.typography.bodyMedium, color = cols[i])
                        }
                    }
                }
                if (Store.apps.none { it.score > 0 }) {
                    Spacer(Modifier.height(6.dp))
                    EmptyHint("Belum ada hasil pindai. Jalankan di tab Alat.")
                }
            }
            SectionCard("Aksi cepat") {
                ActionRow("Lockdown", "Matikan biometrik dan kunci layar", "Kunci") { confirmLock = true }
                Divider()
                ActionRow("Perbaiki internet", "Matikan kill switch, firewall, DNS pribadi lalu sambung ulang Wi-Fi", "Perbaiki") { confirmFix = true }
            }
        }
    }
    if (confirmLock) Confirm("Lockdown sekarang?", "Sidik jari/wajah dinonaktifkan dan layar dikunci. Buka dengan PIN.", "Lockdown", { confirmLock = false }) {
        Store.run("lockdown", "Lockdown dikirim")
    }
    if (confirmFix) Confirm("Perbaiki internet?", "Mematikan kill switch, firewall, DNS pribadi, dan beberapa proteksi jaringan.", "Perbaiki", { confirmFix = false }) {
        Store.run("netfix", "Pengaturan jaringan dipulihkan")
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    )
}

@Composable
fun StatusCard() {
    val ok = Store.rootOk
    val (title, body, warn) = when {
        ok == null -> Triple("Memeriksa…", "Mengecek akses root dan modul.", false)
        ok == false -> Triple("Root belum diizinkan", "Buka KernelSU Manager > Superuser lalu izinkan Mora Shield.", true)
        !Store.installed -> Triple("Modul tidak ditemukan", "Pasang modul Mora Privacy Shield v3.2 lalu reboot.", true)
        Ps.major(Store.version) < 3 -> Triple("Modul terlalu lama", "Terpasang ${Store.version}. Pasang v3.2.0 lalu reboot.", true)
        !Store.cfg.containsKey("rev") -> Triple("Modul ${Store.version}", "Sinkron otomatis aktif di modul v3.2+. Fitur lain tetap jalan.", false)
        else -> Triple("Modul ${Store.version} aktif", "Sinkron dengan WebUI: perubahan di sana muncul di sini otomatis.", false)
    }
    InfoNote("$title\n$body", warn)
}
