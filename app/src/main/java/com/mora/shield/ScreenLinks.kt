@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LinkCard() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences(Linker.PREFS, Context.MODE_PRIVATE)
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Linker.Result?>(null) }
    var priv by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) } // memicu penyegaran label pengaturan
    SectionCard("Tautan: bersihkan dan alihkan", "Buang pelacak dari link dan buka lewat frontend privat. Semua lokal, tanpa internet.", collapsible = true, startOpen = false) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, label = { Text("Tempel tautan") }, minLines = 2, maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
        )
        Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            TextButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString() ?: "" else ""
                if (t.isEmpty()) Store.message = "Papan klip kosong" else input = t.take(4000)
            }) { Text("Ambil dari papan klip") }
            TextButton(onClick = {
                val r = Linker.clean(input.take(4000))
                result = r
                priv = Linker.redirect(ctx, r.url.trim())?.url
            }) { Text("Bersihkan") }
        }
        val r = result
        if (r != null) {
            Text(
                if (r.removed.isEmpty() && !r.unwrapped) "Tidak ada pelacak yang ditemukan."
                else "Dibuang ${r.removed.size} parameter" + (if (r.unwrapped) ", pengalihan dibongkar" else "") +
                    (if (r.removed.isNotEmpty()) ": " + r.removed.distinct().joinToString(", ") else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(r.url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            }
            val p = priv
            if (p != null && p != r.url) {
                Text("Versi privat:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 18.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(p, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                }
            }
            Row(Modifier.padding(horizontal = 18.dp)) {
                TextButton(onClick = { copyText(ctx, priv ?: r.url); Store.message = "Disalin" }) { Text("Salin") }
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, priv ?: r.url) }
                    ctx.startActivity(Intent.createChooser(i, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Bagikan") }
            }
        }
        Divider()
        key(tick) {
            PrefChoice("YouTube", "lk_yt", listOf("off" to "Tidak dialihkan", "invidious" to "Invidious", "piped" to "Piped", "newpipe" to "Aplikasi NewPipe", "libretube" to "Aplikasi LibreTube"), "off") { tick++ }
            PrefHost("Alamat Invidious/Piped", "lk_yt_host", "contoh: yewtu.be") { tick++ }
            PrefChoice("X / Twitter", "lk_tw", listOf("off" to "Tidak dialihkan", "on" to "Alihkan ke Nitter"), "off") { tick++ }
            PrefHost("Alamat Nitter", "lk_tw_host", "contoh: nitter.example.org") { tick++ }
            PrefChoice("Reddit", "lk_rd", listOf("off" to "Tidak dialihkan", "on" to "Alihkan ke Redlib"), "off") { tick++ }
            PrefHost("Alamat Redlib", "lk_rd_host", "contoh: redlib.example.org") { tick++ }
        }
        Text(
            "Pengalihan otomatis saat mengetuk link di aplikasi lain butuh izin sekali: Pengaturan sistem > Aplikasi > Mora Shield > Buka secara default > tambahkan tautan. Menu Bagikan > \"Mora Shield\" selalu bisa dipakai tanpa itu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
        ActionRow("Pengaturan buka tautan", null, "Buka") {
            try {
                val i = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e: Exception) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e2: Exception) {
                    Store.message = "Tidak bisa membuka pengaturan"
                }
            }
        }
    }
}

@Composable
fun PrefChoice(title: String, key: String, options: List<Pair<String, String>>, default: String, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences(Linker.PREFS, Context.MODE_PRIVATE)
    var cur by remember { mutableStateOf(prefs.getString(key, default) ?: default) }
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickableRow { open = true }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, null, Modifier.weight(1f))
        Text(options.firstOrNull { it.first == cur }?.second ?: cur, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    for ((v, l) in options) {
                        Row(
                            Modifier.fillMaxWidth().clickableRow {
                                cur = v; prefs.edit().putString(key, v).apply(); open = false; onChange()
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = v == cur, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(l)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Tutup") } }
        )
    }
}

@Composable
fun PrefHost(title: String, key: String, hint: String, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences(Linker.PREFS, Context.MODE_PRIVATE)
    var cur by remember { mutableStateOf(prefs.getString(key, "") ?: "") }
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(cur) }
    var err by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickableRow { text = cur; err = false; open = true }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, null, Modifier.weight(1f))
        Text(cur.ifEmpty { "belum diatur" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    Text("Isi hanya nama host tanpa https://. $hint", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.trim().lowercase(); err = false }, singleLine = true,
                        isError = err, label = { Text("Host") }
                    )
                    if (err) Text("Host tidak valid", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isEmpty() || Linker.validHost(text)) {
                        prefs.edit().putString(key, text).apply(); cur = text; open = false; onChange()
                    } else err = true
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } }
        )
    }
}
