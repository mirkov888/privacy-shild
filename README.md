# Mora Shield (aplikasi pendamping)

Aplikasi pendamping untuk modul **Mora Privacy Shield v3.0+** (KernelSU/Magisk).
Menampilkan kejadian akses sensor, aplikasi baru, dan data keluar besar sebagai notifikasi
dengan tombol Blokir/Izinkan/Abaikan, daftar aplikasi dengan skor pelacak, serta tombol darurat.

## Cara membangun APK dengan GitHub
1. Buat repository baru di GitHub (boleh privat).
2. Unggah **seluruh isi folder ini** (termasuk folder tersembunyi `.github`).
3. Buka tab **Actions**, pilih workflow **Build APK**, jalankan (**Run workflow**) bila belum jalan otomatis.
4. Setelah selesai, buka hasil build dan unduh artifact **MoraShield-apk**.
5. Pasang `app-release.apk` di HP. Izinkan root untuk aplikasi di KernelSU Manager > Superuser, dan izinkan notifikasi.

Bila build gagal, salin log langkah "Build release APK" dan kirim untuk diperbaiki.

## Catatan
- Kode ini belum pernah dikompilasi oleh pembuatnya (lingkungan tanpa Android SDK), jadi build pertama bisa menemukan kesalahan kecil.
- Aplikasi ditandatangani dengan kunci debug supaya langsung bisa dipasang.
- Butuh Android 8 (API 26) atau lebih baru.
