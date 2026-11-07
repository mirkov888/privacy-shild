package com.mora.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

private data class PolicyPreset(val id: String, val title: String, val description: String, val values: Map<String, String>)

private val POLICY_PRESETS = listOf(
    PolicyPreset("balanced", "Seimbang", "Privasi dasar tanpa mematikan konektivitas umum.", mapOf(
        "ads" to "1", "telemetry" to "1", "mac_rand" to "1", "lock_priv" to "1", "adb_off" to "1", "usb_lock" to "1"
    )),
    PolicyPreset("privacy", "Privasi ketat", "Mengurangi pelacakan dan akses perangkat; beberapa fungsi dapat dibatasi.", mapOf(
        "ads" to "1", "telemetry" to "1", "mac_rand" to "1", "lock_priv" to "1", "loc_off" to "1", "adb_off" to "1",
        "ipv6_off" to "1", "clean_boot" to "1", "usb_lock" to "1", "wifi_open" to "1"
    )),
    PolicyPreset("performance", "Minimal", "Mengurangi pembatasan tambahan; hanya mempertahankan perlindungan iklan dan telemetri.", mapOf(
        "ads" to "1", "telemetry" to "1", "mac_rand" to "0", "lock_priv" to "0", "loc_off" to "0", "adb_off" to "0",
        "ipv6_off" to "0", "clean_boot" to "0", "usb_lock" to "0", "wifi_open" to "0"
    ))
)

@Composable
fun PolicyProfilesCard() {
    var applying by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("") }
    SectionCard("Profil kebijakan", "Preset mengubah sakelar yang tercantum. Tinjau kembali pengaturan setelah menerapkan.") {
        POLICY_PRESETS.forEach { preset ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(preset.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${preset.values.size} kebijakan akan diubah", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(enabled = !applying, onClick = {
                        selected = preset.id
                        applying = true
                        thread {
                            var success = true
                            preset.values.forEach { (key, value) ->
                                val result = Ps.set(key, value)
                                if (!result.ok) success = false
                            }
                            Store.refresh(Store.appCtx)
                            Store.message = if (success) "Profil ${preset.title} diterapkan" else "Profil ${preset.title} diterapkan sebagian; periksa status modul"
                            applying = false
                        }
                    }) { Text(if (applying && selected == preset.id) "Menerapkan…" else "Terapkan") }
                }
            }
        }
        if (applying) Text("Jangan tutup aplikasi sampai proses selesai.", modifier = Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodySmall)
    }
}
