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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mora.shield.identity.DeviceSnapshot
import com.mora.shield.identity.FingerprintProtectionEngine
import com.mora.shield.identity.IdentityEngine
import com.mora.shield.identity.IdentityPlatform
import com.mora.shield.identity.IdentityProfileValidator
import com.mora.shield.identity.IdentityProtectionSettings
import com.mora.shield.identity.IdentityProtectionStore
import com.mora.shield.identity.IdentityProfileRecoveryManager
import com.mora.shield.identity.IdentityAndroidIntegration
import com.mora.shield.identity.IdentityAndroidIntegrationReport
import com.mora.shield.IdentityPrivacyAuditCard

@Composable
fun IdentityStudioScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val engine = remember(context) { IdentityEngine(context) }
    val protectionStore = remember(context) { IdentityProtectionStore(context) }
    var platform by remember { mutableStateOf(IdentityPlatform.ANDROID) }
    var selected by remember { mutableStateOf(engine.selectedProfile()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var customName by remember { mutableStateOf("") }
    var customUserAgent by remember { mutableStateOf("") }
    var profileQuery by remember { mutableStateOf("") }
    var showWebPreview by remember { mutableStateOf(true) }
    var protection by remember { mutableStateOf(protectionStore.load()) }
    var sessionSeed by remember { mutableStateOf(FingerprintProtectionEngine.newSessionSeed()) }
    val device = remember { DeviceSnapshot.capture() }
    val profiles = engine.profilesForPlatform(platform)
    val visibleProfiles = remember(profiles, profileQuery) {
        val query = profileQuery.trim()
        if (query.isEmpty()) profiles else profiles.filter { profile ->
            profile.model.contains(query, ignoreCase = true) ||
                profile.manufacturer.contains(query, ignoreCase = true) ||
                profile.browser.contains(query, ignoreCase = true) ||
                profile.purpose.contains(query, ignoreCase = true)
        }
    }
    val customProfiles = engine.customProfiles()
    @Suppress("UNUSED_VARIABLE") val refresh = refreshKey

    fun updateProtection(next: IdentityProtectionSettings) {
        protection = next
        protectionStore.save(next)
        sessionSeed = FingerprintProtectionEngine.newSessionSeed()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Mora Identity Studio", style = MaterialTheme.typography.headlineSmall)
                Text("Profil kompatibilitas browser dan perlindungan fingerprint untuk WebView milik Mora. Identitas Android asli tetap terpisah.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { IdentityAndroidIntegrationCard() }
        item { IdentityRecoveryCard(onRestored = {
            selected = engine.selectedProfile()
            protection = protectionStore.load()
            refreshKey++
            sessionSeed = FingerprintProtectionEngine.newSessionSeed()
        }) }
        item { IdentityPrivacyAuditCard() }
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("System & Fingerprint Protection", style = MaterialTheme.typography.titleMedium)
                    Text("Pengaturan ini aktif hanya di WebView lokal Mora Identity Studio. Bukan spoofing global untuk Chrome, Firefox, atau aplikasi lain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProtectionSwitch("Canvas protection", "Ganggu pembacaan pixel dengan perubahan minimal yang konsisten selama satu sesi.", protection.canvasProtection) {
                        updateProtection(protection.copy(canvasProtection = it))
                    }
                    ProtectionSwitch("WebGL protection", "Sembunyikan renderer detail dan blokir ekstensi debug renderer pada WebView preview.", protection.webGlProtection) {
                        updateProtection(protection.copy(webGlProtection = it))
                    }
                    ProtectionSwitch("Sinkronkan User-Agent + Client Hints", "Gunakan metadata native WebView bila fitur didukung dan profil memakai Chromium.", protection.syncClientHints) {
                        updateProtection(protection.copy(syncClientHints = it))
                    }
                    ProtectionSwitch("Randomisasi per sesi", "Seed Canvas baru setiap sesi; hasil tetap konsisten selama sesi tersebut.", protection.randomizePerSession) {
                        updateProtection(protection.copy(randomizePerSession = it))
                    }
                    ProtectionSwitch("Proteksi WebRTC & media", "Nonaktifkan koneksi peer dan akses kamera/mikrofon hanya di preview Mora.", protection.webRtcProtection) {
                        updateProtection(protection.copy(webRtcProtection = it))
                    }
                    Text("Profile isolation aktif untuk preview Mora. WebRTC dan permintaan kamera/mikrofon diblokir bila proteksinya aktif. Pengaturan ini tidak mengubah browser eksternal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { IdentityRandomizationCard(platform) }
        item {
            Text("Platform profil browser", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdentityPlatform.values().take(3).forEach { p -> FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdentityPlatform.values().drop(3).forEach { p -> FilterChip(selected = platform == p, onClick = { platform = p }, label = { Text(p.label) }) }
            }
        }
        item {
            OutlinedTextField(
                value = profileQuery,
                onValueChange = { profileQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cari merek, model, atau browser") },
                singleLine = true
            )
            Text("${visibleProfiles.size} profil cocok dari ${profiles.size} profil ${platform.label}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(visibleProfiles, key = { it.id }) { profile ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Card(
                    onClick = {
                        if (engine.select(profile.id)) {
                            selected = profile
                            sessionSeed = FingerprintProtectionEngine.newSessionSeed()
                            Store.message = "Profil ${profile.model} dipilih untuk WebView Mora"
                        }
                    },
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
                        if (showWebPreview) {
                            val previewSeed = if (protection.randomizePerSession) sessionSeed else "profile-${selected!!.id}"
                            IdentityWebViewPreview(selected!!, protection, previewSeed)
                            TextButton(onClick = { sessionSeed = FingerprintProtectionEngine.newSessionSeed() }) { Text("Buat sesi baru") }
                        }
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
                        Button(onClick = {
                            val p = engine.randomProfile(platform)
                            if (p != null && engine.select(p.id)) {
                                selected = p
                                sessionSeed = FingerprintProtectionEngine.newSessionSeed()
                                Store.message = "Profil acak dipilih: ${p.model}"
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
                                engine.select(saved.id)
                                selected = saved
                                customName = ""
                                customUserAgent = ""
                                refreshKey++
                                sessionSeed = FingerprintProtectionEngine.newSessionSeed()
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
            Text("Batasan identitas: profil iOS/Windows/macOS/Linux hanya untuk kompatibilitas User-Agent/preview; WebView tetap memakai Chromium Android. Build fingerprint, versi keamanan, IMEI, serial number, hardware attestation, dan Play Integrity tidak diubah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProtectionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@Composable
private fun IdentityAndroidIntegrationCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<IdentityAndroidIntegrationReport?>(null) }
    var scanning by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Android Integration & Compatibility", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pemeriksaan bersifat read-only. Scan root hanya berjalan setelah tombol ditekan; tidak ada modul yang dipasang atau diubah.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = !scanning,
                onClick = {
                    scanning = true
                    scope.launch {
                        report = withContext(Dispatchers.IO) {
                            IdentityAndroidIntegration.scan(context.applicationContext)
                        }
                        scanning = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scanning) "Memeriksa…" else "Scan kompatibilitas Android")
            }
            OutlinedButton(
                onClick = {
                    Store.message = if (IdentityAndroidIntegration.openWebViewSettings(context)) {
                        "Pengaturan WebView Android dibuka"
                    } else {
                        "Pengaturan WebView tidak tersedia pada ROM ini"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Buka pengaturan provider WebView") }

            report?.let { r ->
                Text("WebView: ${r.webViewProvider} · ${r.webViewVersion}", style = MaterialTheme.typography.bodyMedium)
                Text("User-Agent Metadata: ${if (r.userAgentMetadataSupported) "didukung" else "tidak didukung"}")
                Text("Document Start Script: ${if (r.documentStartScriptSupported) "didukung" else "tidak didukung"}")
                Text("Root: ${r.rootManager}")
                Text("LSPosed: ${r.lsPosedStatus}", style = MaterialTheme.typography.bodySmall)
                Text("Browser terpasang yang dapat membuka HTTPS: ${r.browsers.size}")
                r.browsers.forEach { browser ->
                    Text("• ${browser.label} (${browser.packageName})", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Browser eksternal hanya dideteksi. Mora tidak menerapkan User-Agent, hook, atau fingerprint profile ke Chrome/Firefox/Brave dan browser lain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text("Belum dipindai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IdentityRecoveryCard(onRestored: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val recovery = remember(context) { IdentityProfileRecoveryManager(context) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmRollback by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup, Validation & Rollback", style = MaterialTheme.typography.titleMedium)
            Text(
                "Backup mencakup profil browser kustom/favorit, profil aktif, pengaturan proteksi, dan label kompatibilitas per-app. Data keamanan perangkat tidak diekspor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    val json = recovery.exportBackupJson()
                    clipboard.setText(AnnotatedString(json))
                    status = "Backup lengkap disalin ke clipboard"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ekspor backup JSON") }
            OutlinedButton(
                onClick = {
                    val raw = clipboard.getText()?.text.orEmpty()
                    val validation = IdentityProfileRecoveryManager.validateBackupJson(raw)
                    if (validation == null) confirmRestore = true else status = validation
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Validasi & impor dari clipboard") }
            OutlinedButton(
                enabled = recovery.hasRollbackPoint(),
                onClick = { confirmRollback = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Rollback ke backup sebelumnya") }
            Text(
                if (recovery.hasRollbackPoint()) "Rollback point tersedia." else "Rollback point akan dibuat otomatis sebelum impor backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Pulihkan backup Mora?") },
            text = { Text("Profil Identity Studio, pengaturan proteksi, dan profil kompatibilitas per-app akan diganti sesuai isi clipboard. Snapshot kondisi saat ini disimpan sebagai rollback.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    val result = recovery.restoreBackupJson(clipboard.getText()?.text.orEmpty())
                    status = result.message + if (result.success) " · ${result.restoredAppProfiles} profil per-app" else ""
                    if (result.success) onRestored()
                }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Batal") } }
        )
    }

    if (confirmRollback) {
        AlertDialog(
            onDismissRequest = { confirmRollback = false },
            title = { Text("Rollback perubahan terakhir?") },
            text = { Text("Konfigurasi Identity Studio akan dikembalikan ke snapshot sebelumnya. Kondisi saat ini disimpan sehingga rollback dapat dibatalkan dengan operasi berikutnya.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRollback = false
                    val result = recovery.rollbackLastChange()
                    status = result.message
                    if (result.success) onRestored()
                }) { Text("Rollback") }
            },
            dismissButton = { TextButton(onClick = { confirmRollback = false }) { Text("Batal") } }
        )
    }
}
