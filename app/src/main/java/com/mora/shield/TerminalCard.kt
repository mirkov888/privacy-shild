package com.mora.shield

import androidx.compose.foundation.layout.Box
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TerminalEntry(val command: String, val output: String, val exitCode: Int?)

@Composable
fun TerminalCard() {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var command by remember { mutableStateOf("") }
    var rootMode by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<TerminalEntry>() }
    val tint = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.heightIn(min = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val actions = listOf("search", "saved", "history", "device", "settings")
            actions.forEach { action ->
                Surface(
                    modifier = Modifier.size(46.dp).clickable { panel = if (panel == action) "" else action },
                    shape = CircleShape, color = tint
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (action) {
                            "search" -> Icon(Icons.Default.Search, null, tint = ink)
                            "saved" -> Icon(Icons.Default.Search, null, tint = ink)
                            "history" -> Icon(Icons.Default.Search, null, tint = ink)
                            "device" -> Icon(Icons.Default.Settings, null, tint = ink)
                            else -> Icon(Icons.Default.Settings, null, tint = ink)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mora Shell", fontSize = 32.sp, fontWeight = FontWeight.Light)
            Surface(shape = RoundedCornerShape(28.dp), color = if (rootMode) MaterialTheme.colorScheme.primary else tint,
                modifier = Modifier.clickable { rootMode = !rootMode }) {
                Text(if (rootMode) "Root ON" else "Root", modifier = Modifier.padding(horizontal = 25.dp, vertical = 15.dp),
                    color = if (rootMode) MaterialTheme.colorScheme.onPrimary else ink,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = command, onValueChange = { command = it.take(2000) },
                modifier = Modifier.weight(1f), placeholder = { Text("Input Shell Command") },
                singleLine = false, minLines = 1, maxLines = 3,
                shape = RoundedCornerShape(5.dp)
            )
            Surface(
                modifier = Modifier.size(66.dp).clickable(enabled = !running && command.isNotBlank()) {
                    val requested = command.trim()
                    val useRoot = rootMode
                    command = ""
                    running = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runTerminalCommand(requested, useRoot) }
                        history.add(0, TerminalEntry(requested, result.first, result.second))
                        if (history.size > 30) history.removeAt(history.lastIndex)
                        running = false
                    }
                }, shape = RoundedCornerShape(18.dp),
                color = tint
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (running) Icons.Default.Settings else Icons.Default.PlayArrow,
                        contentDescription = "Jalankan perintah", tint = ink, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (panel.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when(panel) {
                            "search" -> "Cari output"
                            "saved" -> "Perintah cepat"
                            "history" -> "Riwayat"
                            "device" -> "Info perangkat"
                            else -> "Pengaturan terminal"
                        }, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { panel = "" }) { Text("Tutup") }
                    }
                    when(panel) {
                        "saved" -> listOf("id", "getprop ro.build.version.release", "uname -a", "df -h").forEach { item ->
                            TextButton(onClick = { command = item; panel = "" }) { Text(item, fontFamily = FontFamily.Monospace) }
                        }
                        "device" -> TextButton(onClick = { command = "getprop ro.product.model; getprop ro.build.version.release"; panel = "" }) { Text("Isi perintah info perangkat") }
                        "settings" -> Text("Root hanya digunakan jika tombol Root ON aktif. Perintah dijalankan secara lokal.")
                        "search" -> Text("Gunakan riwayat output di bawah untuk mencari dan menyalin teks.")
                        "history" -> TextButton(onClick = { history.clear() }, enabled = history.isNotEmpty()) {
                            Icon(Icons.Default.Search, null)
                            Text(" Hapus riwayat")
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            history.forEach { entry ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text((if (rootMode) "# " else "$ ") + entry.command,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text("Exit: ${entry.exitCode ?: "timeout/error"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(entry.output.ifBlank { "(tidak ada output)" },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(
                onClick = { clipboard.getText()?.text?.let { command += it } },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.heightIn(min = 62.dp)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(12.dp))
                Text("Paste")
            }
        }
    }
}
private fun runTerminalCommand(command: String, root: Boolean): Pair<String, Int?> {
    return try {
        val process = if (root) {
            ProcessBuilder("su", "-c", command)
        } else {
            ProcessBuilder("/system/bin/sh", "-c", command)
        }.redirectErrorStream(true).start()

        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < 24_000) output.append(line).append('\n')
                        }
                    }
                }
            }
        }
        reader.isDaemon = true
        reader.start()

        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            reader.join(500)
            synchronized(output) { output.append("\n[WAKTU HABIS: 15 detik]") }
            Pair(synchronized(output) { output.toString().take(24_000) }, null)
        } else {
            reader.join(1000)
            Pair(synchronized(output) { output.toString().take(24_000) }, process.exitValue())
        }
    } catch (error: Exception) {
        Pair(error.message ?: error.javaClass.simpleName, null)
    }
}
