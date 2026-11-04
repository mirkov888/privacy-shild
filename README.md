# Mora Shield 3.0 (Material 3)

Aplikasi pendamping modul **Mora Privacy Shield v3.2.0**. Semua pengaturan modul bisa diubah dari aplikasi tanpa WebUI, lengkap dengan statistik.

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
