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
        Text("Pilih preset perangkat atau atur manual. Perubahan hanya diterapkan pada proses aplikasi yang dipilih melalui LSPosed; tidak mengubah identitas sistem global.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var fingerprint by remember { mutableStateOf(original.fingerprint) }
    var release by remember { mutableStateOf(original.release) }
    var sdk by remember { mutableStateOf(original.sdk.toString()) }
    var patch by remember { mutableStateOf(original.securityPatch) }
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
                        release = preset.release
                        sdk = preset.sdk.toString()
                        // Fingerprint/security patch intentionally remain untouched:
                        // they are build-specific and should not be fabricated.
                        selectedPresetLabel = preset.label
                        presetExpanded = false
                    }
                )
            }
        }
        Text("Preset mengisi manufacturer, brand, model, device, product, dan versi Android. Fingerprint serta security patch tetap manual karena harus berasal dari build resmi yang cocok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Aktifkan spoofing", Modifier.weight(1f))
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
        field("Build fingerprint (opsional)", fingerprint) { fingerprint = it }
        field("Android release", release) { release = it }
        field("SDK integer", sdk) { sdk = it.filter(Char::isDigit).take(2) }
        field("Security patch (YYYY-MM-DD, opsional)", patch) { patch = it }
        Button(onClick = {
            val n = sdk.toIntOrNull() ?: 36
            runCatching {
                PerAppIdentityStore.save(context, DeviceIdentityProfile(pkg, enabled, manufacturer, brand, model, device, product, fingerprint, release, n, patch))
            }.onSuccess { Store.message = "Profil identitas disimpan. Force-stop lalu buka ulang aplikasi target."; onSaved() }
                .onFailure { Store.message = "Profil tidak valid: ${it.message}" }
        }, Modifier.fillMaxWidth()) { Text("Simpan profil") }
        OutlinedButton(onClick = {
            PerAppIdentityStore.save(context, DeviceIdentityProfile(pkg, enabled = false))
            Store.message = "Spoofing dinonaktifkan untuk aplikasi ini"
            onBack()
        }, Modifier.fillMaxWidth()) { Text("Emergency restore") }
        TextButton(onClick = onBack) { Text("Kembali") }
        Text("Catatan: profil ini untuk pengujian kompatibilitas. Aplikasi tertentu membaca identitas lewat native code atau layanan sistem sehingga hasilnya bisa berbeda. Ini tidak mengubah IMEI/serial, hardware attestation, atau Play Integrity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
