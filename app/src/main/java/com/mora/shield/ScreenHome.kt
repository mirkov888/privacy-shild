@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
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
    val bannerContext = LocalContext.current
    LaunchedEffect(bannerContext) { BannerStore.load(bannerContext) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmFix by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Hero(s.score)
        StatusCard()
        if (Store.installed) {
            BootGuardCard()
            SecurityCard()
            SituationCard()
            SectionCard("Skor perlindungan", "Berdasarkan ${SCORE_KEYS.size} proteksi inti yang aktif") {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
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
            WeeklyCard()
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
        !Store.installed -> Triple("Modul tidak ditemukan", "Pasang modul Mora Privacy Shield v3.5 lalu reboot.", true)
        Ps.major(Store.version) < 3 -> Triple("Modul terlalu lama", "Terpasang ${Store.version}. Pasang v3.5.0 lalu reboot.", true)
        !Store.cfg.containsKey("rev") -> Triple("Modul ${Store.version}", "Sinkron otomatis aktif di modul v3.2+. Fitur lain tetap jalan.", false)
        else -> Triple("Modul ${Store.version} aktif", "Sinkron dengan WebUI: perubahan di sana muncul di sini otomatis.", false)
    }
    InfoNote("$title\n$body", warn)
}


@Composable
fun Hero(score: Int) {
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(210.dp)) {
        val customBanner = BannerStore.bitmap(LocalContext.current)
        if (customBanner != null) {
            Image(
                bitmap = customBanner.asImageBitmap(), contentDescription = "Banner Mora Shield",
                modifier = Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Crop, alpha = 0.62f
            )
        } else {
            Image(
                painter = painterResource(R.drawable.hero_cat), contentDescription = "Banner bawaan Mora Shield",
                modifier = Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Crop, alpha = 0.55f
            )
        }
        Box(
            Modifier.fillMaxWidth().height(210.dp).background(
                Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.55f), bg))
            )
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).align(Alignment.BottomStart),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text("MORA SHIELD", fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    when {
                        Store.rootOk != true || !Store.installed -> "Belum terhubung ke modul"
                        score >= 70 -> "Perlindungan kuat"
                        score >= 40 -> "Perlindungan sedang"
                        else -> "Perlindungan rendah"
                    },
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
                )
            }
            if (Store.installed) ScoreRing(score, Modifier.size(96.dp))
        }
    }
}

@Composable
fun SecurityCard() {
    val ctx = LocalContext.current
    val patch = Store.cfg["patch"] ?: ""
    val age: Long? = try {
        if (patch.isEmpty()) null
        else java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(patch), java.time.LocalDate.now())
    } catch (e: Exception) { null }
    val eos = Store.cfg["eos"] == "1"
    val rel = Store.cfg["android_rel"] ?: ""
    SectionCard("Keamanan sistem", "Pembaruan adalah perlindungan terbaik") {
        Text(
            (if (eos) "/e/OS ${Store.cfg["eos_ver"] ?: ""}".trim() else "Android") + (if (rel.isNotEmpty()) " · Android $rel" else ""),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp)
        )
        if (patch.isNotEmpty()) {
            val old = age != null && age > 75
            Text(
                "Patch keamanan: $patch" + (if (age != null) " ($age hari lalu)" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = if (old) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            if (old) Text(
                "Patch sudah lama. Periksa pembaruan sistem; celah yang belum ditambal bisa dipakai penyadap.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
        if (eos && Store.cfg["adv_privacy"] == "1") {
            Text(
                "Advanced Privacy terpasang" + (if ((Store.cfg["vpn_active"] ?: "0") != "0") " · VPN lokal aktif" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }
        ActionRow("Pembaruan sistem", null, "Buka") {
            try {
                val i = android.content.Intent("android.settings.SYSTEM_UPDATE_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e: Exception) {
                try {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e2: Exception) {
                    Store.message = "Tidak bisa membuka pengaturan pembaruan"
                }
            }
        }
    }
}


@Composable
fun BootGuardCard() {
    val dis = Store.cfg["mod_disabled"] == "1"
    val tries = Store.cfg["boot_try"]?.toIntOrNull() ?: 0
    if (dis) {
        InfoNote("Pengaman boot menonaktifkan modul karena HP gagal menyelesaikan boot 3 kali berturut-turut. Perlindungan modul MATI sampai kamu mengaktifkannya lagi.", true)
        ActionRow("Aktifkan modul lagi", "Berlaku setelah reboot berikutnya", "Aktifkan") {
            Store.run("bootguard reset", "Modul akan aktif setelah reboot") { Store.refresh(Store.appCtx) }
        }
    } else if (tries >= 1) {
        InfoNote("Percobaan boot belum selesai: $tries dari 3. Bila HP baru saja bootloop, modul akan menonaktifkan diri pada percobaan ke-3.", true)
    }
}

@Composable
fun SituationCard() {
    var pick by remember { mutableStateOf<String?>(null) }
    val cur = Store.cfg["sprofile"] ?: ""
    val desc = mapOf(
        "demo" to "Biometrik mati dalam 1 jam, USB data terkunci, NFC/Bluetooth/ADB mati, peringatan Wi-Fi terbuka, aplikasi baru ditahan, pemantau akses dan pemicu tombol dinyalakan.",
        "travel" to "USB terkunci, ADB mati, aplikasi baru ditahan, pantau akses dan data keluar, biometrik mati setelah 8 jam, reboot tiap 24 jam (tetap mode uji bila belum kamu matikan).",
        "home" to "Melonggarkan: USB data, biometrik, Bluetooth/NFC, timer Wi-Fi, kill switch, reboot otomatis dan pemicu tombol dimatikan. Iklan dan telemetri tetap diblokir."
    )
    SectionCard("Profil situasi", "Satu ketukan untuk keadaan tertentu" + (if (cur.isNotEmpty()) ". Terakhir: $cur" else "")) {
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in listOf("demo" to "Demo/Protes", "travel" to "Perjalanan", "home" to "Rumah")) {
                androidx.compose.material3.FilterChip(selected = cur == id, onClick = { pick = id }, label = { Text(label) })
            }
        }
        Text(
            "Tidak pernah mengubah mode uji, persetujuan hapus data, atau perlindungan terkunci PIN.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
    val p = pick
    if (p != null) Confirm("Terapkan profil $p?", desc[p] ?: "", "Terapkan", { pick = null }) {
        Store.run("sprofile $p", "Profil $p diterapkan") { Store.refresh(Store.appCtx) }
    }
}

@Composable
fun WeeklyCard() {
    val ctx = LocalContext.current
    val rows = Store.weekly()
    SectionCard("Laporan 7 hari", "Dibanding 7 hari sebelumnya (dari kejadian yang tersimpan)") {
        if (rows.isEmpty()) {
            EmptyHint("Belum cukup data.")
            return@SectionCard
        }
        for (r in rows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typeName(r.type), style = MaterialTheme.typography.bodyLarge)
                    if (r.top.isNotEmpty()) Text("Terbanyak: ${r.top}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val d = r.now - r.prev
                Text("${r.now}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (d > 0) "▲ $d" else if (d < 0) "▼ ${-d}" else "–",
                    color = if (d > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        ActionRow("Bagikan ringkasan", null, "Bagikan") {
            val sb = StringBuilder("Laporan Mora Shield 7 hari\n")
            for (r in rows) sb.append(typeName(r.type)).append(": ").append(r.now).append(" (sebelumnya ").append(r.prev).append(")")
                .append(if (r.top.isNotEmpty()) ", terbanyak ${r.top}" else "").append("\n")
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, sb.toString()) }
            ctx.startActivity(android.content.Intent.createChooser(i, "Bagikan").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
