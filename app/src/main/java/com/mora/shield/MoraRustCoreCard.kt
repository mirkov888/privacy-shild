package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mora.shield.core.MoraRustEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoraRustCoreCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<MoraRustEngine.Status?>(null) }
    var statusText by remember { mutableStateOf("Memeriksa library native…") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { MoraRustEngine.inspect() }
        }
        result.onSuccess {
            status = it
            statusText = if (it.compatible) {
                "Native engine aktif dan kompatibel."
            } else {
                "Native engine terdeteksi, tetapi versi atau capability tidak cocok."
            }
        }.onFailure {
            statusText = "Native engine belum tersedia: @{it.message ?: it.javaClass.simpleName}"
        }
    }

    SectionCard(
        "Rust Native Engine · Mora Shield",
        "Mesin native lokal melalui JNI. Database Room dan Connector Mora Core tetap menjadi control plane."
    ) {
        Text(statusText, style = MaterialTheme.typography.bodyMedium)
        status?.let {
            Text(
                "API @{it.apiVersion} · Schema @{it.schemaVersion} · Capability 0x@{it.capabilityMask.toString(16)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Modul: @{it.modules.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching { MoraRustEngine.runSelfCheck() }
                        }
                        message = result.fold(
                            onSuccess = { check ->
                                check.details.joinToString("\n") +
                                    "\nHasil: @{check.passed}/@{check.total}" +
                                    if (check.allPassed) " · SEMUA MODUL LOLOS" else " · ADA TES GAGAL"
                            },
                            onFailure = { "Tes integrasi gagal: @{it.message ?: it.javaClass.simpleName}" }
                        )
                        busy = false
                    }
                },
                enabled = status?.compatible == true && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Sedang menjalankan…" else "Tes Integrasi 6 Modul Rust")
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching { MoraRustEngine.auditOwnApp(context) }
                        }
                        message = result.fold(
                            onSuccess = { report ->
                                "Audit Mora Shield selesai: @{report.optString("risk_level").uppercase()} " +
                                    "(@{report.optInt("risk_score")}/100), " +
                                    "@{report.optJSONArray("findings")?.length() ?: 0} temuan.\n" +
                                    "Laporan disimpan di penyimpanan privat aplikasi."
                            },
                            onFailure = { "Audit gagal: @{it.message ?: it.javaClass.simpleName}" }
                        )
                        busy = false
                    }
                },
                enabled = status?.compatible == true && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Audit Keamanan Mora Shield")
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching { MoraRustEngine.captureOwnNetworkSnapshot(context) }
                        }
                        message = result.fold(
                            onSuccess = { summary ->
                                "Snapshot UID Mora Shield tersimpan.\n" +
                                    "Sampel: @{summary.optInt("samples")} · " +
                                    "RX terukur: @{summary.optLong("total_rx_delta_bytes")} bytes · " +
                                    "TX terukur: @{summary.optLong("total_tx_delta_bytes")} bytes."
                            },
                            onFailure = { "Snapshot jaringan gagal: @{it.message ?: it.javaClass.simpleName}" }
                        )
                        busy = false
                    }
                },
                enabled = status?.compatible == true && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ambil Snapshot Jaringan Aplikasi")
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        Text(
            "Batas akses: audit hanya metadata dan permission Mora Shield; statistik jaringan hanya UID aplikasi ini. Tidak ada pemindaian aplikasi lain, packet capture, atau perubahan permission otomatis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
