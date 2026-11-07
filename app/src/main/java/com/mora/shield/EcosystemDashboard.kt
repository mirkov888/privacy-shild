package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EcosystemDashboard() {
    val connected = Store.rootOk == true && Store.installed
    SectionCard("Dashboard ekosistem", "Status komponen utama Mora Shield") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(if (connected) "Aktif" else "Offline", "Mora Core", Modifier.weight(1f))
            StatTile("${Store.apps.size}", "Aplikasi", Modifier.weight(1f))
            StatTile("${Store.events.size}", "Kejadian", Modifier.weight(1f))
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EcosystemStatus("Akses root", when (Store.rootOk) { true -> "Diizinkan"; false -> "Belum diizinkan"; null -> "Memeriksa" })
            EcosystemStatus("Modul Shield", if (Store.installed) "Terhubung · ${Store.version}" else "Tidak terdeteksi")
            EcosystemStatus("Sinkronisasi", if (Store.lastSync > 0L) "Terakhir: " + fmtTime(Store.lastSync / 1000L) else "Belum tersinkron")
            EcosystemStatus("Kebijakan aktif", "${SCORE_KEYS.count { Store.cfg.on(it) }} dari ${SCORE_KEYS.size}")
        }
    }
}

@Composable
private fun EcosystemStatus(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}
