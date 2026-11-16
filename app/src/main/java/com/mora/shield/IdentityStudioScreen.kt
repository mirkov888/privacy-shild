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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.mora.shield.identity.IdentityProfileValidator

@Composable
fun IdentityStudioScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val engine = remember(context) { IdentityEngine(context) }
    var platform by remember { mutableStateOf(IdentityPlatform.ANDROID) }
    var selected by remember { mutableStateOf(engine.selectedProfile()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var customName by remember { mutableStateOf("") }
    var customUserAgent by remember { mutableStateOf("") }
    var showWebPreview by remember { mutableStateOf(true) }
    val device = remember { DeviceSnapshot.capture() }
    val profiles = engine.profilesForPlatform(platform)
    val customProfiles = engine.customProfiles()
    @Suppress("UNUSED_VARIABLE") val refresh = refreshKey

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Mora Identity Studio", style = MaterialTheme.typography.headlineSmall)
                Text("Profil kompatibilitas browser untuk WebView milik Mora, tanpa mengubah identitas Android asli perangkat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                IdentityPlatform.values().take(3).forEach { p -> FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdentityPlatform.values().drop(3).forEach { p -> FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p.label) }) }
            }
        }
        items(profiles, key = { it.id }) { profile ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Card(
                    onClick = { if (engine.select(profile.id)) { selected = profile; Store.message = "Profil ${profile.model} dipilih untuk WebView Mora" } },
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = if (selected?.id == profile.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${profile.manufacturer} ${profile.model}", style = MaterialTheme.typography.titleMedium)
                        Text("${profile.platform.label} • ${profile.osVersion} • ${profile.browser}", style = MaterialTheme.typography.bodySmall)
                        Text(profile.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { engine.toggleFavorite(profile.id); refreshKey++ }) { Text(if (engine.isFavorite(profile.id)) "★" else "☆") }
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
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(selected!!.userAgent)) }, Modifier.fillMaxWidth()) { Text("Salin User-Agent") }
                        OutlinedButton(onClick = { showWebPreview = !showWebPreview }, Modifier.fillMaxWidth()) { Text(if (showWebPreview) "Sembunyikan WebView Preview" else "Tes di WebView Mora") }
                        if (showWebPreview) IdentityWebViewPreview(selected!!)
                        if (engine.isCustomProfile(selected!!.id)) {
                            TextButton(onClick = {
                                engine.deleteCustomProfile(selected!!.id)
                                selected = engine.selectedProfile()
                                refreshKey++
                                Store.message = "Profil kustom dihapus"
                            }) { Text("Hapus profil kustom") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { val p = engine.randomProfile(platform); if (p != null && engine.select(p.id)) { selected = p; Store.message = "Profil acak dipilih: ${p.model}" } }, Modifier.weight(1f)) { Text("Randomize") }
                        OutlinedButton(onClick = { engine.clearSelection(); selected = null; Store.message = "Pilihan profil direset" }, Modifier.weight(1f)) { Text("Reset") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pengelola profil kustom", style = MaterialTheme.typography.titleMedium)
                    Text("Profil baru masuk ke platform yang sedang dipilih: ${platform.label}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = customName, onValueChange = { customName = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nama profil") }, singleLine = true)
                    OutlinedTextField(value = customUserAgent, onValueChange = { customUserAgent = it.take(512) }, modifier = Modifier.fillMaxWidth(), label = { Text("User-Agent") }, minLines = 3)
                    Button(onClick = {
                        val error = IdentityProfileValidator.validate(customName, customUserAgent)
                        if (error != null) Store.message = error
                        else {
                            val saved = engine.saveCustomProfile(platform, customName, customUserAgent)
                            if (saved != null) {
                                engine.select(saved.id); selected = saved; customName = ""; customUserAgent = ""; refreshKey++
                                Store.message = "Profil kustom disimpan"
                            } else Store.message = "Gagal menyimpan profil"
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Simpan profil kustom") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(engine.exportCustomProfilesJson())); Store.message = "JSON profil kustom disalin" }, modifier = Modifier.weight(1f)) { Text("Ekspor JSON") }
                        OutlinedButton(onClick = {
                            val added = engine.importCustomProfilesJson(clipboard.getText()?.text.orEmpty())
                            refreshKey++
                            Store.message = if (added > 0) "$added profil berhasil diimpor" else "Tidak ada profil valid di clipboard"
                        }, modifier = Modifier.weight(1f)) { Text("Impor clipboard") }
                    }
                    Text("Profil kustom tersimpan: ${customProfiles.size}. Favorit ditandai ★ dan diurutkan lebih dulu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Batasan: User-Agent hanya diterapkan pada WebView preview lokal milik Mora. Preview memblokir request jaringan; profil iOS/Windows/macOS/Linux tidak mengubah Android menjadi sistem operasi tersebut. Chrome, Firefox, aplikasi lain, Build properties, hardware ID, dan Play Integrity tidak disentuh. Per-App Device Identity tetap terpisah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
