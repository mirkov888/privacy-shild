package com.mora.shield

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mora.shield.core.MoraCore
import kotlin.concurrent.thread

/** Status ringkas Core dan connector di tab Alat. */
@Composable
fun MoraCoreCard() {
    val context = LocalContext.current
    SectionCard(
        "Mora Core & Connector",
        "Pusat event lokal dan jembatan aman untuk aplikasi Mora lain."
    ) {
        ActionRow("Status connector", "Periksa modul Shield dan telemetry perangkat", "Periksa") {
            thread {
                MoraCore.initialize()
                val reports = MoraCore.inspectConnectors()
                val reportText = if (reports.isEmpty()) {
                    "Belum ada connector terdaftar."
                } else {
                    reports.joinToString("\n\n") {
                        it.displayName + " — " + it.state.label + "\n" + it.message
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    Store.showText("Mora Core · Status", reportText)
                }
            }
        }
        Divider()
        ActionRow("Riwayat event terpusat", "Maksimal 500 event terakhir dari Shield dan aplikasi connector", "Lihat") {
            val events = MoraCore.recentEvents(50)
            val body = if (events.isEmpty()) {
                "Belum ada event. Buka aplikasi dan pastikan pemantauan aktif."
            } else {
                events.joinToString("\n\n") { event ->
                    val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.occurredAtMs))
                    time + " · " + event.severity.name + "\n" + event.title +
                        "\nSumber: " + event.sourceId +
                        (if (event.packageName.isBlank()) "" else "\nPaket: " + event.packageName) +
                        (if (event.detail.isBlank()) "" else "\n" + event.detail)
                }
            }
            Store.showText("Mora Core · Event", body)
        }
    }
}
