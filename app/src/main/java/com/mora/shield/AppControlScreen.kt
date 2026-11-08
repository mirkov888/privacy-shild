package com.mora.shield

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.concurrent.thread

data class ManagedApp(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
    val disabled: Boolean,
    val hidden: Boolean
)

object AppControlManager {
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val protectedPackages = setOf("com.mora.shield")
    private val protectedPrefixes = listOf("com.android.", "com.google.android.", "com.miui.", "com.xiaomi.", "android.")

    fun isSafeThirdPartyPackage(packageName: String): Boolean =
        packagePattern.matches(packageName) &&
            packageName !in protectedPackages &&
            packageName != "android" &&
            protectedPrefixes.none { packageName.startsWith(it) }

    fun parsePackages(output: String): Set<String> = output.lineSequence()
        .map { it.trim().removePrefix("package:") }
        .filter { isSafeThirdPartyPackage(it) }
        .toSet()

    fun parseHiddenPackages(output: String): Set<String> {
        val result = mutableSetOf<String>()
        var current: String? = null
        output.lineSequence().forEach { line ->
            val header = Regex("^\\s*Package \\[([^]]+)]").find(line)
            if (header != null) current = header.groupValues[1]
            else if (current != null && line.contains("User 0:") && line.contains("hidden=true")) result += current!!
        }
        return result
    }

    fun list(context: Context): List<ManagedApp> {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { isSafeThirdPartyPackage(it.packageName) }
        val disabled = parsePackages(Root.sh("pm list packages -3 -d --user 0 2>/dev/null").out)
        val hidden = parseHiddenPackages(Root.sh("dumpsys package 2>/dev/null").out)
        return installed.map { info ->
            val icon = try { pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap() } catch (_: Exception) { null }
            ManagedApp(
                info.packageName,
                try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { info.packageName },
                icon,
                info.packageName in disabled || !info.enabled,
                info.packageName in hidden
            )
        }.sortedWith(compareBy<ManagedApp> { it.disabled }.thenBy { it.label.lowercase() })
    }

    fun setDisabled(packageName: String, disabled: Boolean): Root.Result {
        if (!isSafeThirdPartyPackage(packageName)) return Root.Result(2, "Paket tidak diizinkan untuk dikendalikan")
        val command = if (disabled) "pm disable-user --user 0" else "pm enable --user 0"
        return Root.sh("$command '$packageName'")
    }

    fun setHidden(packageName: String, hidden: Boolean): Root.Result {
        if (!isSafeThirdPartyPackage(packageName)) return Root.Result(2, "Paket tidak diizinkan untuk dikendalikan")
        val command = if (hidden) "pm hide --user 0" else "pm unhide --user 0"
        return Root.sh("$command '$packageName'")
    }
}

@Composable
fun AppControlScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<ManagedApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var pending by remember { mutableStateOf<ManagedApp?>(null) }
    var pendingAction by remember { mutableStateOf("freeze") }
    var actionBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true
        thread {
            val hasRoot = Root.available()
            val result = if (hasRoot) AppControlManager.list(context.applicationContext) else emptyList()
            rootAvailable = hasRoot
            apps = result
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    val visible = apps.filter { app ->
        val matches = query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)
        matches && when (filter) {
            "enabled" -> !app.disabled
            "disabled" -> app.disabled
            "hidden" -> app.hidden
            else -> true
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageIntro("Kontrol aplikasi", "Bekukan aplikasi atau sembunyikan ikon launcher tanpa menonaktifkannya.")
        if (rootAvailable == false) {
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Akses root tidak tersedia", fontWeight = FontWeight.SemiBold)
                    Text("Izinkan Mora Shield melalui KernelSU-Next atau Magisk, lalu muat ulang.")
                    Button(onClick = { reload() }) { Text("Periksa root lagi") }
                }
            }
        } else {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Cari nama atau package") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp)
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Semua") })
                FilterChip(selected = filter == "enabled", onClick = { filter = "enabled" }, label = { Text("Aktif") })
                FilterChip(selected = filter == "disabled", onClick = { filter = "disabled" }, label = { Text("Dibekukan") })
                FilterChip(selected = filter == "hidden", onClick = { filter = "hidden" }, label = { Text("Disembunyikan") })
                TextButton(onClick = { reload() }) { Text("Refresh") }
            }
            Text("${visible.size} aplikasi · ${apps.count { it.disabled }} dibekukan · ${apps.count { it.hidden }} disembunyikan", Modifier.padding(horizontal = 18.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            if (loading) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Memindai aplikasi…", Modifier.padding(top = 8.dp))
                }
            } else if (visible.isEmpty()) {
                EmptyHint("Tidak ada aplikasi yang cocok.")
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (app.icon != null) Image(app.icon, null, Modifier.size(42.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(when { app.disabled -> "Dibekukan · tidak berjalan"; app.hidden -> "Aktif · ikon disembunyikan"; else -> "Aktif · terlihat di launcher" }, style = MaterialTheme.typography.labelSmall,
                                        color = if (app.disabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Button(enabled = !actionBusy, onClick = { pendingAction = "freeze"; pending = app }) { Text(if (app.disabled) "Aktifkan" else "Bekukan") }
                                    TextButton(enabled = !actionBusy && !app.disabled, onClick = { pendingAction = "hide"; pending = app }) { Text(if (app.hidden) "Tampilkan" else "Sembunyikan") }
                                }
                            }
                        }
                    }
                }
            }
        }
        status?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
        Text("Sembunyikan memakai status hidden Android untuk user 0. Ikon disembunyikan dari launcher, tetapi paket masih dapat terlihat melalui Settings, ADB, atau pemeriksaan sistem. Aplikasi sistem dan Mora Shield dilindungi.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    pending?.let { app ->
        val disabling = !app.disabled
        AlertDialog(
            onDismissRequest = { if (!actionBusy) pending = null },
            title = { Text(if (disabling) "Bekukan aplikasi?" else "Aktifkan aplikasi?") },
            text = { Text(if (disabling)
                "${app.label} akan dinonaktifkan untuk pengguna saat ini, dihentikan, dan ikon launcher-nya tidak tampil. Data aplikasi tidak dihapus. Aplikasi yang bergantung padanya mungkin ikut terganggu."
                else "${app.label} akan diaktifkan kembali. Data aplikasi tetap dipertahankan.") },
            confirmButton = {
                TextButton(enabled = !actionBusy, onClick = {
                    actionBusy = true
                    pending = null
                    thread {
                        val result = AppControlManager.setDisabled(app.packageName, disabling)
                        apps = if (result.ok) AppControlManager.list(context.applicationContext) else apps
                        status = if (result.ok) {
                            if (disabling) "${app.label} berhasil dibekukan." else "${app.label} berhasil diaktifkan."
                        } else "Gagal mengubah ${app.label}: ${result.out.take(160)}"
                        actionBusy = false
                    }
                }) { Text(if (disabling) "Bekukan" else "Aktifkan") }
            },
            dismissButton = { TextButton(enabled = !actionBusy, onClick = { pending = null }) { Text("Batal") } }
        )
    }
}
