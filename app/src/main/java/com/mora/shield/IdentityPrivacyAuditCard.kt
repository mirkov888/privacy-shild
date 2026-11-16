package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.mora.shield.identity.IdentityPrivacyAudit
import com.mora.shield.identity.IdentityPrivacyAuditReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IdentityPrivacyAuditCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<IdentityPrivacyAuditReport?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Identity Privacy Control", style = MaterialTheme.typography.titleMedium)
            Text(
                "Audit lokal untuk melihat aplikasi yang meminta izin terkait telepon dan kontak. Mora tidak mengubah identifier perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                enabled = !scanning,
                onClick = {
                    scanning = true
                    scanMessage = ""
                    scope.launch {
                        try {
                            report = withContext(Dispatchers.IO) {
                                IdentityPrivacyAudit.scan(context.applicationContext)
                            }
                            scanMessage = "Audit selesai. Tidak ada identifier yang diubah."
                        } catch (error: Exception) {
                            scanMessage = "Audit gagal: ${error.message ?: "kesalahan tidak diketahui"}"
                        } finally {
                            scanning = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scanning) "Memindai aplikasi…" else "Audit izin identifier")
            }

            OutlinedButton(
                onClick = {
                    Store.message = if (IdentityPrivacyAudit.openPrivacySettings(context)) {
                        "Pengaturan privasi Android dibuka"
                    } else {
                        "Pengaturan privasi Android tidak tersedia"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buka pengaturan privasi Android")
            }

            report?.let { result ->
                Text("Android ID", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (result.androidIdAvailable) {
                        "Tersedia untuk identitas aplikasi Mora. Nilai mentah tidak ditampilkan, disalin, atau disimpan."
                    } else {
                        "Nilai Android ID aplikasi tidak tersedia pada sesi ini."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Android ID modern dibatasi berdasarkan kunci penandatangan aplikasi, profil pengguna Android, dan perangkat. Ini bukan IMEI dan bukan ID global untuk semua aplikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("IMEI & serial number", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Mora tidak membaca atau menyimpan nilainya. Android membatasi akses identifier telepon permanen untuk aplikasi biasa; izin Status Telepon saja tidak otomatis memberi akses IMEI atau serial.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text("Hardware attestation & Play Integrity", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tidak diminta, tidak dipalsukan, dan tidak diubah. Hasil integritas harus diverifikasi oleh layanan yang berwenang; profil model perangkat bukan bukti attestation.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Aplikasi terpasang yang meminta izin sensitif: ${result.apps.size}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Daftar ini membaca deklarasi izin dan status grant Android. Ini bukan bukti bahwa aplikasi benar-benar memakai atau mengirim datanya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (result.apps.isEmpty()) {
                    Text("Tidak ditemukan aplikasi lain dengan deklarasi izin dalam kategori audit ini.")
                } else {
                    result.apps.take(20).forEach { app ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${app.packageName} · ${if (app.isSystemApp) "Sistem" else "Aplikasi pengguna"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        Store.message = if (
                                            IdentityPrivacyAudit.openApplicationSettings(
                                                context,
                                                app.packageName
                                            )
                                        ) {
                                            "Pengaturan ${app.label} dibuka. Tinjau izin secara manual."
                                        } else {
                                            "Pengaturan aplikasi tidak dapat dibuka."
                                        }
                                    }
                                ) {
                                    Text("Atur izin")
                                }
                            }
                            Text(
                                app.permissions.joinToString(" · ") {
                                    "${it.label}: ${if (it.granted) "diizinkan" else "belum diizinkan"}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (result.apps.size > 20) {
                        Text(
                            "Menampilkan 20 dari ${result.apps.size} aplikasi. Urutan memprioritaskan aplikasi pengguna.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (scanMessage.isNotBlank()) {
                Text(
                    scanMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
