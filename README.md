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


## v1.3.0 integration update
- Single app controls Mora Privacy Shield and the embedded UltimatePrivacy helper.
- Kernel tab includes CPU governor presets (only governors advertised by each policy) and opt-in apply-on-boot restoration.
- Kernel monitoring discovers CPU policy, GPU/devfreq, RAM/ZRAM, I/O scheduler, thermal zones and battery sysfs where available.
- Script Manager stores user scripts in `/data/adb/privacyshield/scripts`; scripts execute as root only after confirmation.
- Module extension commands are routed to `bin/ultimateprivacy.sh`; no second module ID is installed.
- Kernel apply-on-boot writes only CPU governor values to `/data/adb/privacyshield/kernel_governors.conf`; the module validates each against `scaling_available_governors`.
- This version does not change CPU min/max frequency, core online state, GPU frequency, thermal limits or charging current. Those controls require device-specific validation.
- UI is the existing native Android Views UI with added sections; this build does not claim a full Jetpack Compose Material 3 redesign.
