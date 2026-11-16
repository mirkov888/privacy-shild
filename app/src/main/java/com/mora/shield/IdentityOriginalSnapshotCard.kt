package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.shield.identity.IdentityOriginalDeviceSnapshot
import com.mora.shield.identity.IdentityOriginalDeviceSnapshotStore
import java.text.DateFormat
import java.util.Date

@Composable
fun IdentityOriginalSnapshotCard() {
    val context = LocalContext.current
    val store = remember(context) { IdentityOriginalDeviceSnapshotStore(context) }
    var snapshot by remember(store) { mutableStateOf(store.loadOrCapture()) }
    var status by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Backup Identitas Asli", style = MaterialTheme.typography.titleMedium)
            Text(
                "Baseline lokal perangkat disimpan untuk referensi dan ikut dimasukkan ke backup Identity Studio. Ini snapshot baca-saja, bukan alat untuk mengubah atau memulihkan identitas sistem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${snapshot.device.manufacturer} ${snapshot.device.model}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Brand: ${snapshot.device.brand} · Device: ${snapshot.device.device} · Product: ${snapshot.device.product}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Android ${snapshot.device.androidRelease} (SDK ${snapshot.device.sdkInt}) · Patch ${snapshot.device.securityPatch.ifBlank { "tidak tersedia" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Disimpan: ${DateFormat.getDateTimeInstance().format(Date(snapshot.capturedAtEpochMs))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    snapshot = store.captureNow()
                    status = "Baseline diperbarui dari nilai perangkat saat ini."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ambil ulang baseline perangkat") }
            Text(
                "Tidak menyimpan IMEI, serial number, Android ID, MAC address, Build fingerprint, atau data attestation. Import backup dari perangkat lain tidak menimpa baseline lokal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
