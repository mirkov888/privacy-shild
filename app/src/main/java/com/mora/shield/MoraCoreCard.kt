package com.mora.shield

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mora.shield.core.ActivityEventEntity
import com.mora.shield.core.CoreOverview
import com.mora.shield.core.MoraCore
import kotlinx.coroutines.delay
import kotlin.concurrent.thread

/** Ringkasan awal pusat kontrol Mora. Nilai berasal dari database Core, bukan data demo. */
@Composable
fun MoraCoreCard() {
    val context = LocalContext.current
    var overview by remember { mutableStateOf(CoreOverview()) }

    LaunchedEffect(Unit) {
        MoraCore.initialize(context)
        while (true) {
            overview = runCatching { MoraCore.overview() }.getOrDefault(CoreOverview())
            delay(5000)
        }
    }

    SectionCard(
        "Mora Core · Master Controller",
        "Pusat registrasi aplikasi, kebijakan, timeline aktivitas, dan audit perintah."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Aplikasi terdaftar", style = MaterialTheme.typography.bodyMedium)
            Text(overview.registeredApps.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Connector aktif", style = MaterialTheme.typography.bodyMedium)
            Text(overview.connectedApps.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Event tersimpan", style = MaterialTheme.typography.bodyMedium)
            Text(overview.storedEvents.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Perintah menunggu", style = MaterialTheme.typography.bodyMedium)
            Text(overview.pendingCommands.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Divider()
        ActionRow(
            "Aplikasi Mora terdaftar",
            "Lihat status instalasi, identitas paket, dan kebijakan default",
            "Lihat"
        ) {
            thread {
                val apps = runCatching { kotlinx.coroutines.runBlocking { MoraCore.registeredApps() } }.getOrDefault(emptyList())
                val body = if (apps.isEmpty()) "Registry belum siap." else apps.joinToString("\n\n") {
                    "${it.displayName}\n${it.packageName}\nVersi: ${it.versionName}\nStatus: ${it.connectionState}\nAPI: ${it.apiVersion}"
                }
                Handler(Looper.getMainLooper()).post { Store.showText("Mora Core · App Registry", body) }
            }
        }
        Divider()
        ActionRow(
            "Timeline terpusat",
            "Maksimal 5.000 event terbaru disimpan secara lokal",
            "Lihat"
        ) {
            thread {
                val events = runCatching { kotlinx.coroutines.runBlocking { MoraCore.recentEvents(100) } }.getOrDefault(emptyList())
                val body = formatCoreEvents(events)
                Handler(Looper.getMainLooper()).post { Store.showText("Mora Core · Activity Timeline", body) }
            }
        }
        Text(
            "Mode aman: akses perintah connector dinonaktifkan sampai capability diizinkan melalui kebijakan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCoreEvents(events: List<ActivityEventEntity>): String {
    if (events.isEmpty()) return "Belum ada aktivitas yang diterima Mora Core."
    return events.joinToString("\n\n") { event ->
        val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.occurredAtMs))
        buildString {
            append(time)
            append(" · ")
            append(event.severity.uppercase())
            append("\n")
            append(event.title)
            append("\nSumber: ")
            append(event.sourcePackage)
            if (event.affectedPackage.isNotBlank()) {
                append("\nPaket terkait: ")
                append(event.affectedPackage)
            }
            if (event.detail.isNotBlank()) {
                append("\n")
                append(event.detail)
            }
        }
    }
}
