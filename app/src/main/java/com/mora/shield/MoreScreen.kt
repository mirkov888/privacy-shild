package com.mora.shield

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class MoreEntry(val title: String, val subtitle: String, val symbol: String, val route: Int)

@Composable
fun MoreScreen(onSelect: (Int) -> Unit) {
    val entries = listOf(
        MoreEntry("Terminal", "Shell Android dan perintah root dalam halaman khusus", "⌁", 8),
        MoreEntry("Pengaturan", "Preferensi aplikasi, keamanan, dan konfigurasi modul", "⚙", 4),
        MoreEntry("Mora Core", "App Registry, konektor, kebijakan aplikasi, dan audit perintah", "◈", 5),
        MoreEntry("Keamanan & Privasi", "Deteksi risiko jaringan, audit izin, sembunyikan/pulihkan aplikasi, dan ekspor laporan", "🛡", 9),
        MoreEntry("Statistik Jaringan", "Riwayat penggunaan data total dan per aplikasi", "⇅", 10),
        MoreEntry("Integrasi Modul v3.8", "Pengawas jaringan, audit izin, trafik, brankas, dan profil border/home", "◉", 11),
        MoreEntry("Kontrol aplikasi", "Bekukan dan aktifkan kembali aplikasi pihak ketiga secara individual", "◫", 12),
        MoreEntry("Per-App Location", "Profil lokasi statis, random, radius, dan interval untuk tiap aplikasi", "⌖", 13),
        MoreEntry("Alat", "Diagnostik, pemantauan layar, Kernel Manager, dan utilitas", "⌘", 6)
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                Text("Pusat fitur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Fitur lanjutan dipisahkan agar navigasi utama tetap ringkas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(entries, key = { it.route }) { entry ->
            MoreItem(entry.title, entry.subtitle, entry.symbol) { onSelect(entry.route) }
        }
    }
}

@Composable
private fun MoreItem(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(180)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(symbol, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
