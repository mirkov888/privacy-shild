package com.mora.shield

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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Layar aktif")
            Text(ScreenTimeTracker.format(snapshot.screenOnMs), style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Standby")
            Text(ScreenTimeTracker.format(snapshot.standbyMs), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (snapshot.isScreenOn) "Status: layar menyala" else "Status: layar mati / standby",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
