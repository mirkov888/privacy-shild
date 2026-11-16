@file:OptIn(ExperimentalMaterial3Api::class)
package com.mora.shield

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PerAppIdentityScreen() {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps = remember {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName && !it.packageName.startsWith("com.android.") }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter { pm.getApplicationLabel(it).toString().contains(query, true) || it.packageName.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Per-App Device Identity", style = MaterialTheme.typography.headlineSmall)
        Text("Pilih aplikasi secara eksplisit. Hanya label perangkat publik yang didukung yang diterapkan ke proses target melalui LSPosed; identitas keamanan dan hardware tidak disentuh.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Cari aplikasi") }, singleLine = true)
        if (selected == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(onClick = { selected = app.packageName }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(pm.getApplicationLabel(app).toString(), style = MaterialTheme.typography.titleMedium)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            key(selected, refresh) {
                IdentityEditor(selected!!, onBack = { selected = null }, onSaved = { refresh++ })
            }
        }
    }
}

@Composable
private fun IdentityEditor(pkg: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val original = remember { PerAppIdentityStore.load(context, pkg) }
    var enabled by remember { mutableStateOf(original.enabled) }
    var manufacturer by remember { mutableStateOf(original.manufacturer) }
    var brand by remember { mutableStateOf(original.brand) }
    var model by remember { mutableStateOf(original.model) }
    var device by remember { mutableStateOf(original.device) }
    var product by remember { mutableStateOf(original.product) }
    var presetExpanded by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember { mutableStateOf("Pilih preset perangkat") }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pkg, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { presetExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$selectedPresetLabel · ${DeviceIdentityPresetCatalog.presets.size} preset")
        }
        DropdownMenu(
            expanded = presetExpanded,
            onDismissRequest = { presetExpanded = false },
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            DeviceIdentityPresetCatalog.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        manufacturer = preset.manufacturer
                        brand = preset.brand
                        model = preset.model
                        device = preset.device
                        product = preset.product
                        selectedPresetLabel = preset.label
                        presetExpanded = false
                    }
                )
            }
        }
        Text(
            "Properti yang didukung: Manufacturer, Brand, Model, Device, dan Product. Build fingerprint, Android release/SDK, security patch, IMEI, serial number, hardware attestation, dan Play Integrity tetap asli.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Aktifkan profil kompatibilitas", Modifier.weight(1f))
            Switch(enabled, { enabled = it })
        }
        @Composable fun field(label: String, value: String, set: (String) -> Unit) {
            OutlinedTextField(value, set, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
        }
        field("Manufacturer", manufacturer) { manufacturer = it }
        field("Brand", brand) { brand = it }
        field("Model", model) { model = it }
        field("Device", device) { device = it }
        field("Product", product) { product = it }
        Button(onClick = {
            // Preserve legacy values for backward compatibility; the hook intentionally ignores them.
            runCatching {
                PerAppIdentityStore.save(
                    context,
                    DeviceIdentityProfile(
                        pkg, enabled, manufacturer, brand, model, device, product,
                        original.fingerprint, original.release, original.sdk, original.securityPatch
                    )
                )
            }.onSuccess {
                Store.message = "Profil disimpan. Force-stop lalu buka ulang aplikasi target."
                onSaved()
            }.onFailure {
                Store.message = "Profil tidak valid: ${it.message}"
            }
        }, Modifier.fillMaxWidth()) { Text("Simpan profil") }
        OutlinedButton(onClick = {
            val restored = PerAppIdentityStore.rollback(context, pkg)
            if (restored) {
                Store.message = "Profil sebelumnya berhasil dipulihkan"
            } else {
                PerAppIdentityStore.save(context, DeviceIdentityProfile(pkg, enabled = false))
                Store.message = "Tidak ada snapshot lama; profil dinonaktifkan"
            }
            onBack()
        }, Modifier.fillMaxWidth()) { Text("Emergency restore") }
        TextButton(onClick = onBack) { Text("Kembali") }
        Text(
            "Penerapan hanya bekerja untuk aplikasi target yang dipilih dan memerlukan LSPosed + companion hook. Aplikasi dapat memakai sumber identitas lain sehingga hasilnya tidak dijamin seragam.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
