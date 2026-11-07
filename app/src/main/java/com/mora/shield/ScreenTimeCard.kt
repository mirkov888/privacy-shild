package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ScreenTimeCard() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(ScreenTimeTracker.snapshot(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            snapshot = ScreenTimeTracker.tick(context, pm.isInteractive)
            delay(5000L)
        }
    }
    SectionCard("Screen Time · Hari ini", "Reset otomatis pukul 00:00 waktu lokal. Standby dihitung saat perangkat aktif tetapi layar mati.") {
        val total = (snapshot.screenOnMs + snapshot.standbyMs).coerceAtLeast(1L)
        val activeRatio = (snapshot.screenOnMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Layar aktif", style = MaterialTheme.typography.bodyMedium)
                Text(ScreenTimeTracker.format(snapshot.screenOnMs), style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(progress = { activeRatio }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Standby", style = MaterialTheme.typography.bodyMedium)
                Text(ScreenTimeTracker.format(snapshot.standbyMs), style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { 1f - activeRatio },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                if (snapshot.isScreenOn) "Status: layar sedang menyala" else "Status: layar mati · standby",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
