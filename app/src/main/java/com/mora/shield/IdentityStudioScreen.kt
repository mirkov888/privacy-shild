@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mora.shield.identity.DeviceSnapshot
import com.mora.shield.identity.IdentityEngine
import com.mora.shield.identity.IdentityPlatform
import com.mora.shield.identity.IdentityProfile
import com.mora.shield.identity.IdentityProfileCatalog

@Composable
fun IdentityStudioScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val engine = remember(context) { IdentityEngine(context) }
    var platform by remember { mutableStateOf(IdentityPlatform.ANDROID) }
    var selected by remember { mutableStateOf(engine.selectedProfile()) }
    val device = remember { DeviceSnapshot.capture() }
    val profiles = remember(platform) { IdentityProfileCatalog.forPlatform(platform) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Mora Identity Studio", style = MaterialTheme.typography.headlineSmall)
                Text("Katalog profil kompatibilitas browser dan identitas Android asli perangkat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Identitas perangkat saat ini", style = MaterialTheme.typography.titleMedium)
                    Text("${device.manufacturer} ${device.model}", style = MaterialTheme.typography.bodyLarge)
                    Text("Brand: ${device.brand} • Device: ${device.device}", style = MaterialTheme.typography.bodySmall)
                    Text("Android ${device.androidRelease} (SDK ${device.sdkInt}) • Patch ${device.securityPatch.ifBlank { "tidak tersedia" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Platform profil browser", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdentityPlatform.values().take(3).forEach { p ->
                    FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdentityPlatform.values().drop(3).forEach { p ->
                    FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p.label) })
                }
            }
        }
        items(profiles, key = { it.id }) { profile ->
            IdentityProfileCard(profile, selected?.id == profile.id) {
                if (engine.select(profile.id)) {
                    selected = profile
                    Store.message = "Profil ${profile.model} dipilih untuk pengujian WebView Mora"
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profil terpilih", style = MaterialTheme.typography.titleMedium)
                    if (selected == null) {
                        Text("Belum ada profil dipilih.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("${selected!!.manufacturer} ${selected!!.model} • ${selected!!.browser}")
                        Text(selected!!.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(selected!!.userAgent)) }, Modifier.fillMaxWidth()) {
                            Text("Salin User-Agent")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val profile = engine.randomProfile(platform)
                            if (profile != null && engine.select(profile.id)) {
                                selected = profile
                                Store.message = "Profil acak dipilih: ${profile.model}"
                            }
                        }, Modifier.weight(1f)) { Text("Randomize") }
                        OutlinedButton(onClick = {
                            engine.clearSelection()
                            selected = null
                            Store.message = "Pilihan profil direset"
                        }, Modifier.weight(1f)) { Text("Reset") }
                    }
                }
            }
        }
        item {
            Text(
                "Batasan Phase 1–2: profil hanya disimpan di Mora Shield dan User-Agent dapat dipakai oleh WebView milik Mora setelah integrasi. Ini tidak mengubah Chrome/Firefox eksternal atau identitas sistem Android. Profil iOS/Windows/macOS/Linux untuk uji tampilan dan kompatibilitas situs. Fitur Per-App Device Identity yang sudah ada tetap terpisah dan tidak ditimpa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IdentityProfileCard(profile: IdentityProfile, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${profile.manufacturer} ${profile.model}", style = MaterialTheme.typography.titleMedium)
            Text("${profile.platform.label} • ${profile.osVersion} • ${profile.browser}", style = MaterialTheme.typography.bodySmall)
            Text(profile.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
