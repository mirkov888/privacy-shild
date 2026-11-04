# Mora Shield (aplikasi pendamping)

Aplikasi pendamping untuk modul **Mora Privacy Shield v3.0+** (KernelSU/Magisk).
Menampilkan kejadian akses sensor, aplikasi baru, dan data keluar besar sebagai notifikasi
dengan tombol Blokir/Izinkan/Abaikan, daftar aplikasi dengan skor pelacak, serta tombol darurat.

## Cara membangun APK dengan GitHub
1. Buat repository baru di GitHub (boleh privat).
2. Unggah semua isi folder ini lewat **Add file > Upload files** (tidak ada berkas tersembunyi di dalamnya).
3. Buat berkas workflow langsung di GitHub, karena nama folder `.github` tersembunyi dan tidak bisa diunggah:
   - Klik **Add file > Create new file**.
   - Di kolom nama, ketik persis: `.github/workflows/build.yml` (garis miring otomatis membuat folder).
   - Salin seluruh isi `ISI-build.yml.txt` ke kolom isi, lalu **Commit changes**.
4. Buka tab **Actions** (aktifkan bila diminta). Workflow **Build APK** berjalan otomatis setelah commit, atau jalankan lewat **Run workflow**.
5. Setelah selesai, buka hasil build dan unduh artifact **MoraShield-apk**, lalu pasang `app-release.apk`.
6. Di KernelSU Manager > Superuser izinkan Mora Shield, dan izinkan notifikasi.

Bila build gagal, salin log langkah "Build release APK" dan kirim untuk diperbaiki.

## Catatan
- Kode ini belum pernah dikompilasi oleh pembuatnya (lingkungan tanpa Android SDK), jadi build pertama bisa menemukan kesalahan kecil.
- Aplikasi ditandatangani dengan kunci debug supaya langsung bisa dipasang.
- Butuh Android 8 (API 26) atau lebih baru.
