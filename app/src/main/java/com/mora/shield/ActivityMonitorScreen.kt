@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mora.shield.core.ActivityEventEntity
import com.mora.shield.core.CoreOverview
import com.mora.shield.core.MoraCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private enum class MonitorSection(val label: String) {
    DASHBOARD("Dashboard"), APPS("App Monitor"), TIMELINE("Timeline"), NETWORK("Network"), SECURITY("Security")
}

private data class MonitorAppRow(
    val packageName: String,
    val label: String,
    val uid: Int,
    val system: Boolean,
    val rxBytes: Long,
    val txBytes: Long,
    val connectorState: String,
    val monitoringEnabled: Boolean?,
    val eventCount: Int
)

private data class MonitorSnapshot(
    val overview: CoreOverview = CoreOverview(),
    val apps: List<MonitorAppRow> = emptyList(),
    val events: List<ActivityEventEntity> = emptyList(),
    val totalRx: Long = -1,
    val totalTx: Long = -1,
    val sockets: List<String> = emptyList(),
    val socketsSupported: Boolean = false,
    val securitySignals: List<com.mora.shield.security.NetworkRiskMonitor.Signal> = emptyList(),
    val permissionApps: List<com.mora.shield.security.PermissionAudit.AppPermissions> = emptyList(),
    val updatedAt: Long = 0L,
    val error: String = ""
)

@Composable
fun ActivityMonitorScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var section by remember { mutableStateOf(MonitorSection.DASHBOARD) }
    var query by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(MonitorSnapshot()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        MoraCore.initialize(context.applicationContext)
        while (true) {
            val next = withContext(Dispatchers.IO) { loadMonitorSnapshot(context.applicationContext) }
            snapshot = next
            loading = false
            delay(10_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonitorSection.values().forEach { item ->
                FilterChip(selected = section == item, onClick = { section = item }, label = { Text(item.label) })
            }
        }
        when (section) {
            MonitorSection.DASHBOARD -> DashboardMonitor(snapshot, loading)
            MonitorSection.APPS -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Cari nama atau package aplikasi") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                )
                val q = query.trim().lowercase()
                val filtered = snapshot.apps.filter {
                    q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Text("${filtered.size} aplikasi terpasang · status koneksi Mora ditampilkan terpisah dari status pemantauan Android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(filtered, key = { it.packageName }) { app ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    when {
                                        app.connectorState == "CONNECTED" && app.monitoringEnabled == true -> "Connector aktif · pelaporan event diizinkan"
                                        app.connectorState == "CONNECTED" && app.monitoringEnabled == false -> "Connector aktif · pemantauan dinonaktifkan oleh kebijakan"
                                        app.connectorState == "CONNECTED" && app.monitoringEnabled == null -> "Connector aktif · kebijakan pemantauan belum tersedia"
                                        app.connectorState == "REGISTERED" -> "Connector terdaftar · belum tersambung"
                                        else -> "Belum memiliki Connector Mora"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("${if (app.system) "Aplikasi sistem" else "Aplikasi pengguna"} · UID ${app.uid} · ${app.eventCount} event", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Lalu lintas sejak boot: ↓ ${formatBytes(app.rxBytes)}  ↑ ${formatBytes(app.txBytes)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (filtered.isEmpty()) item { EmptyHint("Tidak ada aplikasi yang cocok.") }
                }
            }
            MonitorSection.TIMELINE -> TimelineMonitor(snapshot)
            MonitorSection.NETWORK -> NetworkMonitor(snapshot)
            MonitorSection.SECURITY -> SecurityMonitor(snapshot)
        }
    }
}

private suspend fun loadMonitorSnapshot(context: Context): MonitorSnapshot {
    return try {
        val pm = context.packageManager
        val events = runCatching { MoraCore.recentEvents(500) }.getOrDefault(emptyList())
        val overview = runCatching { MoraCore.overview() }.getOrDefault(CoreOverview())
        val registered = runCatching { MoraCore.registeredApps() }.getOrDefault(emptyList())
        val policies = registered.associate { app ->
            app.packageName to runCatching { MoraCore.appPolicy(app.packageName) }.getOrNull()
        }
        val registry = registered.associateBy { it.packageName }
        val eventCounts = events.flatMap { listOf(it.affectedPackage, it.sourcePackage) }
            .filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL).map { info ->
            val registeredApp = registry[info.packageName]
            val policy = policies[info.packageName]
            val rx = TrafficStats.getUidRxBytes(info.uid)
            val tx = TrafficStats.getUidTxBytes(info.uid)
            MonitorAppRow(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                uid = info.uid,
                system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                rxBytes = rx,
                txBytes = tx,
                connectorState = registeredApp?.connectionState ?: "NONE",
                monitoringEnabled = policy?.monitoringEnabled,
                eventCount = eventCounts[info.packageName] ?: 0
            )
        }.sortedWith(compareByDescending<MonitorAppRow> { it.eventCount }.thenBy { it.label.lowercase() })
        val socketResult = if (Store.rootOk == true) Root.sh("ss -Htunap 2>/dev/null | head -n 80") else Root.Result(1, "")
        val signals = runCatching { com.mora.shield.security.NetworkRiskMonitor.inspect(context) }.getOrDefault(emptyList())
        val permissionApps = runCatching { com.mora.shield.security.PermissionAudit.collect(context) }.getOrDefault(emptyList())
        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()
        val todayEvents = events.count { it.occurredAtMs >= System.currentTimeMillis() - 24L * 60 * 60 * 1000 }
        runCatching { com.mora.shield.security.TrafficHistory.record(context, totalRx, totalTx, apps.map { Triple(it.packageName, it.rxBytes, it.txBytes) }, todayEvents) }
        MonitorSnapshot(
            overview = overview,
            apps = apps,
            events = events,
            totalRx = totalRx,
            totalTx = totalTx,
            sockets = socketResult.out.lines().filter { it.isNotBlank() },
            securitySignals = signals,
            permissionApps = permissionApps,
            socketsSupported = socketResult.ok && socketResult.out.isNotBlank(),
            updatedAt = System.currentTimeMillis()
        )
    } catch (e: Exception) {
        MonitorSnapshot(error = e.message ?: "Gagal membaca data monitor")
    }
}

@Composable
private fun DashboardMonitor(data: MonitorSnapshot, loading: Boolean) {
    val connected = data.apps.count { it.connectorState == "CONNECTED" }
    val recent = data.events.count { System.currentTimeMillis() - it.occurredAtMs < 24L * 60 * 60 * 1000 }
    val alerts = data.events.count { it.severity.equals("HIGH", true) || it.severity.equals("CRITICAL", true) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Ringkasan perangkat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (loading) item { Text("Mengambil data perangkat…", style = MaterialTheme.typography.bodySmall) }
        if (data.error.isNotEmpty()) item { EmptyHint(data.error) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonitorStat("Aplikasi", data.apps.size.toString(), Modifier.weight(1f))
                MonitorStat("Connector aktif", connected.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonitorStat("Event 24 jam", recent.toString(), Modifier.weight(1f))
                MonitorStat("Peringatan tinggi", alerts.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mora Core", fontWeight = FontWeight.SemiBold)
                    Text("${data.overview.registeredApps} aplikasi terdaftar · ${data.overview.connectedApps} terhubung · ${data.overview.storedEvents} event tersimpan")
                    Text("${data.overview.pendingCommands} perintah menunggu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lalu lintas jaringan sejak boot", fontWeight = FontWeight.SemiBold)
                    Text("Download: ${formatBytes(data.totalRx)}")
                    Text("Upload: ${formatBytes(data.totalTx)}")
                    Text("Angka berasal dari TrafficStats Android; bukan laju real-time.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Aktivitas terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(data.events.take(6), key = { it.eventId }) { event -> EventCard(event) }
        if (data.events.isEmpty()) item { EmptyHint("Belum ada event yang diterima Mora Core. Aplikasi harus mengirim event melalui Connector atau sumber pemantauan Shield.") }
        item { Text("Diperbarui: ${if (data.updatedAt == 0L) "belum tersedia" else fmtTime(data.updatedAt / 1000)} · refresh otomatis 10 detik", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MonitorStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineMonitor(data: MonitorSnapshot) {
    var filter by remember { mutableStateOf("Semua") }
    val types = listOf("Semua") + data.events.map { it.eventType }.distinct().take(12)
    val visible = if (filter == "Semua") data.events else data.events.filter { it.eventType == filter }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { type -> FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type) }) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.eventId }) { EventCard(it) }
            if (visible.isEmpty()) item { EmptyHint("Belum ada aktivitas untuk filter ini.") }
        }
    }
}

@Composable
private fun EventCard(event: ActivityEventEntity) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(event.title.ifBlank { event.eventType }, fontWeight = FontWeight.SemiBold)
            Text("${event.eventType} · ${event.severity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val pkg = event.affectedPackage.ifBlank { event.sourcePackage }
            if (pkg.isNotBlank()) Text(pkg, style = MaterialTheme.typography.bodySmall)
            if (event.detail.isNotBlank()) Text(event.detail, style = MaterialTheme.typography.bodySmall)
            Text(fmtTime(event.occurredAtMs / 1000), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NetworkMonitor(data: MonitorSnapshot) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val perAppDaily = com.mora.shield.security.TrafficHistory.appDailyDelta(context)
    val activeRows = data.apps.filter { it.rxBytes >= 0L || it.txBytes >= 0L }
        .sortedByDescending { (it.rxBytes.coerceAtLeast(0L) + it.txBytes.coerceAtLeast(0L)) }
        .take(20)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Network Monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            val daily = com.mora.shield.security.TrafficHistory.dailyDelta(androidx.compose.ui.platform.LocalContext.current)
            Text(daily?.let { "Perkiraan 24 jam dari sampel tersimpan: ↓ ${formatBytes(it.first)}  ↑ ${formatBytes(it.second)}" }
                ?: "Riwayat 24 jam akan tersedia setelah minimal dua sampel (interval 15 menit).", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total perangkat sejak boot", fontWeight = FontWeight.SemiBold)
                    Text("Download: ${formatBytes(data.totalRx)}")
                    Text("Upload: ${formatBytes(data.totalTx)}")
                    Text("Counter kumulatif dari Android TrafficStats. Nilai -1 berarti tidak didukung oleh perangkat/ROM.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Penggunaan per UID aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(activeRows, key = { it.packageName }) { app ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app.label, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sejak boot: ↓ ${formatBytes(app.rxBytes)}  ↑ ${formatBytes(app.txBytes)}")
                    perAppDaily[app.packageName]?.let { Text("Sampel 24 jam: ↓ ${formatBytes(it.first)}  ↑ ${formatBytes(it.second)}", style = MaterialTheme.typography.bodySmall) }
                    val series = com.mora.shield.security.TrafficHistory.appDailySeries(context, app.packageName)
                    if (series.size >= 2) {
                        val lineColor = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                            val maxValue = (series.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
                            val stepX = size.width / (series.size - 1).toFloat()
                            val points = series.mapIndexed { index, value ->
                                Offset(index * stepX, size.height - (value.toFloat() / maxValue * size.height))
                            }
                            points.zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, strokeWidth = 3f) }
                        }
                    }
                }
            }
        }
        item { Text("Koneksi aktif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (data.socketsSupported) {
            items(data.sockets) { line ->
                Card(Modifier.fillMaxWidth()) { Text(line, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
            }
        } else {
            item { EmptyHint("Daftar koneksi aktif belum tersedia. Perintah ss tidak tersedia atau tidak mengembalikan data pada ROM ini. Counter penggunaan data tetap ditampilkan bila didukung.") }
        }
        item { Text("Data diperbarui otomatis setiap 10 detik saat halaman monitor terbuka. Koneksi aktif bersifat snapshot, bukan packet capture.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SecurityMonitor(data: MonitorSnapshot) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val createDocument = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingExport != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExport!!) } }
                .onSuccess { status = "Laporan terenkripsi berhasil diekspor." }
                .onFailure { status = "Ekspor gagal: ${it.message}" }
            pendingExport = null
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val candidates = data.permissionApps.filter { row ->
        !row.isSystem && row.lastUsedMs != null && row.lastUsedMs < System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000 &&
            row.packageName != context.packageName
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Security & Permission Audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("Sinyal jaringan hanya indikator heuristik, bukan bukti penyadapan atau BTS palsu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Atur Usage Access") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Atur izin aplikasi") }
            }
        }
        items(data.securitySignals) { signal ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${signal.title} · ${signal.severity}", fontWeight = FontWeight.SemiBold)
                    Text(signal.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (data.securitySignals.isEmpty()) item { EmptyHint("Belum ada perubahan jaringan yang terdeteksi atau izin yang diperlukan belum tersedia.") }
        item { Text("Izin sensitif per aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { Text("Daftar ini menunjukkan izin sensitif yang saat ini diberikan. Waktu terakhir dipakai adalah aktivitas aplikasi secara umum dari UsageStats, bukan bukti kapan izin tertentu digunakan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(data.permissionApps, key = { it.packageName }) { app ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.label, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                    Text(app.granted.joinToString(" · ") { it.substringAfterLast('.') }, style = MaterialTheme.typography.bodySmall)
                    Text("Terakhir digunakan: ${app.lastUsedMs?.let { fmtTime(it / 1000) } ?: "data Usage Access tidak tersedia"}", style = MaterialTheme.typography.labelSmall)
                    if (!app.isSystem && app.enabled) {
                        androidx.compose.material3.TextButton(onClick = { selectedPackage = app.packageName }) { Text("Sembunyikan aplikasi…") }
                    } else if (!app.isSystem) {
                        androidx.compose.material3.TextButton(onClick = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val result = com.mora.shield.security.SensitiveAppActions.enable(app.packageName)
                                withContext(Dispatchers.Main) { status = if (result.ok) "Aplikasi diaktifkan kembali: ${app.packageName}" else "Gagal: ${result.out}" }
                            }
                        }) { Text("Aktifkan kembali") }
                    }
                }
            }
        }
        item {
            androidx.compose.material3.Button(onClick = { showExport = true }, modifier = Modifier.fillMaxWidth()) { Text("Ekspor laporan audit terenkripsi") }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cabut izin otomatis — mode uji", fontWeight = FontWeight.SemiBold)
                    Text("${candidates.size} aplikasi pengguna tidak aktif lebih dari 90 hari terdeteksi sebagai kandidat. Belum ada izin yang dicabut. Tambahkan pengecualian dan tinjau satu per satu sebelum mengaktifkan tindakan nyata.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (status.isNotBlank()) item { Text(status, style = MaterialTheme.typography.bodySmall) }
    }
    if (showExport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Enkripsi laporan audit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sandi minimal 10 karakter. Simpan sandi terpisah; tanpa sandi laporan tidak dapat dibuka.")
                    androidx.compose.material3.OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("Sandi laporan") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = exportPassword.length >= 10, onClick = {
                    val password = exportPassword.toCharArray()
                    exportPassword = ""
                    showExport = false
                    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                        val plain = data.events.joinToString("\n") { e ->
                            listOf(e.occurredAtMs, e.sourcePackage, e.affectedPackage, e.eventType, e.severity, e.title, e.detail)
                                .joinToString("\t") { it.toString().replace("\n", " ").replace("\t", " ") }
                        }.toByteArray(Charsets.UTF_8)
                        val encrypted = runCatching { com.mora.shield.security.EncryptedAuditExport.encrypt(plain, password) }
                        password.fill('\u0000')
                        withContext(Dispatchers.Main) {
                            encrypted.onSuccess { pendingExport = it; createDocument.launch("mora-audit-${System.currentTimeMillis()}.mora") }
                                .onFailure { status = "Enkripsi gagal: ${it.message}" }
                        }
                    }
                }) { Text("Enkripsi & ekspor") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showExport = false; exportPassword = "" }) { Text("Batal") } }
        )
    }
    val pkg = selectedPackage
    if (pkg != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedPackage = null },
            title = { Text("Sembunyikan aplikasi?") },
            text = { Text("Mora Shield akan menjalankan pm disable-user untuk $pkg. Ikon dan akses aplikasi dinonaktifkan; data tetap ada dan tindakan ini bukan perlindungan forensik. Aplikasi dapat diaktifkan kembali dari Mora Shield jika root tersedia.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    selectedPackage = null
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val result = com.mora.shield.security.SensitiveAppActions.disable(pkg)
                        withContext(Dispatchers.Main) { status = if (result.ok) "Aplikasi dinonaktifkan: $pkg" else "Gagal: ${result.out}" }
                    }
                }) { Text("Nonaktifkan") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { selectedPackage = null }) { Text("Batal") } }
        )
    }
}

private fun formatBytes(value: Long): String {
    if (value < 0L) return "Tidak didukung"
    if (value < 1024L) return "$value B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    do { amount /= 1024.0; index++ } while (amount >= 1024.0 && index < units.lastIndex)
    return String.format(java.util.Locale.getDefault(), "%.1f %s", amount, units[index])
}
