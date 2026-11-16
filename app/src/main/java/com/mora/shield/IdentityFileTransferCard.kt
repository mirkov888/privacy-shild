package com.mora.shield

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.shield.identity.IdentityEngine
import com.mora.shield.identity.IdentityProfileRecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * File-based import/export for browser profiles and complete Identity Studio backups.
 * Storage Access Framework keeps files in the user's chosen location; no broad storage
 * permission or hard-coded filesystem path is required.
 */
@Composable
fun IdentityFileTransferCard(onUpdated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { IdentityEngine(context) }
    val recovery = remember(context) { IdentityProfileRecoveryManager(context) }
    var status by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val exportProfiles = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            status = "Ekspor profil dibatalkan."
        } else {
            val json = engine.exportCustomProfilesJson()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { writeText(context, uri, json) }
                }.onSuccess {
                    status = "Profil browser berhasil diekspor."
                }.onFailure {
                    status = "Ekspor profil gagal: ${it.message ?: "kesalahan I/O"}"
                }
            }
        }
    }

    val importProfiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val raw = withContext(Dispatchers.IO) {
                        readText(context, uri, MAX_PROFILE_IMPORT_BYTES)
                    }
                    engine.importCustomProfilesJson(raw)
                }.onSuccess { added ->
                    status = if (added > 0) {
                        "$added profil browser berhasil diimpor."
                    } else {
                        "Tidak ada profil baru yang valid; file mungkin kosong, duplikat, atau tidak sesuai."
                    }
                    if (added > 0) onUpdated()
                }.onFailure {
                    status = "Impor profil gagal: ${it.message ?: "file tidak valid"}"
                }
            }
        }
    }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            status = "Ekspor backup dibatalkan."
        } else {
            val json = recovery.exportBackupJson()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { writeText(context, uri, json) }
                }.onSuccess {
                    status = "Backup lengkap Identity Studio berhasil diekspor."
                }.onFailure {
                    status = "Ekspor backup gagal: ${it.message ?: "kesalahan I/O"}"
                }
            }
        }
    }

    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { readText(context, uri, MAX_BACKUP_IMPORT_BYTES) }
                }.onSuccess { raw ->
                    val validation = IdentityProfileRecoveryManager.validateBackupJson(raw)
                    if (validation == null) {
                        pendingRestore = raw
                        status = "Backup valid. Konfirmasi pemulihan untuk menerapkan konfigurasi."
                    } else {
                        status = "Backup ditolak: $validation"
                    }
                }.onFailure {
                    status = "Pembacaan backup gagal: ${it.message ?: "file tidak valid"}"
                }
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Impor / Ekspor Berkas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Simpan profil browser sebagai JSON atau pindahkan seluruh pengaturan Identity Studio beserta baseline identitas asli yang baca-saja melalui backup. File dipilih sendiri melalui pemilih dokumen Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { exportProfiles.launch("mora-identity-profiles.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ekspor profil browser (.json)") }
            OutlinedButton(
                onClick = { importProfiles.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Impor profil browser (.json)") }
            Button(
                onClick = { exportBackup.launch("mora-identity-backup.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ekspor backup lengkap") }
            OutlinedButton(
                onClick = { importBackup.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pilih backup untuk dipulihkan") }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    val restorePayload = pendingRestore
    if (restorePayload != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Pulihkan backup Identity Studio?") },
            text = {
                Text(
                    "Profil kustom, profil terpilih, pengaturan proteksi, jadwal randomizer, dan profil kompatibilitas per-aplikasi akan diganti sesuai file. Keadaan saat ini disimpan sebagai rollback point."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                recovery.restoreBackupJson(restorePayload)
                            }
                            status = result.message
                            if (result.success) onUpdated()
                        }
                    }
                ) { Text("Pulihkan") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Batal") }
            }
        )
    }
}

private fun writeText(context: Context, uri: Uri, value: String) {
    val output = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw IllegalStateException("Tidak dapat membuka file tujuan")
    output.bufferedWriter(Charsets.UTF_8).use { it.write(value) }
}

private fun readText(context: Context, uri: Uri, maxBytes: Int): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Tidak dapat membuka file sumber")
    val output = ByteArrayOutputStream()
    input.use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "File melewati batas ${maxBytes / 1024} KB" }
            output.write(buffer, 0, count)
        }
    }
    return String(output.toByteArray(), Charsets.UTF_8)
}

private const val MAX_PROFILE_IMPORT_BYTES = 256 * 1024
private const val MAX_BACKUP_IMPORT_BYTES = 1024 * 1024
