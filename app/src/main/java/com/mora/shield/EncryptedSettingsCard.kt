package com.mora.shield

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mora.shield.security.EncryptedAuditExport
import kotlin.concurrent.thread

@Composable
fun EncryptedSettingsCard() {
    val ctx = LocalContext.current
    var password by remember { mutableStateOf("") }
    var askRestore by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val secret = password
            thread {
                try {
                    if (secret.length < 10) {
                        Store.message = "Sandi minimal 10 karakter"
                        return@thread
                    }
                    val backup = Ps.run("backup")
                    if (!backup.ok) {
                        Store.message = "Ekspor gagal: ${backup.out.take(120)}"
                        return@thread
                    }
                    val encrypted = EncryptedAuditExport.encrypt(backup.out.toByteArray(Charsets.UTF_8), secret.toCharArray())
                    val written = ctx.contentResolver.openOutputStream(uri)?.use { it.write(encrypted); true } ?: false
                    Store.message = if (written) "Cadangan pengaturan terenkripsi disimpan" else "Gagal menulis berkas"
                } catch (e: Exception) {
                    Store.message = "Ekspor terenkripsi gagal: ${e.message ?: "kesalahan"}"
                }
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val secret = password
            thread {
                try {
                    if (secret.length < 10) {
                        Store.message = "Sandi minimal 10 karakter"
                        return@thread
                    }
                    val encrypted = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (encrypted.isEmpty() || encrypted.size > 262144) {
                        Store.message = "Berkas kosong atau terlalu besar"
                        return@thread
                    }
                    val plain = EncryptedAuditExport.decrypt(encrypted, secret.toCharArray())
                    val text = String(plain, Charsets.UTF_8)
                    val validLine = Regex("^(#.*|[A-Za-z0-9_]+=[A-Za-z0-9._,:/-]*)?$")
                    if (!text.lines().all { validLine.matches(it.trimEnd('\r')) }) {
                        Store.message = "Isi cadangan tidak valid; pengaturan tidak diubah"
                        return@thread
                    }
                    val b64 = Base64.encodeToString(text.replace("\r", "").toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    val result = Root.sh("mkdir -p ${Ps.DATA} && printf '%s' '$b64' | base64 -d > ${Ps.DATA}/restore.in && sh ${Ps.MOD}/bin/ps.sh restore")
                    val summary = result.out.lines().firstOrNull { it.startsWith("RESTORE|") }
                    Store.showText("Pemulihan pengaturan terenkripsi", if (result.ok) summary ?: result.out else "Gagal: ${result.out.take(180)}")
                    Store.refresh(ctx)
                } catch (e: Exception) {
                    Store.message = "Sandi salah atau berkas cadangan rusak"
                }
            }
        }
    }

    SectionCard("Cadangan pengaturan terenkripsi", "AES-GCM + PBKDF2; cocok untuk pindah HP atau sebelum flash ROM", collapsible = true, startOpen = false) {
        OutlinedTextField(
            password, { password = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            singleLine = true, label = { Text("Sandi cadangan (minimal 10 karakter)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        ActionRow("Ekspor pengaturan terenkripsi", "Simpan berkas .moraenc; sandi tidak ikut disimpan", "Ekspor") {
            if (password.length >= 10) save.launch("mora-shield-settings.moraenc")
            else Store.message = "Sandi minimal 10 karakter"
        }
        ActionRow("Impor pengaturan terenkripsi", "Pengaturan modul saat ini akan ditimpa", "Pilih berkas") { askRestore = true }
        InfoNote("Simpan sandi secara terpisah. Jika sandi hilang, isi cadangan tidak dapat dipulihkan.", true)
    }
    if (askRestore) Confirm(
        "Pulihkan pengaturan dari cadangan?", "Pengaturan modul akan ditimpa. PIN, persetujuan hapus data, dan mode uji tetap mengikuti aturan pemulihan modul.",
        "Pilih berkas", { askRestore = false }
    ) { open.launch(arrayOf("application/octet-stream", "*/*")) }
}
