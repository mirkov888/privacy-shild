package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mora.shield.identity.IdentityEngine
import com.mora.shield.identity.IdentityLanSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IdentityLanSyncCard(onSynced: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { IdentityEngine(context) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var token by remember { mutableStateOf("") }
    var certificatePin by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Sinkronisasi langsung Android ↔ Linux", style = MaterialTheme.typography.titleMedium)
            Text(
                "Transfer profil dua arah melalui Wi-Fi/LAN yang sama. Linux menjalankan server HTTPS sementara; tidak memakai cloud dan tidak menyentuh profil browser utama.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.filter { char -> char.isDigit() || char == '.' }.take(15) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IPv4 private komputer Linux") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                enabled = !syncing
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port server") },
                singleLine = true,
                enabled = !syncing
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.take(256) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Token sesi Linux") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !syncing
            )
            OutlinedTextField(
                value = certificatePin,
                onValueChange = { certificatePin = it.filter { char ->
                    char.isDigit() || char.lowercaseChar() in 'a'..'f' || char == ':'
                }.take(95) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SHA-256 certificate fingerprint") },
                placeholder = { Text("AA:BB:CC:… (64 digit hex)") },
                minLines = 2,
                enabled = !syncing
            )
            Button(
                enabled = !syncing && host.isNotBlank() && token.isNotBlank() && certificatePin.isNotBlank(),
                onClick = {
                    val parsedPort = port.toIntOrNull()
                    if (parsedPort == null) {
                        status = "Port tidak valid."
                        return@Button
                    }
                    syncing = true
                    status = "Mengirim dan mengambil profil melalui koneksi HTTPS ter-pin…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                IdentityLanSyncClient.synchronize(
                                    host = host.trim(),
                                    port = parsedPort,
                                    token = token.trim(),
                                    certificateFingerprint = certificatePin.trim(),
                                    engine = engine
                                )
                            }
                            status = result.summary()
                            onSynced()
                        } catch (error: Exception) {
                            status = "Sinkronisasi gagal: ${error.message ?: "kesalahan koneksi"}"
                        } finally {
                            syncing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("Sinkronisasi…")
                } else {
                    Text("Sinkronkan dua arah")
                }
            }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.startsWith("Sinkronisasi gagal")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                "Di Linux: python3 mora_identity.py sync-serve --minutes 15. Masukkan alamat, token, dan certificate pin yang ditampilkan terminal. Token tidak disimpan oleh aplikasi. Hentikan server dengan Ctrl+C setelah selesai.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
