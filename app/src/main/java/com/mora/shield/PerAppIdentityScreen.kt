@file:OptIn(ExperimentalMaterial3Api::class)
package com.mora.shield

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        Text("Spoof Build properties hanya di proses aplikasi yang dipilih pada Scope LSPosed. Tidak mengubah identitas sistem global.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Cari aplikasi") }, singleLine = true)
        if (selected == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(Modifier.fillMaxWidth(), onClick = { selected = app.packageName }) {
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
    Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pkg, style = MaterialTheme.typography.titleSmall)
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
        field("Build fingerprint", fingerprint) { fingerprint = it }
        field("Android release", release) { release = it }
        field("SDK integer", sdk) { sdk = it.filter(Char::isDigit).take(2) }
        field("Security patch (YYYY-MM-DD)", patch) { patch = it }
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
        Text("Catatan: aplikasi tertentu membaca identitas melalui native code atau layanan sistem dan mungkin tidak terpengaruh. Jangan gunakan profil palsu untuk melewati pemeriksaan keamanan atau verifikasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
