package com.mora.shield

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppLocationScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LocationApp>>(emptyList()) }
    var selected by remember { mutableStateOf<LocationApp?>(null) }
    var profile by remember { mutableStateOf<PerAppLocationProfile?>(null) }
    var presets by remember { mutableStateOf<List<LocationPreset>>(emptyList()) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var latText by remember { mutableStateOf("0.0") }
    var lonText by remember { mutableStateOf("0.0") }
    var radiusText by remember { mutableStateOf("1000") }
    var intervalText by remember { mutableStateOf("15") }
    var presetName by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var backendActive by remember { mutableStateOf(false) }
    var moduleInstalled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = runCatching {
            context.packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { AppControlManager.isSafeThirdPartyPackage(it.packageName) }
                .map { LocationApp(it.packageName, context.packageManager.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
        }.getOrDefault(emptyList())
        moduleInstalled = runCatching { context.packageManager.getPackageInfo("com.mora.shield.locationhook", 0) }.isSuccess
    }

    fun refreshBackend(pkg: String) {
        val beat = PerAppLocationStore.backendHeartbeat(context, pkg)
        backendActive = beat > 0L && System.currentTimeMillis() - beat < 120_000L
    }

    fun selectApp(app: LocationApp) {
        selected = app
        val p = PerAppLocationStore.load(context, app.packageName)
        profile = p
        presets = PerAppLocationStore.presets(context, app.packageName)
        selectedPresetId = null
        latText = p.latitude.toString()
        lonText = p.longitude.toString()
        radiusText = p.radiusMeters.toString()
        intervalText = p.intervalMinutes.toString()
        presetName = ""
        refreshBackend(app.packageName)
        status = null
    }

    LaunchedEffect(selected?.packageName) {
        while (selected != null) {
            selected?.packageName?.let { refreshBackend(it) }
            delay(5_000L)
        }
    }

    fun profileFromFields(): PerAppLocationProfile? {
        val p = profile ?: return null
        return p.copy(
            latitude = latText.toDoubleOrNull() ?: Double.NaN,
            longitude = lonText.toDoubleOrNull() ?: Double.NaN,
            radiusMeters = radiusText.toIntOrNull() ?: -1,
            intervalMinutes = intervalText.toIntOrNull() ?: -1
        )
    }

    Column(Modifier.fillMaxSize()) {
        PageIntro("Per-App Location Control", "Profil, preset, dan aktivasi lokasi disimpan terpisah untuk setiap aplikasi.")
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (moduleInstalled) "Mora Location Hook terpasang" else "Companion LSPosed belum terpasang",
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (moduleInstalled) "Pilih aplikasi target pada Scope LSPosed. Status aktif muncul setelah proses target memuat hook."
                    else "Backend per-aplikasi memerlukan APK Mora Location Hook dan LSPosed. Tidak ada fallback ke mock location global.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (selected == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    PerAppLocationStore.disableAll(context)
                    status = "Emergency disable: semua profil spoofing dinonaktifkan."
                }) { Text("Emergency disable all") }
            }
            OutlinedTextField(search, { search = it }, label = { Text("Cari aplikasi") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }, key = { it.packageName }) { app ->
                    val saved = PerAppLocationStore.load(context, app.packageName)
                    Card(Modifier.fillMaxWidth().clickable { selectApp(app) }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                                Text(saved.mode.title + " • " + if (saved.enabled) "Aktif diminta" else "Nonaktif",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Atur ›")
                        }
                    }
                }
            }
        } else {
            val app = selected!!
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = null; profile = null; status = null }) { Text("‹ Daftar aplikasi") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        PerAppLocationStore.disableAll(context)
                        profile = profile?.copy(enabled = false)
                        status = "Emergency disable: semua profil spoofing dinonaktifkan."
                    }) { Text("Disable all") }
                }
                Text(app.label, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (backendActive) "Backend aktif pada proses aplikasi" else "Backend belum terdeteksi")
                        Text(if (backendActive) "Heartbeat LSPosed terdeteksi" else "Pastikan modul diaktifkan dan package ini dipilih pada Scope LSPosed.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = profile?.enabled == true,
                        enabled = backendActive && profile?.mode != LocationMode.REAL,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                val next = profile?.copy(enabled = false) ?: return@Switch
                                PerAppLocationStore.save(context, next)
                                profile = next
                                status = "Lokasi asli dipulihkan untuk " + app.label
                            } else {
                                val next = profileFromFields()?.copy(enabled = true) ?: return@Switch
                                val error = LocationSpoofEngine.validate(next)
                                if (error != null) status = error else {
                                    PerAppLocationStore.save(context, next)
                                    profile = next
                                    status = "Spoofing diaktifkan untuk " + app.label
                                }
                            }
                        }
                    )
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(profile?.mode?.title ?: "Lokasi asli", {}, readOnly = true,
                        label = { Text("Mode lokasi") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LocationMode.values().forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.title) }, onClick = {
                                val next = profile?.copy(mode = mode, enabled = false)
                                if (next != null) {
                                    val error = LocationSpoofEngine.validate(next)
                                    if (error == null) {
                                        PerAppLocationStore.save(context, next)
                                        profile = next
                                    } else {
                                        profile = next
                                        status = error
                                    }
                                }
                                expanded = false
                                if (mode == LocationMode.REAL) status = "Mode lokasi asli dipilih; spoofing langsung dinonaktifkan."
                            })
                        }
                    }
                }
                if (profile?.mode == LocationMode.STATIC || profile?.mode == LocationMode.RADIUS) {
                    OutlinedTextField(latText, { latText = it }, label = { Text("Latitude (-90..90)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(lonText, { lonText = it }, label = { Text("Longitude (-180..180)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (profile?.mode == LocationMode.RADIUS) {
                    OutlinedTextField(radiusText, { radiusText = it }, label = { Text("Radius meter (1–500000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (profile?.mode == LocationMode.RANDOM || profile?.mode == LocationMode.RADIUS) {
                    OutlinedTextField(intervalText, { intervalText = it }, label = { Text("Interval random menit (1–1440)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = {
                    val next = profileFromFields()
                    val error = if (next == null) "Profil tidak valid." else LocationSpoofEngine.validate(next)
                    if (error != null) status = error else {
                        PerAppLocationStore.save(context, next!!)
                        profile = next
                        status = "Profil tersimpan untuk " + app.label + ". Status aktivasi tidak berubah."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Simpan profil") }

                Divider()
                Text("Preset lokasi untuk " + app.label, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(expanded = presetMenuExpanded, onExpandedChange = { presetMenuExpanded = it }) {
                    OutlinedTextField(
                        presets.firstOrNull { it.id == selectedPresetId }?.name ?: "Pilih preset tersimpan",
                        {}, readOnly = true, label = { Text("Preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = presetMenuExpanded, onDismissRequest = { presetMenuExpanded = false }) {
                        presets.forEach { preset ->
                            DropdownMenuItem(text = { Text(preset.name) }, onClick = {
                                selectedPresetId = preset.id
                                val next = profile?.copy(mode = preset.mode, latitude = preset.latitude, longitude = preset.longitude,
                                    radiusMeters = preset.radiusMeters, intervalMinutes = preset.intervalMinutes, enabled = false)
                                if (next != null) {
                                    PerAppLocationStore.save(context, next)
                                    profile = next
                                }
                                latText = preset.latitude.toString()
                                lonText = preset.longitude.toString()
                                radiusText = preset.radiusMeters.toString()
                                intervalText = preset.intervalMinutes.toString()
                                presetMenuExpanded = false
                                status = "Preset diterapkan ke editor. Tekan Simpan profil untuk menyimpan."
                            })
                        }
                    }
                }
                OutlinedTextField(presetName, { presetName = it }, label = { Text("Nama preset") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val next = profileFromFields()
                        val error = if (next == null) "Profil tidak valid." else LocationSpoofEngine.validate(next)
                        when {
                            error != null -> status = error
                            presetName.isBlank() -> status = "Nama preset wajib diisi."
                            else -> {
                                val item = LocationPreset(UUID.randomUUID().toString(), presetName.trim(), next!!.mode,
                                    next.latitude, next.longitude, next.radiusMeters, next.intervalMinutes)
                                PerAppLocationStore.savePreset(context, app.packageName, item)
                                presets = PerAppLocationStore.presets(context, app.packageName)
                                selectedPresetId = item.id
                                status = "Preset " + item.name + " tersimpan untuk aplikasi ini."
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Simpan preset") }
                    OutlinedButton(onClick = {
                        val id = selectedPresetId
                        val next = profileFromFields()
                        val error = if (next == null) "Profil tidak valid." else LocationSpoofEngine.validate(next)
                        if (id == null) status = "Pilih preset yang akan diperbarui."
                        else if (error != null) status = error
                        else {
                            val existing = presets.firstOrNull { it.id == id }
                            val name = presetName.trim().ifBlank { existing?.name.orEmpty() }
                            if (name.isBlank()) status = "Nama preset wajib diisi."
                            else {
                                PerAppLocationStore.savePreset(context, app.packageName,
                                    LocationPreset(id, name, next!!.mode, next.latitude, next.longitude, next.radiusMeters, next.intervalMinutes))
                                presets = PerAppLocationStore.presets(context, app.packageName)
                                status = "Preset diperbarui."
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Perbarui") }
                }
                OutlinedButton(
                    onClick = {
                        selectedPresetId?.let { PerAppLocationStore.deletePreset(context, app.packageName, it) }
                        presets = PerAppLocationStore.presets(context, app.packageName)
                        selectedPresetId = null
                        status = "Preset dihapus."
                    },
                    enabled = selectedPresetId != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Hapus preset terpilih") }
                Text("Lokasi random menggunakan SecureRandom. Interval dihitung dari elapsed realtime dan konfigurasi tersimpan lintas reboot. Mode REAL selalu menonaktifkan spoofing.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        status?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
    }
}
