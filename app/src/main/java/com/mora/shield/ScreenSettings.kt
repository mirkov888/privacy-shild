@file:OptIn(ExperimentalMaterial3Api::class)

package com.mora.shield

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

private fun nums(vararg v: Int, unit: String = ""): List<Pair<String, String>> =
    v.map { Pair(it.toString(), "$it$unit") }

private val ACTS_ALL = listOf("reboot" to "Reboot terkunci", "poweroff" to "Matikan HP", "lockdown" to "Lockdown", "wipe" to "HAPUS DATA")
private val WIPE_WARN =
    "HAPUS DATA tidak bisa dibatalkan setelah hitung mundur 60 detik selesai. Tetap butuh persetujuan terpisah dan mode uji harus dimatikan. " +
        "Hitung mundur batal bila HP dibuka atau file /sdcard/PS_off dibuat. Pilih hanya bila kamu paham risikonya."

@Composable
private fun BannerSettingsCard() {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf(BannerStore.files(ctx)) }
    var message by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { BannerStore.importZip(ctx, uri) }
                .onSuccess { count ->
                    files = BannerStore.files(ctx)
                    message = "$count banner berhasil diimpor dari ZIP."
                }
                .onFailure { message = "Gagal mengimpor ZIP: " + (it.message ?: "file tidak valid") }
        }
    }
    SectionCard("Banner Beranda", "Pilih banner dari ZIP koleksi. Banner bawaan tetap tersedia sebagai default.") {
        Text("Banner aktif: " + if (BannerStore.selectedIndex == 0) "Bawaan Mora Shield" else "Banner " + BannerStore.selectedIndex,
            modifier = Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                Text("Impor banner ZIP")
            }
            androidx.compose.material3.OutlinedButton(onClick = {
                BannerStore.select(ctx, 0)
                message = "Banner bawaan dipulihkan."
            }) { Text("Atur ulang default") }
        }
        if (files.isNotEmpty()) {
            Text("Pilih salah satu dari " + files.size + " gambar:", modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.labelLarge)
            files.forEachIndexed { index, file ->
                val bitmap = remember(file.absolutePath, file.lastModified()) { BitmapFactory.decodeFile(file.absolutePath) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)
                        .clickable { BannerStore.select(ctx, index + 1) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Banner " + (index + 1),
                        modifier = Modifier.width(112.dp).height(64.dp).padding(end = 10.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text("Banner " + (index + 1), style = MaterialTheme.typography.bodyMedium)
                        Text(file.name, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (BannerStore.selectedIndex == index + 1) {
                        Text("AKTIF", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        } else {
            EmptyHint("Belum ada banner kustom. Impor file banner1.zip dari penyimpanan HP.")
        }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        PageIntro("Pengaturan", "Atur perlindungan privasi dan perilaku sistem dari satu tempat.")
        BannerSettingsCard()
        if (Store.installed) PolicyProfilesCard()
        if (!Store.installed) {
            StatusCard()
            return@Column
        }
        ProfileCard()
        MonitorCard(ctx)
        PrivacyCard()
        ContentCard()
        SensorCard()
        DeviceCard()
        VpnCard()
        NetworkCard()
        AutoCard()
        TriggerCard()
    }
}

// ------------------------------------------------------------------ Profil

@Composable
fun ProfileCard() {
    var pick by remember { mutableStateOf<String?>(null) }
    val cur = Store.cfg["profile"] ?: "custom"
    SectionCard("Profil cepat", "Satu ketukan menerapkan sekumpulan proteksi. Profil saat ini: $cur") {
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in listOf("basic" to "Dasar", "journalist" to "Jurnalis", "paranoid" to "Penuh", "off" to "Mati")) {
                FilterChip(selected = cur == id, onClick = { pick = id }, label = { Text(label) })
            }
        }
        Text(
            "Profil hanya mengatur saklar inti (iklan, telemetri, DNS, kunci, sensor). Pengaturan lanjutan di bawah tidak diubah.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
    val p = pick
    if (p != null) Confirm("Terapkan profil?", "Saklar inti akan diset ulang sesuai profil '$p'.", "Terapkan", { pick = null }) {
        Store.run("profile $p", "Profil $p diterapkan") { Store.refresh(Store.appCtx) }
    }
}

// ------------------------------------------------------------------ Pemantauan

@Composable
fun MonitorCard(ctx: Context) {
    val prefs = ctx.getSharedPreferences("mora", Context.MODE_PRIVATE)
    var svc by remember { mutableStateOf(prefs.getBoolean("service", true)) }
    SectionCard("Pemantauan", "Seperti indikator privasi GrapheneOS: tahu siapa mengakses apa", collapsible = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText("Notifikasi kejadian", "Layanan latar di aplikasi ini; tombol Blokir/Izinkan/Abaikan di notifikasi", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = svc, onCheckedChange = { on ->
                svc = on
                prefs.edit().putBoolean("service", on).apply()
                if (on) {
                    try { ctx.startForegroundService(Intent(ctx, EventService::class.java)) } catch (e: Exception) { Store.message = "Gagal memulai: " + (e.message ?: "?") }
                } else {
                    ctx.stopService(Intent(ctx, EventService::class.java))
                }
            })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText("Sembunyikan dari tangkapan layar", "Layar aplikasi hitam di daftar aplikasi terbaru dan tidak bisa di-screenshot. Berlaku setelah aplikasi dibuka ulang.", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            var sec by remember { mutableStateOf(prefs.getBoolean("secure", true)) }
            Switch(checked = sec, onCheckedChange = { on -> sec = on; prefs.edit().putBoolean("secure", on).apply() })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText("Kunci aplikasi", "Membuka Mora Shield harus memakai PIN/pola/sidik jari layar kunci. Terkunci lagi setelah 30 detik di latar belakang.", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = Store.appLock, onCheckedChange = { on ->
                val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                if (on && !km.isDeviceSecure) {
                    Store.message = "Atur kunci layar HP dulu (PIN/pola/sandi)"
                } else {
                    Store.appLock = on
                    Store.unlocked = true
                    prefs.edit().putBoolean("applock", on).apply()
                }
            })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText("Notifikasi statistik", "Notifikasi tetap seperti TCCM: skor, kejadian hari ini, baterai, patch. Diperbarui tiap menit.", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            var sn by remember { mutableStateOf(prefs.getBoolean("statnotif", true)) }
            Switch(checked = sn, onCheckedChange = { on -> sn = on; prefs.edit().putBoolean("statnotif", on).apply() })
        }
        ActionRow("Daftar diabaikan", "Notifikasi dari aplikasi yang kamu 'Abaikan' akan muncul lagi", "Kosongkan") {
            prefs.edit().remove("ignored").apply(); Store.message = "Daftar diabaikan dikosongkan"
        }
        Divider()
        SwitchRow("Pantau akses mikrofon/kamera/lokasi", "Modul membaca AppOps secara berkala (butuh modul v3.0+)", "am_on")
        ChoiceRow("Interval pemeriksaan", "am_sec", nums(10, 20, 30, 60, 120, unit = " dtk"), "20", "Lebih kecil = akses singkat lebih mungkin terdeteksi, baterai lebih boros")
        MultiRow("Yang dipantau", "am_ops", listOf("mic" to "Mikrofon", "cam" to "Kamera", "loc" to "Lokasi", "contacts" to "Kontak", "sms" to "SMS", "calllog" to "Log panggilan"), "mic,cam,loc")
        SwitchRow("Termasuk aplikasi sistem", "Biasanya ramai; aktifkan hanya bila perlu", "am_sys")
        SwitchRow("Notifikasi dari modul sendiri", "Cadangan bila aplikasi ini dimatikan sistem", "am_notify")
        Divider()
        SwitchRow("Pantau data keluar saat layar mati", "Memberi tahu bila aplikasi mengirim banyak data diam-diam", "nb_on")
        ChoiceRow("Batas data", "nb_mb", nums(5, 10, 25, 50, 100, 500, unit = " MB"), "25")
        Divider()
        SwitchRow("Tahan internet aplikasi baru", "Aplikasi yang baru dipasang diblokir sampai kamu izinkan (tab Aplikasi > Baru)", "na_on")
    }
}

// ------------------------------------------------------------------ Privasi

@Composable
fun PrivacyCard() {
    SectionCard("Iklan, pelacak, jaringan", "Lapisan dasar", collapsible = true, startOpen = false) {
        SwitchRow("Blokir iklan dan pelacak", "Daftar hosts sistemless", "ads")
        SwitchRow("Blokir telemetri", "Domain analitik OEM dan Google", "telemetry")
        ChoiceRow(
            "DNS pribadi", "dns",
            listOf(
                "off" to "Mati", "adguard" to "AdGuard", "quad9" to "Quad9", "mullvad" to "Mullvad",
                "cloudflare" to "Cloudflare", "adguard_family" to "AdGuard Keluarga",
                "cleanbrowsing" to "CleanBrowsing", "cloudflare_family" to "Cloudflare Keluarga"
            ),
            "off", "Modul otomatis kembali ke DNS biasa bila DNS pribadi gagal."
        )
        if (Store.cfg["eos"] == "1" && Store.cfg["adv_privacy"] == "1") {
            InfoNote("/e/OS terdeteksi dengan Advanced Privacy. Bila pemblokir pelacaknya aktif (VPN lokal${if ((Store.cfg["vpn_active"] ?: "0") != "0") " sedang menyala" else ""}), DNS pribadi di sini bisa saling menimpa. Pilih salah satu bila internet bermasalah.")
        }
        SwitchRow("Acak MAC Wi-Fi", "Memakai pengacakan bawaan Android (tidak dipaksa)", "mac_rand")
        SwitchRow("Matikan IPv6", "Menutup kebocoran lewat IPv6", "ipv6_off")
    }
}

@Composable
fun ContentCard() {
    var ask by remember { mutableStateOf<String?>(null) }
    var pinMode by remember { mutableStateOf("") }
    SectionCard("Konten dewasa dan judi", "Bisa dikunci diri supaya tidak mudah dimatikan", collapsible = true, startOpen = false) {
        ProtectedRow("Blokir situs dewasa", "adult_block") { ask = it }
        ProtectedRow("Blokir judi online", "gambling_block") { ask = it }
        ProtectedRow("Kunci diri (self-lock)", "selflock") { ask = it }
        ChoiceRow("Tunggu sebelum boleh mati", "sl_h", nums(1, 6, 12, 24, 48, 72, unit = " jam"), "24", "Bila tidak pakai PIN, mematikan baru berlaku setelah jeda ini.")
        ActionRow("PIN kunci diri", "4-12 angka. Dengan PIN, bisa dimatikan langsung.", "Atur") { pinMode = "set" }
        ActionRow("Hapus PIN", null, "Hapus") { pinMode = "clear" }
    }
    val k = ask
    if (k != null) PinOffDialog(k, onDone = { ask = null })
    if (pinMode.isNotEmpty()) PinSetDialog(pinMode, onDone = { pinMode = "" })
}

@Composable
fun ProtectedRow(title: String, key: String, onOff: (String) -> Unit) {
    val on = Store.cfg.on(key)
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(title, null, Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { v -> if (v) Store.set(key, "1") else onOff(key) })
    }
}

@Composable
fun PinOffDialog(key: String, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Matikan perlindungan?") },
        text = {
            Column {
                Text("Masukkan PIN untuk mematikan langsung. Kosongkan untuk menjadwalkan penonaktifan setelah jeda tunggu.")
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12) }, singleLine = true,
                    label = { Text("PIN (opsional)") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(); Store.set(key, "0", pin) }) { Text("Lanjut") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Batal") } }
    )
}

@Composable
fun PinSetDialog(mode: String, onDone: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (mode == "set") "Atur PIN" else "Hapus PIN") },
        text = {
            Column {
                if (mode == "set") {
                    OutlinedTextField(a, { a = it.filter { c -> c.isDigit() }.take(12) }, singleLine = true, label = { Text("PIN baru") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                    OutlinedTextField(b, { b = it.filter { c -> c.isDigit() }.take(12) }, singleLine = true, label = { Text("PIN lama (bila sudah ada)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                } else {
                    OutlinedTextField(a, { a = it.filter { c -> c.isDigit() }.take(12) }, singleLine = true, label = { Text("PIN saat ini") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone()
                val args = if (mode == "set") "pin set $a" + (if (b.isNotEmpty()) " $b" else "") else "pin clear $a"
                if (a.isEmpty()) Store.message = "PIN kosong" else Store.run(args, if (mode == "set") "PIN disimpan" else "PIN dihapus")
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Batal") } }
    )
}

// ------------------------------------------------------------------ Sensor

@Composable
fun SensorCard() {
    SectionCard("Sensor dan lokasi", collapsible = true, startOpen = false) {
        SwitchRow("Blokir mikrofon", "Lewat saklar privasi Android (butuh dukungan ROM)", "mic_block")
        SwitchRow("Blokir kamera", null, "cam_block")
        SwitchRow("Matikan lokasi", null, "loc_off")
    }
}

// ------------------------------------------------------------------ Perangkat

@Composable
fun DeviceCard() {
    SectionCard("Perangkat dan kunci", collapsible = true, startOpen = false) {
        SwitchRow("Privasi layar kunci", "Sembunyikan isi notifikasi", "lock_priv")
        SwitchRow("Matikan ADB", "Tutup debugging USB", "adb_off")
        SwitchRow("Matikan NFC", null, "nfc_off")
        SwitchRow("Matikan Bluetooth", null, "bt_off")
        SwitchRow("Bersihkan cache saat boot", null, "clean_boot")
        SwitchRow("Kunci data USB saat terkunci", "Mencegah akses data lewat kabel ketika layar terkunci", "usb_lock")
        SwitchRow("Putar ID iklan", "Reset ID iklan Google secara berkala", "id_rot")
        ChoiceRow("Setiap", "id_days", nums(1, 3, 7, 14, 30, unit = " hari"), "7")
        Divider()
        SwitchRow("Matikan biometrik bila lama tak dibuka", "Hanya PIN yang bisa membuka setelah jeda", "bio_on")
        ChoiceRow("Jeda", "bio_h", nums(1, 2, 4, 8, 12, 24, 48, 72, unit = " jam"), "8")
        DryRow("bio_dry", "pengunci biometrik")
    }
}

// ------------------------------------------------------------------ Jaringan

@Composable
fun NetworkCard() {
    SectionCard("Jaringan lanjutan", collapsible = true, startOpen = false) {
        SwitchRow("Peringatan Wi-Fi terbuka", null, "wifi_open")
        SwitchRow("Mode Wi-Fi ketat", "Blokir Wi-Fi tanpa enkripsi", "wifi_strict")
        SwitchRow("Firewall saat layar mati", null, "fw_screenoff")
        SwitchRow("Batasi jaringan 2G", "Mengurangi risiko stasiun palsu (tergantung perangkat)", "net2g")
        Divider()
        SwitchRow("Matikan Wi-Fi/Bluetooth otomatis", "Timer: mati bila lama tidak dipakai", "wbt_on")
        ChoiceRow("Setelah", "wbt_min", nums(5, 10, 15, 30, 60, 120, unit = " menit"), "30")
        SwitchRow("Wi-Fi ikut dimatikan", null, "wbt_wifi")
        SwitchRow("Bluetooth ikut dimatikan", null, "wbt_bt")
        SwitchRow("Nyalakan lagi saat layar dibuka", null, "wbt_restore")
        DryRow("wbt_dry", "pematian Wi-Fi/Bluetooth")
        Divider()
        SwitchRow("Tor per aplikasi", "Pilih aplikasi yang lewat Orbot (butuh Orbot dengan port transparan)", "tor_on")
        TorAppsRow()
        ChoiceRow("Port transparan", "tor_trans", listOf("9040" to "9040 (bawaan)", "9041" to "9041"), "9040")
        ChoiceRow("Port DNS", "tor_dns", listOf("5400" to "5400 (bawaan)", "5353" to "5353"), "5400")
        if (Store.cfg["eos"] == "1" && Store.cfg["adv_privacy"] == "1") {
            InfoNote("/e/OS: fitur 'Hide My IP' dan kill switch/Tor di sini bisa tumpang tindih karena sama-sama memakai VPN lokal. Pakai salah satu per aplikasi.")
        }
    }
}

// ------------------------------------------------------------------ Otomatis

@Composable
fun AutoCard() {
    SectionCard("Reboot otomatis", "Membersihkan memori dan mengunci ulang HP", collapsible = true, startOpen = false) {
        SwitchRow("Reboot terjadwal", null, "autoreboot")
        ChoiceRow("Setiap", "autoreboot_h", nums(6, 12, 24, 48, 72, unit = " jam"), "12")
        DryRow("autoreboot_dry", "reboot")
    }
}

// ------------------------------------------------------------------ Pemicu darurat

@Composable
fun TriggerCard() {
    var rec by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf<String?>(null) }
    SectionCard("Pemicu darurat", "Aksi otomatis. Semua mulai dalam MODE UJI; hapus data butuh persetujuan terpisah.", collapsible = true, startOpen = false) {
        Text("Kombinasi tombol", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        SwitchRow("Aktifkan kombinasi tombol", "Pola tekanan volume/daya memicu aksi", "kt_on")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText("Pola saat ini", (Store.cfg["kt_seq"] ?: "").ifEmpty { "belum diatur" }, Modifier.weight(1f))
            TextButton(onClick = { rec = true }) { Text("Rekam") }
        }
        ChoiceRow("Jendela waktu", "kt_win", nums(3, 5, 8, 10, 15, unit = " dtk"), "8")
        ChoiceRow("Aksi", "kt_act", listOf("reboot" to "Reboot terkunci", "poweroff" to "Matikan HP", "panic" to "Panic", "lockdown" to "Lockdown"), "reboot")
        DryRow("kt_dry", "aksi tombol")
        ActionRow("Uji pemicu tombol", "Hanya notifikasi uji, tidak menjalankan aksi", "Uji") { Store.run("acttest kt", "Uji dikirim") }
        Divider()
        Text("Dead-man switch", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        SwitchRow("Aktifkan dead-man switch", "Bertindak bila HP tidak dibuka beberapa hari", "dm_on")
        ChoiceRow("Tahap 1 setelah", "dm_d1", nums(1, 3, 7, 14, 30, unit = " hari"), "7")
        ChoiceRow("Aksi tahap 1", "dm_a1", listOf("reboot" to "Reboot terkunci", "poweroff" to "Matikan HP", "lockdown" to "Lockdown"), "reboot")
        ChoiceRow("Tahap 2 setelah", "dm_d2", listOf("0" to "Mati") + nums(7, 14, 21, 30, 60, 90, unit = " hari"), "0")
        ChoiceRow("Aksi tahap 2", "dm_a2", ACTS_ALL, "reboot", confirmValue = "wipe" to WIPE_WARN)
        Approval("dm_wipe_ok", "Setujui HAPUS DATA oleh dead-man", "dm_a2") { wipe = it }
        DryRow("dm_dry", "aksi dead-man")
        Row(Modifier.padding(horizontal = 18.dp)) {
            TextButton(onClick = { Store.run("acttest dm1", "Uji tahap 1 dikirim") }) { Text("Uji tahap 1") }
            TextButton(onClick = { Store.run("acttest dm2", "Uji tahap 2 dikirim") }) { Text("Uji tahap 2") }
        }
        Divider()
        Text("Salah PIN berulang", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        SwitchRow("Aktifkan", "Aksi setelah N kali salah memasukkan PIN layar kunci", "fp_on")
        ChoiceRow("Jumlah salah", "fp_n", nums(5, 6, 8, 10, 15, 20, 30), "10")
        ChoiceRow("Aksi", "fp_act", ACTS_ALL.filter { true }, "reboot", confirmValue = "wipe" to WIPE_WARN)
        Approval("fp_wipe_ok", "Setujui HAPUS DATA oleh salah PIN", "fp_act") { wipe = it }
        DryRow("fp_dry", "aksi salah PIN")
        ActionRow("Uji salah PIN", "Hanya notifikasi uji", "Uji") { Store.run("acttest fp", "Uji dikirim") }
    }
    if (rec) KeyRecordDialog { rec = false }
    val w = wipe
    if (w != null) Confirm(
        "Setujui HAPUS DATA?",
        "Dengan persetujuan ini dan mode uji dimatikan, HP akan di-factory-reset setelah hitung mundur 60 detik. Batal otomatis bila HP dibuka atau file /sdcard/PS_off ada.",
        "Setujui", { wipe = null }
    ) { Store.set(w, "1") }
}

@Composable
fun Approval(approveKey: String, title: String, actKey: String, ask: (String) -> Unit) {
    val usesWipe = (Store.cfg[actKey] ?: "") == "wipe"
    if (!usesWipe && !Store.cfg.on(approveKey)) return
    val on = Store.cfg.on(approveKey)
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(title, "Pengaman terpisah. Tanpa ini aksi hapus data ditolak.", Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = { v -> if (v) ask(approveKey) else Store.set(approveKey, "0") })
    }
}

@Composable
fun KeyRecordDialog(onDone: () -> Unit) {
    var state by remember { mutableStateOf("ready") }
    var out by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Rekam pola tombol") },
        text = {
            Text(
                when (state) {
                    "ready" -> "Tekan Mulai, lalu dalam 8 detik tekan tombol volume/daya sesuai pola yang diinginkan (4-12 tekanan; disarankan minimal 8)."
                    "run" -> "Merekam… tekan tombol sekarang."
                    else -> out
                }
            )
        },
        confirmButton = {
            if (state == "ready") TextButton(onClick = {
                state = "run"
                Store.run("keyrec", "Selesai merekam") { o ->
                    val seq = o.lines().firstOrNull { it.startsWith("SEQ|") }?.removePrefix("SEQ|") ?: ""
                    val n = if (seq.isEmpty()) 0 else seq.split(",").size
                    if (n in 4..12) {
                        Store.set("kt_seq", seq)
                        out = "Pola tersimpan ($n tekanan): $seq"
                    } else {
                        out = "Tidak valid ($n tekanan). Butuh 4-12. " + o.take(120)
                    }
                    state = "done"
                }
            }) { Text("Mulai") } else if (state == "done") TextButton(onClick = onDone) { Text("Selesai") }
        },
        dismissButton = { if (state != "run") TextButton(onClick = onDone) { Text("Tutup") } }
    )
}

// ------------------------------------------------------------------ Pilihan ganda

@Composable
fun MultiRow(title: String, key: String, options: List<Pair<String, String>>, default: String) {
    val cur = (Store.cfg[key]?.ifEmpty { default } ?: default).split(",").filter { it.isNotBlank() }.toSet()
    var open by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(cur) }
    Row(
        Modifier.fillMaxWidth().clickableRow { sel = cur; open = true }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, null, Modifier.weight(1f))
        Text("${cur.size} dipilih", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    for ((v, l) in options) {
                        Row(
                            Modifier.fillMaxWidth().clickableRow { sel = if (v in sel) sel - v else sel + v },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = v in sel, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(l, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    val ordered = options.map { it.first }.filter { it in sel }
                    if (ordered.isEmpty()) Store.message = "Pilih minimal satu"
                    else Store.set(key, ordered.joinToString(","))
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun TorAppsRow() {
    val cur = (Store.cfg["tor_apps"] ?: "").split(",").filter { it.isNotBlank() }.toSet()
    var open by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(cur) }
    Row(
        Modifier.fillMaxWidth().clickableRow { sel = cur; open = true }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText("Aplikasi lewat Tor", null, Modifier.weight(1f))
        Text("${cur.size} aplikasi", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Aplikasi lewat Tor") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (a in Store.apps) {
                        Row(
                            Modifier.fillMaxWidth().clickableRow { sel = if (a.pkg in sel) sel - a.pkg else sel + a.pkg },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = a.pkg in sel, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false; Store.set("tor_apps", sel.joinToString(",")) }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } }
        )
    }
}


@Composable
fun VpnCard() {
    val ctx = LocalContext.current
    val always = (Store.cfg["vpn_always"] ?: "").let { if (it == "null") "" else it }
    val lock = Store.cfg["vpn_lockdown"] == "1"
    val tun = (Store.cfg["vpn_active"] ?: "0") != "0"
    SectionCard("VPN dan kill switch", "Status: " + (if (tun) "VPN aktif" else "tidak ada VPN aktif"), collapsible = true, startOpen = false) {
        Text(
            "VPN Android selalu-aktif: " + (if (always.isEmpty()) "tidak diatur" else always) + (if (always.isNotEmpty()) (if (lock) " · blokir tanpa VPN: ya" else " · blokir tanpa VPN: tidak") else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )
        SwitchRow("Kill switch modul", "Putus internet bila VPN/Tor terputus", "killswitch", confirmOn = "Bila VPN belum aktif, internet akan terputus. Bila terkunci, pakai 'Perbaiki internet' di Beranda.")
        AppPickRow("Aplikasi VPN yang diizinkan", "vpn_pkgs", "bawaan (WireGuard, Mullvad, Proton, OpenVPN, Orbot, dll.)")
        ActionRow("Pengaturan VPN Android", "Pilih VPN selalu-aktif dan 'Blokir koneksi tanpa VPN' (kill switch bawaan Android, paling andal)", "Buka") {
            try {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Store.message = "Tidak bisa membuka pengaturan VPN"
            }
        }
        InfoNote("Aplikasi ini tidak membuat atau menjalankan VPN sendiri (tanpa izin internet). Pasang klien VPN pilihanmu lalu aturnya di sini. Tes kebocoran IP/DNS butuh internet, jadi ada di WebUI modul.")
    }
}

@Composable
fun AppPickRow(title: String, key: String, emptyLabel: String) {
    val cur = (Store.cfg[key] ?: "").split(",").filter { it.isNotBlank() }.toSet()
    var open by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(cur) }
    Row(
        Modifier.fillMaxWidth().clickableRow { sel = cur; open = true }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, if (cur.isEmpty()) emptyLabel else null, Modifier.weight(1f))
        Text(if (cur.isEmpty()) "bawaan" else "${cur.size} aplikasi", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Kosongkan semua pilihan untuk memakai daftar bawaan.", style = MaterialTheme.typography.bodySmall)
                    for (a in Store.apps) {
                        Row(
                            Modifier.fillMaxWidth().clickableRow { sel = if (a.pkg in sel) sel - a.pkg else sel + a.pkg },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = a.pkg in sel, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false; Store.set(key, sel.joinToString(",")) }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } }
        )
    }
}
