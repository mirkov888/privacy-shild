package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.shield.identity.IdentityPlatform
import com.mora.shield.identity.IdentityRandomizationEngine
import com.mora.shield.identity.IdentityRandomizationScheduler
import com.mora.shield.identity.IdentityRandomizationStore

@Composable
fun IdentityRandomizationCard(platform: IdentityPlatform, onRandomized: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember(context) { IdentityRandomizationStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var seed by remember(settings.seed) { mutableStateOf(settings.seed) }
    var status by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Randomizer Engine", style = MaterialTheme.typography.titleMedium)
            Text(
                "Seed membuat urutan pilihan bisa diulang. Jadwal berjalan di background Android dan dapat ditunda oleh penghemat baterai.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Randomisasi otomatis")
                    Text("Profil browser Mora dan opsional profil per-app yang sudah aktif.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.enabled, onCheckedChange = { settings = settings.copy(enabled = it) })
            }
            Text("Interval jadwal", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(6, 12, 24, 48, 168).forEach { hours ->
                    FilterChip(
                        selected = settings.intervalHours == hours,
                        onClick = { settings = settings.copy(intervalHours = hours) },
                        label = { Text(if (hours == 168) "7 hari" else hours.toString() + "j") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Randomisasi per aplikasi")
                    Text("Hanya profil aplikasi yang sudah diaktifkan; hanya label kompatibilitas non-keamanan yang dipilih ulang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.randomizeEnabledApps, onCheckedChange = { settings = settings.copy(randomizeEnabledApps = it) })
            }
            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Seed yang bisa digunakan kembali") },
                singleLine = true
            )
            Button(
                onClick = {
                    runCatching {
                        val configured = store.save(settings.copy(platform = platform, seed = seed))
                        settings = configured
                        seed = configured.seed
                        IdentityRandomizationScheduler.sync(context, configured)
                        status = if (configured.enabled) {
                            "Jadwal aktif setiap " + configured.intervalHours + " jam."
                        } else {
                            "Jadwal otomatis dimatikan."
                        }
                    }.onFailure { status = it.message ?: "Pengaturan randomisasi tidak valid" }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan pengaturan randomizer") }
            OutlinedButton(
                onClick = {
                    runCatching {
                        val configured = store.save(settings.copy(platform = platform, seed = seed))
                        settings = configured
                        seed = configured.seed
                        IdentityRandomizationScheduler.sync(context, configured)
                        val result = IdentityRandomizationEngine.runNow(context, configured)
                        settings = store.load()
                        seed = settings.seed
                        status = "Run #" + result.runIndex + ": " +
                            (result.selectedProfile ?: "tidak ada profil") + "; " +
                            result.randomizedApps + " profil per-app diacak."
                        onRandomized()
                    }.onFailure { status = it.message ?: "Randomisasi gagal" }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Randomize sekarang") }
            Text(
                "Seed tersimpan: " + settings.seed.take(12) + "… · Run: " + store.runIndex(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
