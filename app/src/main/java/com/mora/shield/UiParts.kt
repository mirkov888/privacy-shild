@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- Kartu & judul

@Composable
fun SectionCard(
    title: String, subtitle: String? = null, collapsible: Boolean = false, startOpen: Boolean = true,
    content: @Composable () -> Unit
) {
    var open by remember { mutableStateOf(startOpen || !collapsible) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().then(if (collapsible) Modifier.clickable { open = !open } else Modifier)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                if (collapsible) {
                    Text(if (open) "−" else "+", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
fun InfoNote(text: String, warn: Boolean = false) {
    val bg = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(bg).padding(14.dp)
    ) { Text(text, color = fg, style = MaterialTheme.typography.bodyMedium) }
}

// ---------------------------------------------------------------- Baris pengaturan

@Composable
fun RowText(title: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!sub.isNullOrEmpty()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Saklar yang terhubung langsung ke kunci konfigurasi modul. */
@Composable
fun SwitchRow(
    title: String, sub: String? = null, key: String,
    confirmOn: String? = null, enabled: Boolean = true
) {
    val on = Store.cfg.on(key)
    var ask by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, sub, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on, enabled = enabled && Store.installed,
            onCheckedChange = { v ->
                if (v && confirmOn != null) ask = true else Store.set(key, if (v) "1" else "0")
            }
        )
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(title) },
            text = { Text(confirmOn ?: "") },
            confirmButton = { TextButton(onClick = { ask = false; Store.set(key, "1") }) { Text("Aktifkan") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Batal") } }
        )
    }
}

/**
 * Saklar "Mode uji". Nilai modul: 1 = simulasi (aman), 0 = aksi sungguhan.
 * Mematikan mode uji meminta konfirmasi.
 */
@Composable
fun DryRow(key: String, what: String) {
    val dry = Store.cfg.dry(key)
    var ask by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(
            "Mode uji (simulasi)",
            if (dry) "Aman: hanya mencatat dan memberi notifikasi, $what TIDAK dijalankan."
            else "NONAKTIF: $what akan dijalankan sungguhan.",
            Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = dry, enabled = Store.installed, onCheckedChange = { v ->
            if (v) Store.set(key, "1") else ask = true
        })
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Matikan mode uji?") },
            text = { Text("Setelah ini, $what benar-benar akan dijalankan saat pemicunya terpenuhi. Pastikan sudah menguji dengan tombol uji.") },
            confirmButton = { TextButton(onClick = { ask = false; Store.set(key, "0") }) { Text("Matikan mode uji") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Tetap aman") } }
        )
    }
}

/** Pilihan satu dari beberapa nilai (dialog radio). */
@Composable
fun ChoiceRow(
    title: String, key: String, options: List<Pair<String, String>>, default: String = "",
    sub: String? = null, confirmValue: Pair<String, String>? = null
) {
    val cur = Store.cfg[key]?.ifEmpty { default } ?: default
    var open by remember { mutableStateOf(false) }
    var pendingValue by remember { mutableStateOf<String?>(null) }
    val label = options.firstOrNull { it.first == cur }?.second ?: cur.ifEmpty { "—" }
    Row(
        Modifier.fillMaxWidth().clickableRow { if (Store.installed) open = true }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, sub, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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
                                open = false
                                if (confirmValue != null && v == confirmValue.first) pendingValue = v
                                else Store.set(key, v)
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
    val pv = pendingValue
    if (pv != null && confirmValue != null) {
        AlertDialog(
            onDismissRequest = { pendingValue = null },
            title = { Text("Konfirmasi") },
            text = { Text(confirmValue.second) },
            confirmButton = { TextButton(onClick = { pendingValue = null; Store.set(key, pv) }) { Text("Lanjut") } },
            dismissButton = { TextButton(onClick = { pendingValue = null }) { Text("Batal") } }
        )
    }
}

@Composable
fun ActionRow(title: String, sub: String? = null, button: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, sub, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        if (danger) {
            Button(
                onClick = onClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(button) }
        } else {
            OutlinedButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
fun Divider() {
    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** Dialog konfirmasi sederhana. */
@Composable
fun Confirm(title: String, text: String, ok: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onOk() }) { Text(ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ---------------------------------------------------------------- Grafik (Canvas)

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(targetValue = score / 100f, animationSpec = tween(900), label = "score")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val col = when {
        score >= 70 -> MaterialTheme.colorScheme.primary
        score >= 40 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val sw = 14.dp.toPx()
            val inset = sw / 2
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(col, 135f, 270f * anim, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("skor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Batang horizontal berlabel. */
@Composable
fun BarList(rows: List<Pair<String, Int>>, color: Color) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(rows) { shown = true }
    val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, n) in rows) {
            val frac by animateFloatAsState(targetValue = if (shown) n.toFloat() / max else 0f, animationSpec = tween(700), label = "bar")
            Column {
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("$n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))) {
                    Box(
                        Modifier.fillMaxWidth(frac).height(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(color)
                    )
                }
            }
        }
    }
}

/** Histogram 24 jam. */
@Composable
fun Hourly(values: IntArray, color: Color) {
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            for (v in values) {
                val h = if (v == 0) 3f else 8f + 78f * v / max
                Box(
                    Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (v == 0) color.copy(alpha = 0.18f) else color)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (t in listOf("00", "06", "12", "18", "23")) {
                Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Donat sederhana dari beberapa irisan. */
@Composable
fun Donut(values: IntArray, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sum()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.size(96.dp)) {
        val sw = 16.dp.toPx()
        val inset = sw / 2
        val sz = Size(size.width - sw, size.height - sw)
        if (total == 0) {
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
        } else {
            var start = -90f
            for (i in values.indices) {
                if (values[i] == 0) continue
                val sweep = 360f * values[i] / total
                drawArc(colors[i], start, (sweep - 2f).coerceAtLeast(1f), false, Offset(inset, inset), sz, style = Stroke(sw))
                start += sweep
            }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ---------------------------------------------------------------- Util

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)

fun typeName(t: String): String = when (t) {
    "MIC" -> "Mikrofon"
    "CAM" -> "Kamera"
    "LOC" -> "Lokasi"
    "CONTACTS" -> "Kontak"
    "SMS" -> "SMS"
    "CALLLOG" -> "Log panggilan"
    "NETBIG" -> "Data keluar besar"
    "NEWAPP" -> "Aplikasi baru"
    "ACTION" -> "Aksi"
    "BOOTGUARD" -> "Pengaman boot"
    else -> t
}

fun fmtTime(ts: Long): String =
    java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale("id", "ID")).format(java.util.Date(ts * 1000))
