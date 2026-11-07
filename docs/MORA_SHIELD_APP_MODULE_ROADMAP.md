# Mora Shield + Mora Privacy Shield Module — Roadmap

Mora Shield tetap menjadi pusat kontrol. MoraDownloader dan MoraSystemManager tetap aplikasi pendamping. Fitur yang memerlukan root dijalankan oleh modul/worker, bukan melalui arbitrary shell Connector IPC.

## Prinsip arsitektur
- **Mora Shield app:** dashboard, registry, kebijakan, persetujuan, audit, visualisasi, diagnostik.
- **Mora Core:** registry, autentikasi konektor, policy engine, event timeline, command audit.
- **Mora Privacy Shield module:** enforcement root, konfigurasi boot, firewall, sensor/privacy controls, pemulihan.
- **Companion apps:** handler capability masing-masing, hanya menerima perintah yang diizinkan.
- Default deny, signature verification, capability allowlist, approval per tindakan, audit lokal.
- Tidak mengirim data perangkat ke server tanpa persetujuan eksplisit.

## Update aplikasi Mora Shield

### 3.6.x — Stabilitas dan integrasi
- Halaman Mora Core khusus.
- Registry tiga aplikasi dengan status `INTERNAL`, `NOT_INSTALLED`, `INSTALLED`, `CONNECTED`.
- Permission center per aplikasi, status heartbeat dan last-seen.
- Timeline aktivitas terpadu dan command audit.
- Screen Time harian (screen-on dan standby).
- Terminal lokal dengan mode root manual.
- Uji kompatibilitas ROM, Android API, signer dan Connector SDK.

### 3.7 — Dashboard dan kontrol
- Dashboard koneksi companion, versi, API dan perubahan capability.
- Profil kebijakan: Balanced, Strict Privacy, Travel, Battery Saver.
- Per-app usage via UsageStats dengan izin khusus yang diminta secara transparan.
- Riwayat screen time harian/mingguan.
- Notifikasi event prioritas tinggi dengan batching dan rate limit.
- Export audit lokal dengan redaksi data sensitif.

### 3.8 — Diagnostik dan pemulihan
- Health check Core DB, EventService, Connector heartbeat, module version dan worker.
- Diagnostic bundle dengan preview dan redaction.
- Backup/restore kebijakan, bukan token sesi atau data privat aplikasi.
- Deteksi signer mismatch dan rekomendasi reinstall companion.
- UI/instrumentation tests untuk Android 13–16.

### 4.x — Ekosistem
- SDK version negotiation dan migrasi API.
- Permission templates per capability.
- Widget status Core dan quick actions.
- Multi-profile policy dan jadwal kebijakan lokal.
- Dokumentasi API, changelog dan compatibility matrix.

## Update modul Mora Privacy Shield

### 3.6 — Reliability
- Recovery service setelah reboot dan force-stop.
- Validasi konfigurasi sebelum diterapkan dan rollback otomatis jika gagal.
- Health state: active, degraded, unsupported, failed.
- Logging terstruktur dengan rotasi dan batas ukuran.
- Kurangi polling; gunakan event observer bila aman.

### 3.7 — Network privacy
- Per-app firewall berbasis UID dengan status rule yang dapat diverifikasi.
- Statistik upload/download per UID tanpa merekam payload.
- DNS policy per profile, indikator DNS leak dan fallback aman.
- Aturan IPv4/IPv6 konsisten dan pemeriksaan setelah pergantian jaringan.
- Dry-run untuk menampilkan dampak rule sebelum penerapan.

### 3.8 — Sensor dan hardening
- Status sensor privacy dibaca dari sumber sistem, bukan sekadar flag.
- Audit perubahan permission/app-op sesuai batasan Android.
- Boot integrity/config validation dan pemulihan konfigurasi rusak.
- Thermal-aware CPU governor; jangan mengubah voltase atau menonaktifkan thermal protection.
- Compatibility matrix per ROM/kernel dan fitur unsupported dinonaktifkan.

### 4.x — Update modul
- Manifest update bertanda tangan dan checksum SHA-256.
- Staged rollout: canary → stable, dengan rollback.
- Preflight Android version, ABI, kernel, root manager dan versi app.
- Migrasi konfigurasi idempotent dan backup sebelum upgrade.
- Changelog, known issues dan recovery steps untuk tiap rilis.

## Prioritas
1. Build dan tes Phase 3 pada perangkat nyata.
2. Tes tiga companion dengan signer sama dan heartbeat CONNECTED.
3. Validasi default-deny, allowlist, approval dan audit.
4. Stabilkan EventService dan screen-time sebelum statistik per aplikasi.
5. Implementasi network UID monitoring dan signed module updater.

## Batasan
- Screen Time Phase 3 menghitung total layar aktif/standby, bukan durasi per aplikasi.
- Network/background policy hanya kebijakan pusat sampai worker mengimplementasikan enforcement.
- Terminal adalah terminal lokal, bukan remote shell Connector.
- `CONNECTED` bergantung pada sesi authenticated connector dan heartbeat.
- Firewall, sensor, app-op dan CPU features bergantung Android, ROM, kernel dan root.
