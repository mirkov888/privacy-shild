@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ActivityScreen() {
    var filter by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var appQuery by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val labels = HashMap<String, String>()
    for (a in Store.apps) labels[a.pkg] = a.label
    val types = Store.events.map { it.type }.distinct()
    fun dateStart(value: String): Long? = runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() }.getOrNull()
    fun dateEnd(value: String): Long? = runCatching { java.time.LocalDate.parse(value).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() - 1 }.getOrNull()
    val start = dateStart(fromDate)
    val end = dateEnd(toDate)
    val list = Store.events.filter { e ->
        (filter.isEmpty() || e.type == filter) &&
        (query.isBlank() || listOf(typeName(e.type), e.detail, e.pkg, labels[e.pkg] ?: "").any { it.contains(query, true) }) &&
        (appQuery.isBlank() || e.pkg.contains(appQuery, true) || (labels[e.pkg] ?: "").contains(appQuery, true)) &&
        (start == null || e.ts >= start) && (end == null || e.ts <= end)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true, label = { Text("Cari kejadian, domain, atau detail") })
        OutlinedTextField(appQuery, { appQuery = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true, label = { Text("Filter aplikasi / package") })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(fromDate, { fromDate = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Dari (YYYY-MM-DD)") })
            }
            Column(Modifier.weight(1f)) {
                OutlinedTextField(toDate, { toDate = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Sampai (YYYY-MM-DD)") })
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter.isEmpty(), onClick = { filter = "" }, label = { Text("Semua (${list.size})") })
            for (t in types) {
                FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(typeName(t)) })
            }
        }
        if (list.isEmpty()) {
            EmptyHint("Belum ada kejadian. Nyalakan 'Pantau akses' di Pengaturan > Pemantauan.")
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { e ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            val who = if (e.pkg.isEmpty()) "Sistem" else (labels[e.pkg] ?: e.pkg)
                            Text(who, fontWeight = FontWeight.SemiBold, maxLines = 2, softWrap = true)
                            Text(typeName(e.type) + (if (e.detail.isNotEmpty()) " · " + e.detail else ""), style = MaterialTheme.typography.bodyMedium, softWrap = true)
                            val domainCandidate = Regex("(?i)(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63}")
                                .find(e.detail)?.value?.lowercase()
                            if (domainCandidate != null && HostInputValidator.domainOk(domainCandidate)) {
                                TextButton(onClick = {
                                    Store.run("hosts add white $domainCandidate", "Domain diizinkan: $domainCandidate") { Store.refresh(Store.appCtx) }
                                }) { Text("Izinkan domain") }
                            }
                            Text(fmtTime(e.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (Store.events.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Hapus riwayat kejadian") }
        }
    }
    if (confirmClear) Confirm("Hapus riwayat?", "Semua kejadian di modul dihapus. Statistik ikut kosong.", "Hapus", { confirmClear = false }) {
        Store.run("evclear", "Riwayat dihapus") { Store.refresh(Store.appCtx) }
    }
}
