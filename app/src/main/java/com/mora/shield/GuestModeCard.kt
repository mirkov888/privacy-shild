package com.mora.shield

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

@Composable
fun GuestModeCard() {
    val context = LocalContext.current
    var minutes by remember { mutableStateOf(60) }
    var askStart by remember { mutableStateOf(false) }
    SectionCard("Mode pinjam HP", "Vault sementara dengan pemulihan otomatis", collapsible = true, startOpen = false) {
        Text("Durasi", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 120, 240, 480).forEach { value ->
                FilterChip(selected = minutes == value, onClick = { minutes = value },
                    label = { Text(if (value < 60) "$value menit" else "${value / 60} jam") })
            }
        }
        ActionRow("Aktifkan mode pinjam", "Vault menyala, layar dikunci sekali, lalu vault dibuka otomatis saat durasi habis.", "Mulai") {
            askStart = true
        }
        ActionRow("Akhiri mode pinjam sekarang", "Batalkan timer dan pulihkan vault lebih awal", "Pulihkan", danger = true) {
            GuestModeScheduler.cancel(context)
            thread {
                val r = Ps.run("vault off")
                Store.message = if (r.ok) "Mode pinjam diakhiri; vault dipulihkan." else "Gagal memulihkan vault: ${r.out.take(100)}"
                Store.refresh(context)
            }
        }
        InfoNote("Ini memakai daftar aplikasi pada konfigurasi vault dan mengunci layar sekali. Ini bukan PIN terpisah untuk setiap aplikasi.", true)
    }
    if (askStart) Confirm(
        "Aktifkan mode pinjam?", "Aplikasi dalam daftar vault akan dibatasi selama durasi terpilih. Layar akan dikunci sekali; pemulihan dijadwalkan otomatis.",
        "Aktifkan", { askStart = false }
    ) {
        askStart = false
        thread {
            val r = Ps.run("vault on")
            if (r.ok) {
                GuestModeScheduler.schedule(context, minutes)
                Ps.run("lockdown")
                Store.message = "Mode pinjam aktif selama ${if (minutes < 60) "$minutes menit" else "${minutes / 60} jam"}."
            } else {
                Store.message = "Gagal mengaktifkan vault: ${r.out.take(120)}"
            }
            Store.refresh(context)
        }
    }
}
