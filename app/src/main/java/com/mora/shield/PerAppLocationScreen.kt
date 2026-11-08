package com.mora.shield

import android.content.Context
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
import java.util.Locale

enum class LocationMode(val title: String) {
    REAL("Lokasi asli"), STATIC("Lokasi statis"), RANDOM("Lokasi random"), RADIUS("Random dalam radius")
}

data class PerAppLocationProfile(
    val packageName: String,
    val mode: LocationMode = LocationMode.REAL,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 1000,
    val intervalMinutes: Int = 15
)

data class LocationApp(val packageName: String, val label: String)

object PerAppLocationStore {
    private const val PREFS = "per_app_location_profiles"
    private fun key(pkg: String, field: String) = pkg + ":" + field

    fun load(context: Context, pkg: String): PerAppLocationProfile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching { LocationMode.valueOf(p.getString(key(pkg, "mode"), "REAL") ?: "REAL") }.getOrDefault(LocationMode.REAL)
        return PerAppLocationProfile(
            pkg, mode,
            java.lang.Double.longBitsToDouble(p.getLong(key(pkg, "lat"), java.lang.Double.doubleToRawLongBits(0.0))),
            java.lang.Double.longBitsToDouble(p.getLong(key(pkg, "lon"), java.lang.Double.doubleToRawLongBits(0.0))),
            p.getInt(key(pkg, "radius"), 1000).coerceIn(1, 500000),
            p.getInt(key(pkg, "interval"), 15).coerceIn(1, 1440)
        )
    }

    fun save(context: Context, profile: PerAppLocationProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(profile.packageName, "mode"), profile.mode.name)
            .putLong(key(profile.packageName, "lat"), java.lang.Double.doubleToRawLongBits(profile.latitude))
            .putLong(key(profile.packageName, "lon"), java.lang.Double.doubleToRawLongBits(profile.longitude))
            .putInt(key(profile.packageName, "radius"), profile.radiusMeters)
            .putInt(key(profile.packageName, "interval"), profile.intervalMinutes)
            .apply()
    }
}

object PerAppLocationBackend {
    fun availability(): Pair<Boolean, String> = false to
        "Backend spoofing per proses belum tersedia. Mock location Android biasa bersifat global, bukan per aplikasi."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppLocationScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LocationApp>>(emptyList()) }
    var selected by remember { mutableStateOf<LocationApp?>(null) }
    var profile by remember { mutableStateOf<PerAppLocationProfile?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var latText by remember { mutableStateOf("0.0") }
    var lonText by remember { mutableStateOf("0.0") }
    var radiusText by remember { mutableStateOf("1000") }
    var intervalText by remember { mutableStateOf("15") }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val backend = remember { PerAppLocationBackend.availability() }

    LaunchedEffect(Unit) {
        apps = runCatching {
            context.packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { AppControlManager.isSafeThirdPartyPackage(it.packageName) }
                .map { LocationApp(it.packageName, context.packageManager.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
        }.getOrDefault(emptyList())
    }

    fun selectApp(app: LocationApp) {
        selected = app
        val p = PerAppLocationStore.load(context, app.packageName)
        profile = p
        latText = p.latitude.toString()
        lonText = p.longitude.toString()
        radiusText = p.radiusMeters.toString()
        intervalText = p.intervalMinutes.toString()
    }

    Column(Modifier.fillMaxSize()) {
        PageIntro("Per-App Location Control", "Profil lokasi tersimpan terpisah untuk setiap aplikasi.")
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Backend belum tersedia", fontWeight = FontWeight.SemiBold)
                Text(backend.second, style = MaterialTheme.typography.bodySmall)
                Text("Profil bisa disiapkan sekarang; tombol aktivasi tetap terkunci sampai backend isolasi per aplikasi teruji.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected == null) {
            OutlinedTextField(search, { search = it }, label = { Text("Cari aplikasi") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }, key = { it.packageName }) { app ->
                    Card(Modifier.fillMaxWidth().clickable { selectApp(app) }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                                Text(PerAppLocationStore.load(context, app.packageName).mode.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Atur ›")
                        }
                    }
                }
            }
        } else {
            val app = selected!!
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                TextButton(onClick = { selected = null; profile = null }) { Text("‹ Daftar aplikasi") }
                Text(app.label, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(profile?.mode?.title ?: "Lokasi asli", {}, readOnly = true,
                        label = { Text("Mode lokasi") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LocationMode.values().forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.title) }, onClick = {
                                profile = profile?.copy(mode = mode); expanded = false
                            })
                        }
                    }
                }
                if (profile?.mode == LocationMode.STATIC || profile?.mode == LocationMode.RADIUS) {
                    OutlinedTextField(latText, { latText = it }, label = { Text("Latitude (-90..90)") }, singleLine = true)
                    OutlinedTextField(lonText, { lonText = it }, label = { Text("Longitude (-180..180)") }, singleLine = true)
                }
                if (profile?.mode == LocationMode.RANDOM || profile?.mode == LocationMode.RADIUS) {
                    OutlinedTextField(radiusText, { radiusText = it }, label = { Text("Radius (meter, 1–500000)") }, singleLine = true)
                }
                if (profile?.mode != LocationMode.REAL) {
                    OutlinedTextField(intervalText, { intervalText = it }, label = { Text("Interval random (menit, 1–1440)") }, singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Spoofing per aplikasi", fontWeight = FontWeight.SemiBold)
                        Text("Belum dapat diaktifkan: backend belum terpasang", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = false, onCheckedChange = {}, enabled = false)
                }
                Button(onClick = {
                    val p = profile
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    val radius = radiusText.toIntOrNull()
                    val interval = intervalText.toIntOrNull()
                    val error = when {
                        p == null -> "Mode lokasi tidak valid."
                        p.mode in setOf(LocationMode.STATIC, LocationMode.RADIUS) && (lat == null || lat !in -90.0..90.0) -> "Latitude harus -90 sampai 90."
                        p.mode in setOf(LocationMode.STATIC, LocationMode.RADIUS) && (lon == null || lon !in -180.0..180.0) -> "Longitude harus -180 sampai 180."
                        p.mode in setOf(LocationMode.RANDOM, LocationMode.RADIUS) && (radius == null || radius !in 1..500000) -> "Radius harus 1–500000 meter."
                        p.mode != LocationMode.REAL && (interval == null || interval !in 1..1440) -> "Interval harus 1–1440 menit."
                        else -> null
                    }
                    if (error != null) status = error else {
                        PerAppLocationStore.save(context, p!!.copy(
                            latitude = lat ?: p.latitude, longitude = lon ?: p.longitude,
                            radiusMeters = radius ?: p.radiusMeters, intervalMinutes = interval ?: p.intervalMinutes
                        ))
                        profile = PerAppLocationStore.load(context, app.packageName)
                        status = "Profil tersimpan untuk " + app.label + ". Spoofing belum aktif."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Simpan profil") }
                Text("Mode lokasi asli berarti tidak ada spoofing. Preset dan aktivasi backend per-app akan disambungkan pada tahap lanjutan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        status?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
    }
}
