package com.mora.shield

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

private data class AppUsageRow(val packageName: String, val label: String, val foregroundMs: Long, val lastUsed: Long)

private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readAppUsage(context: Context, days: Int): List<AppUsageRow> {
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val start = end - TimeUnit.DAYS.toMillis(days.toLong())
    val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
    val grouped = stats.groupBy { it.packageName }.mapValues { (_, entries) ->
        entries.fold(0L to 0L) { acc, item ->
            (acc.first + item.totalTimeInForeground) to maxOf(acc.second, item.lastTimeUsed)
        }
    }
    val pm = context.packageManager
    return grouped.mapNotNull { (pkg, values) ->
        if (values.first <= 0L) return@mapNotNull null
        val label = try {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (_: Exception) { pkg }
        AppUsageRow(pkg, label, values.first, values.second)
    }.sortedByDescending { it.foregroundMs }
}

private fun durationLabel(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val hours = minutes / 60
    val remain = minutes % 60
    return if (hours > 0) "${hours}j ${remain}m" else "${remain}m"
}

@Composable
fun AppUsageScreen() {
    val context = LocalContext.current
    var days by remember { mutableStateOf(1) }
    var rows by remember { mutableStateOf(emptyList<AppUsageRow>()) }
    var access by remember { mutableStateOf(hasUsageAccess(context)) }
    var loading by remember { mutableStateOf(false) }

    fun reload() {
        access = hasUsageAccess(context)
        if (access) {
            loading = true
            rows = readAppUsage(context, days)
            loading = false
        }
    }

    LaunchedEffect(days) { reload() }

    Column(Modifier.fillMaxSize()) {
        PageIntro("Statistik penggunaan aplikasi", "Durasi aplikasi berada di latar depan. Android membatasi data ini dan memerlukan izin Akses penggunaan.")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = days == 1, onClick = { days = 1 }, label = { Text("24 jam") })
            FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text("7 hari") })
            FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text("30 hari") })
        }
        if (!access) {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Izin belum diberikan", fontWeight = FontWeight.SemiBold)
                    Text("Aktifkan Akses penggunaan untuk Mora Shield di pengaturan Android. Data tidak dikirim ke server.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Buka Akses penggunaan") }
                    TextButton(onClick = { reload() }) { Text("Periksa lagi") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${rows.size} aplikasi tercatat", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { reload() }) { Text("Muat ulang") }
            }
            if (loading) {
                EmptyHint("Memuat statistik…")
            } else if (rows.isEmpty()) {
                EmptyHint("Belum ada data penggunaan untuk periode ini.")
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.packageName }) { row ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(row.label, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(row.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(durationLabel(row.foregroundMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Terakhir digunakan: " + if (row.lastUsed > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.lastUsed)) else "—",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
