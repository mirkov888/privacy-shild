@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppsScreen() {
    var query by remember { mutableStateOf("") }
    var only by remember { mutableStateOf("") } // "", "trk", "blk", "new"
    var detail by remember { mutableStateOf<AppItem?>(null) }
    val q = query.trim().lowercase()
    val list = Store.apps.filter {
        (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)) &&
            when (only) {
                "trk" -> it.score > 0
                "blk" -> it.blocked
                "new" -> it.newApp
                else -> true
            }
    }
    Column(Modifier.fillMaxSize()) {
        PageIntro("Aplikasi", "Cari aplikasi, periksa pelacak, dan kendalikan akses jaringan.")
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Cari aplikasi") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = only == "", onClick = { only = "" }, label = { Text("Semua") })
            FilterChip(selected = only == "trk", onClick = { only = if (only == "trk") "" else "trk" }, label = { Text("Berpelacak") })
            FilterChip(selected = only == "blk", onClick = { only = if (only == "blk") "" else "blk" }, label = { Text("Diblokir") })
            FilterChip(selected = only == "new", onClick = { only = if (only == "new") "" else "new" }, label = { Text("Baru") })
        }
        if (!Store.installed) {
            EmptyHint("Hubungkan modul terlebih dulu (lihat tab Beranda).")
        } else if (list.isEmpty()) {
            EmptyHint("Tidak ada aplikasi yang cocok.")
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.pkg }) { a ->
                    Card(
                        Modifier.fillMaxWidth().clickableRow { detail = a },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (a.icon != null) {
                                Image(a.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
                            } else {
                                Spacer(Modifier.size(42.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, softWrap = true)
                                Text(
                                    when {
                                        a.newApp -> "Baru · internet ditahan"
                                        a.score > 0 -> "${a.score} pelacak"
                                        else -> "Tanpa pelacak terdeteksi"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (a.score > 8) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = a.blocked, onCheckedChange = { Store.toggleBlock(a) })
                        }
                    }
                }
            }
        }
    }
    val d = detail
    if (d != null) {
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(d.pkg, style = MaterialTheme.typography.labelSmall, softWrap = true)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (d.trackers.isEmpty()) "Tidak ada SDK pelacak yang dikenali (atau belum dipindai)."
                        else "SDK terdeteksi:\n" + d.trackers.split(",", ";").joinToString("\n") { "• " + it.trim() }
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (d.blocked) "Internet: DIBLOKIR" else "Internet: diizinkan")
                    if (d.tor) Text("Lewat Tor")
                    Spacer(Modifier.size(8.dp))
                    Text("Firewall jaringan", style = MaterialTheme.typography.labelMedium)
                    val inList = { k: String -> (Store.cfg[k] ?: "").split(",").contains(d.pkg) }
                    val mode = when {
                        inList("fw_pkgs") -> "both"
                        inList("fw_wifi") -> "wifi"
                        inList("fw_mobile") -> "mobile"
                        else -> "none"
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((m, l) in listOf("none" to "Bebas", "wifi" to "Blokir Wi-Fi", "mobile" to "Blokir seluler", "both" to "Blokir semua")) {
                            FilterChip(selected = mode == m, onClick = { Store.fwSet(d.pkg, m) }, label = { Text(l) })
                        }
                    }
                    val tl = Store.events.filter { it.pkg == d.pkg }.take(12)
                    Spacer(Modifier.size(8.dp))
                    Text("Linimasa", style = MaterialTheme.typography.labelMedium)
                    if (tl.isEmpty()) Text("Belum ada kejadian.", style = MaterialTheme.typography.bodySmall)
                    for (e in tl) {
                        Text(fmtTime(e.ts) + " · " + typeName(e.type) + (if (e.detail.isNotEmpty()) " (" + e.detail + ")" else ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row {
                    if (d.newApp) TextButton(onClick = {
                        val x = d; detail = null
                        kotlin.concurrent.thread {
                            val ok = Ps.allowNew(x.pkg)
                            Store.message = if (ok) "Diizinkan: ${x.label}" else "Gagal"
                            Store.refresh(ctx)
                        }
                    }) { Text("Izinkan") }
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + d.pkg))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    }) { Text("Izin sistem") }
                }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Tutup") } }
        )
    }
}
