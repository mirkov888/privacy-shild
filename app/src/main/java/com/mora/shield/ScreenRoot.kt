@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

private class RootFact(val label: String, val value: String, val visible: Boolean, val note: String)

private fun parseFacts(out: String): Pair<List<RootFact>, Set<String>> {
    val props = HashMap<String, String>()
    val x = HashMap<String, String>()
    var mods = ""
    for (l in out.lines()) {
        val p = l.split("|", limit = 3)
        if (p.size < 3) continue
        when (p[0]) {
            "P" -> props[p[1]] = p[2]
            "X" -> x[p[1]] = p[2]
            "M" -> mods = p[2]
        }
    }
    val f = ArrayList<RootFact>()
    val vb = props["ro.boot.verifiedbootstate"] ?: ""
    f.add(RootFact("Status boot terverifikasi", vb.ifEmpty { "tidak terbaca" }, vb == "orange" || vb == "yellow", "Orange berarti bootloader terbuka. Aplikasi yang memeriksa ini akan melihatnya."))
    val lk = props["ro.boot.flash.locked"] ?: ""
    f.add(RootFact("Bootloader terkunci", if (lk == "1") "ya" else if (lk == "0") "tidak (terbuka)" else "tidak terbaca", lk == "0", "Bootloader terbuka terlihat lewat properti ini."))
    val ds = props["ro.boot.vbmeta.device_state"] ?: ""
    f.add(RootFact("Status perangkat vbmeta", ds.ifEmpty { "tidak terbaca" }, ds == "unlocked", "Nilai unlocked berarti bootloader terbuka."))
    val tags = props["ro.build.tags"] ?: ""
    f.add(RootFact("Tanda build", tags.ifEmpty { "tidak terbaca" }, tags.contains("test-keys"), "test-keys menandakan build tidak resmi. ROM kustom sering memakainya."))
    val dbg = props["ro.debuggable"] ?: ""
    f.add(RootFact("Debuggable", if (dbg == "1") "ya" else "tidak", dbg == "1", "ro.debuggable=1 jarang normal di perangkat produksi."))
    val su = x["su"] ?: ""
    f.add(RootFact("Biner su di PATH", su.ifEmpty { "tidak ada di PATH" }, su.isNotEmpty(), "Aplikasi biasa tidak punya akses ke PATH root, tapi ini tanda yang dicari beberapa deteksi."))
    val mgr = if (x["ksu"] == "1") "KernelSU" else if (x["magisk"] == "1") "Magisk" else "tidak terdeteksi"
    f.add(RootFact("Pengelola root", mgr, false, ""))
    val ids = mods.split(",").filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    return Pair(f, ids)
}

@Composable
fun RootCard() {
    val ctx = LocalContext.current
    var facts by remember { mutableStateOf<List<RootFact>>(emptyList()) }
    var mods by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ran by remember { mutableStateOf(false) }
    SectionCard("Jejak root dan bootloader", "Apa yang bisa dilihat aplikasi lain dari HP ini", collapsible = true, startOpen = false) {
        ActionRow("Periksa", "Hanya membaca. Tidak mengubah atau menyembunyikan apa pun", "Periksa") {
            thread {
                val r = Ps.run("rootcheck")
                if (!r.ok) { Store.message = "Gagal: " + r.out.take(100); return@thread }
                val pr = parseFacts(r.out)
                facts = pr.first
                mods = pr.second
                ran = true
            }
        }
        if (ran) {
            for (f in facts) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (f.visible) "●" else "○", color = if (f.visible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                        Text(f.label + ": " + f.value, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (f.visible && f.note.isNotEmpty()) Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider()
            Text("Alat penyembunyi yang terpasang", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            val tools = listOf(
                "Zygisk" to listOf("zygisk"),
                "Shamiko" to listOf("shamiko"),
                "Play Integrity Fix" to listOf("playintegrity", "integrity", "pif"),
                "LSPosed" to listOf("lsposed"),
                "SuSFS" to listOf("susfs")
            )
            for ((name, keys) in tools) {
                val on = mods.any { m -> keys.any { k -> m.contains(k) } && !m.endsWith("(off)") }
                Text((if (on) "✔ " else "– ") + name, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Cara yang umum dipakai: (1) di pengelola root, matikan 'Umount modul' atau pakai denylist untuk aplikasi yang tidak boleh melihat root; " +
                "(2) pasang Zygisk Next + Shamiko lalu masukkan aplikasi target ke daftar sembunyi; (3) jangan sembunyikan dari aplikasi yang tidak perlu. " +
                "Pengaturan per aplikasi itu tetap dilakukan di aplikasi pengelola tersebut, bukan di sini.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        InfoNote("Menyamarkan bootloader terbuka agar lolos Play Integrity butuh keybox atau atestasi palsu. Itu tidak bisa dilakukan oleh modul ini dan tidak akan saya buatkan; hasilnya juga tidak stabil. Di /e/OS tanpa Google Play Services, aplikasi yang mewajibkan 'strong integrity' memang akan gagal apa pun yang kamu lakukan.")
        ActionRow("Buka pengelola root", "KernelSU / KernelSU Next / Magisk", "Buka") { openRootManager(ctx) }
    }
}

private fun openRootManager(ctx: Context) {
    val pkgs = listOf("me.weishu.kernelsu", "com.rifsxd.ksunext", "com.sukisu.ultra", "com.topjohnwu.magisk", "io.github.vvb2060.magisk")
    for (p in pkgs) {
        val i: Intent? = ctx.packageManager.getLaunchIntentForPackage(p)
        if (i != null) {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
    Store.message = "Pengelola root tidak ditemukan"
}
