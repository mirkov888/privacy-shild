package com.mora.shield

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

private fun parseHostSummary(text: String): Map<String, String> = text.lineSequence().mapNotNull { line ->
    val i = line.indexOf('=')
    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
}.toMap()

@Composable
fun ValueSwitch(
    title: String, sub: String?, key: String, onValue: String, offValue: String,
    defaultValue: String, enabled: Boolean = true
) {
    val current = Store.cfg[key]?.takeIf { it.isNotEmpty() } ?: defaultValue
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(title, sub, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = current == onValue, enabled = enabled && Store.installed,
            onCheckedChange = { Store.set(key, if (it) onValue else offValue) })
    }
}

@Composable
fun PurgeRow(title: String, sub: String, arg: String) {
    var ask by remember { mutableStateOf(false) }
    ActionRow(title, sub, "Hapus", danger = true) { ask = true }
    if (ask) Confirm(title, sub, "Hapus", { ask = false }) {
        Store.run("purge $arg", "Riwayat dihapus") { Store.refresh(Store.appCtx) }
    }
}

@Composable
fun HealthCard() {
    val c = Store.cfg
    val disabled = c["mod_disabled"] == "1"
    val tries = c["boot_try"]?.toIntOrNull() ?: 0
    val bindh = c["bindhosts_on"] == "1"
    val overlay = (c["live_hosts"]?.toIntOrNull() ?: 0) > 0
    SectionCard("Kesehatan modul", "Versi modul, status aktif, boot gagal, dan konflik", collapsible = true, startOpen = disabled || tries > 0 || (bindh && overlay)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(c["mod_version"] ?: "?", "versi modul", Modifier.weight(1f))
            StatTile(if (disabled) "Mati" else "Aktif", "status", Modifier.weight(1f))
            StatTile(tries.toString(), "boot gagal", Modifier.weight(1f))
        }
        if (disabled) {
            InfoNote("Modul sedang dinonaktifkan. Periksa pengaman boot atau modul hosts lain yang mengambil alih /system/etc/hosts.", true)
            ActionRow("Reset pengaman boot", "Mengaktifkan modul kembali pada boot berikutnya", "Reset") {
                Store.run("bootguard reset", "Pengaman boot direset") { Store.refresh(Store.appCtx) }
            }
        }
        if (tries > 0 && !disabled) InfoNote("Tercatat $tries percobaan boot yang belum selesai.", true)
        if (bindh && overlay) InfoNote("bindhosts aktif tetapi overlay hosts modul ini masih ada. Periksa konflik saat boot berikutnya.", true)
        Text("bindhosts: ${if (bindh) "aktif" else "mati"} · AdAway: ${if (c["aw_installed"] == "1") "terdeteksi" else "tidak terdeteksi"}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        ActionRow("Jalankan selftest", "Lihat hasil di bagian Diagnosis", "Uji") { Store.runSelftest() }
    }
}

@Composable
fun HostsCard() {
    var summary by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sources by remember { mutableStateOf<List<String>>(emptyList()) }
    var domain by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        thread {
            summary = parseHostSummary(Ps.run("hosts show").out)
            sources = Root.sh("cat ${Ps.DATA}/hosts_sources.txt 2>/dev/null").out.lines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        }
    }
    fun command(args: String, success: String) {
        if (busy) return
        busy = true
        thread {
            val r = Ps.run(args)
            Store.message = if (r.ok) success else "Gagal: ${r.out.take(120)}"
            busy = false
            reload()
            Store.refresh(Store.appCtx)
        }
    }
    LaunchedEffect(Store.installed) { if (Store.installed) reload() }

    SectionCard("Daftar pemblokir (hosts)", "Perbarui, ringkasan, whitelist, blacklist, dan sumber", collapsible = true) {
        val updated = summary["updated"]?.toLongOrNull()
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(summary["remote"] ?: "—", "domain sumber", Modifier.weight(1f))
            StatTile(summary["blacklist"] ?: "—", "blacklist", Modifier.weight(1f))
            StatTile(summary["whitelist"] ?: "—", "whitelist", Modifier.weight(1f))
        }
        Text("Diperbarui: ${updated?.let { fmtTime(it) } ?: "belum pernah"} · hosts aktif: ${summary["live"] ?: "—"} · dibangun: ${summary["built"] ?: "—"}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        ActionRow("Perbarui daftar sekarang", "Unduh sumber yang aktif; daftar lama dipertahankan bila gagal", if (busy) "Memproses" else "Perbarui") {
            if (!busy) command("hosts update", "Pembaruan hosts selesai")
        }
        ValueSwitch("Aktifkan sumber hosts", "Gunakan daftar unduhan", "bh_on", "1", "0", "1")
        Text("Jadwal pembaruan otomatis", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 3, 7, 14, 30).forEach { d ->
                FilterChip(selected = (Store.cfg["bh_days"]?.toIntOrNull() ?: 0) == d,
                    onClick = { Store.set("bh_days", d.toString()) }, label = { Text(if (d == 0) "Mati" else "$d hari") })
            }
        }
        Divider()
        Text("Sumber daftar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        sources.forEach { raw ->
            val disabled = raw.startsWith("disabled|")
            val url = raw.removePrefix("disabled|")
            if (HostInputValidator.urlOk(url)) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RowText(url.removePrefix("https://"), if (disabled) "mati" else "aktif", Modifier.weight(1f))
                Switch(checked = !disabled, onCheckedChange = { enabled ->
                    command("hosts src ${if (enabled) "on" else "off"} ${HostInputValidator.quoteUrl(url)}", "Status sumber diubah")
                })
                TextButton(onClick = { command("hosts src rm ${HostInputValidator.quoteUrl(url)}", "Sumber dihapus") }) { Text("Hapus") }
            }
        }
        OutlinedTextField(sourceUrl, { sourceUrl = it.trim() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            singleLine = true, label = { Text("URL sumber HTTPS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        ActionRow("Tambah sumber", "URL HTTPS berisi daftar hosts", "Tambah") {
            if (HostInputValidator.urlOk(sourceUrl)) { command("hosts src add ${HostInputValidator.quoteUrl(sourceUrl)}", "Sumber ditambahkan"); sourceUrl = "" }
            else Store.message = "URL harus HTTPS yang valid"
        }
        Divider()
        OutlinedTextField(domain, { domain = it.trim().lowercase() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            singleLine = true, label = { Text("Domain") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        ActionRow("Masukkan blacklist", "Selalu blokir domain", "Blokir") {
            if (HostInputValidator.domainOk(domain)) command("hosts add black $domain", "Domain masuk blacklist") else Store.message = "Domain tidak valid"
        }
        ActionRow("Masukkan whitelist", "Izinkan domain walau sumber memblokir", "Izinkan") {
            if (HostInputValidator.domainOk(domain)) command("hosts add white $domain", "Domain masuk whitelist") else Store.message = "Domain tidak valid"
        }
        ActionRow("Hapus blacklist", null, "Hapus") {
            if (HostInputValidator.domainOk(domain)) command("hosts del black $domain", "Domain dihapus dari blacklist") else Store.message = "Domain tidak valid"
        }
        ActionRow("Hapus whitelist", null, "Hapus") {
            if (HostInputValidator.domainOk(domain)) command("hosts del white $domain", "Domain dihapus dari whitelist") else Store.message = "Domain tidak valid"
        }
        Divider()
        ActionRow("Reset daftar unduhan", "Blacklist dan whitelist pribadi tetap disimpan", "Reset", danger = true) { command("hosts reset", "Daftar unduhan direset") }
        ActionRow("Muat ulang ringkasan", null, "Muat") { reload() }
    }
}

@Composable
fun CompatCard() {
    val c = Store.cfg
    val bindh = c["bindhosts_on"] == "1"
    SectionCard("AdAway dan bindhosts", "Status integrasi dan pengaturan kompatibilitas", collapsible = true) {
        Text("bindhosts: ${if (bindh) "aktif" else "tidak aktif"} · AdAway: ${if (c["aw_installed"] == "1") "terdeteksi" else "tidak terdeteksi"}",
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        if (bindh) ActionRow("Salurkan daftar ke bindhosts", "Setelah itu jalankan Action bindhosts di KernelSU Manager", "Salurkan") {
            Store.run("hosts bindsync", "Daftar disalurkan") { Store.refresh(Store.appCtx) }
        }
        ValueSwitch("Biarkan modul lain mengelola hosts", "Menghapus overlay hosts Privacy Shield; perlu reboot", "aw_yield", "1", "0", "0")
        ValueSwitch("Ekspor daftar untuk AdAway", "Menulis /sdcard/PS_hosts_export.txt", "aw_export", "1", "0", "0")
        ActionRow("Ekspor daftar sekarang", "Berkas lokal untuk AdAway", "Ekspor") {
            Store.run("hosts export", "Ekspor hosts selesai") { Store.refresh(Store.appCtx) }
        }
        InfoNote("AdAway pada KernelSU biasanya memerlukan helper systemless-hosts. Pasang helper, reboot, aktifkan opsi kompatibilitas, reboot lagi, lalu tambahkan PS_hosts_export.txt sebagai sumber lokal di AdAway.")
        InfoNote("Berkas /sdcard/PS_hosts_export.txt dapat dibaca aplikasi lain yang memiliki akses penyimpanan.", true)
    }
}
