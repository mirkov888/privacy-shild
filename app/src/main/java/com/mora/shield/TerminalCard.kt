package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private data class TerminalEntry(val command: String, val output: String, val exitCode: Int?)

@Composable
fun TerminalCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var rootMode by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<TerminalEntry>() }

    SectionCard("Terminal", "Shell lokal Android. Mode Root memerlukan persetujuan KernelSU/Magisk untuk sesi ini.") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Jalankan sebagai root", style = MaterialTheme.typography.bodyMedium)
                Text("Nonaktif secara default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = rootMode, onCheckedChange = { rootMode = it })
        }
        OutlinedTextField(
            value = command,
            onValueChange = { command = it.take(2000) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Perintah terminal") },
            placeholder = { Text("contoh: id; getprop ro.build.version.release") },
            minLines = 2,
            maxLines = 4
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                enabled = !running && command.isNotBlank(),
                onClick = {
                    val requested = command.trim()
                    command = ""
                    running = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runTerminalCommand(requested, rootMode)
                        }
                        history.add(0, TerminalEntry(requested, result.first, result.second))
                        if (history.size > 30) history.removeAt(history.lastIndex)
                        running = false
                    }
                }
            ) { Text(if (running) "Menjalankan…" else "Jalankan") }
            TextButton(onClick = { history.clear() }, enabled = history.isNotEmpty()) { Text("Bersihkan") }
        }
        if (history.isEmpty()) {
            Text("Output terminal akan muncul di sini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            history.forEach { entry ->
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$ ${entry.command}", style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace, softWrap = true)
                        Text("Exit: ${entry.exitCode ?: "timeout/error"}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(entry.output.ifBlank { "(tidak ada output)" },
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                softWrap = true)
                        }
                    }
                }
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
