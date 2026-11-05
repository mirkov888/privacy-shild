# Mora Shield 3.4 (Material 3)

Aplikasi pendamping modul **Mora Privacy Shield v3.3.0**. Semua pengaturan modul bisa diubah dari aplikasi tanpa WebUI, lengkap dengan statistik.

## Isi
- **Beranda**: skor perlindungan, kejadian 7 hari, jam rawan, jenis kejadian, aplikasi teraktif, tingkat pelacak, aksi cepat.
- **Aktivitas**: riwayat kejadian dengan filter.
- **Aplikasi**: cari, blokir internet per aplikasi, lihat SDK pelacak, izinkan aplikasi baru.
- **Pengaturan**: profil, pemantauan, DNS, konten, sensor, jaringan, Tor, reboot otomatis, pemicu darurat (semua dengan mode uji).
- **Alat**: PANIC/lockdown, pindai pelacak, laporan audit, kompatibilitas, MVT, UltimatePrivacy, Kernel Manager (governor CPU), Script root, diagnostik.

## Sinkron dengan modul
Aplikasi memeriksa `ps.sh rev` tiap 6 detik saat dibuka. Bila WebUI mengubah pengaturan, aplikasi memuat ulang otomatis (butuh modul v3.2+). Perubahan dari aplikasi langsung dipakai modul dan terlihat di WebUI.

## Cara build (GitHub Actions)
1. Unggah semua file di folder ini ke repositori (timpa yang lama). File berawalan titik tidak perlu diunggah ulang bila workflow sudah ada.
2. Pastikan `.github/workflows/build.yml` ada (isi di `ISI-build.yml.txt`).
3. Tab Actions > Build Mora Shield APK > Run workflow. APK ada di Artifacts.
4. Bila gagal, kirim log baris error pertama.

Aplikasi tidak memakai izin internet. Butuh root (KernelSU/Magisk) dan modul terpasang.

## Tautan (v3.2)
Tab **Alat > Tautan**: bersihkan parameter pelacak (utm_, fbclid, gclid, si=, dll), bongkar tautan pengalihan (Google, Facebook, Reddit, dll), lalu alihkan YouTube/X/Reddit ke frontend privat (Invidious/Piped/NewPipe/LibreTube, Nitter, Redlib). Menu Bagikan > "Mora Shield: tautan bersih" selalu bisa dipakai. Pengalihan otomatis saat mengetuk tautan butuh izin sekali di Pengaturan sistem > Buka secara default. Semua lokal; aturan ditanam di aplikasi.

## v3.3.0
- Alat: **Uji sistem** (fitur modul mana yang jalan), **Pemeriksa tanda mencurigakan** (heuristik), **Cadangan pengaturan** (simpan/pulihkan ke file).
- Beranda: **Profil situasi** (Demo/Protes, Perjalanan, Rumah), **Laporan 7 hari**, kartu **Pengaman boot**.
- Aplikasi: firewall per jenis jaringan (Wi-Fi/seluler/semua) dan linimasa per aplikasi.
- Pengaturan: **Kunci aplikasi** (kunci layar HP), tile cepat **PANIC** dan **Mode demo**.
- Butuh modul v3.4.0.

## v3.4.0
- **Notifikasi statistik** tetap (gaya TCCM): skor, kejadian hari ini (mikrofon/kamera/lokasi/kontak), data keluar besar, aplikasi diblokir, baterai, RAM, patch, versi modul. Diperbarui tiap menit; bisa dimatikan di Pengaturan > Pemantauan.
- **VPN dan kill switch** di Pengaturan: status VPN selalu-aktif Android, kill switch modul, daftar aplikasi VPN yang diizinkan, pintasan ke pengaturan VPN.
- **Jejak root dan bootloader** di Alat: apa yang terlihat oleh aplikasi lain (hanya membaca), daftar alat penyembunyi terpasang (Zygisk/Shamiko/PIF/LSPosed/SuSFS), pintasan ke pengelola root.
