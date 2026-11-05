# Mora Core Phase 1 — Mora Shield Master Controller

Patch untuk repository `mirkov888/privacy-shild`. Mora Core ditanam di APK Mora Shield yang sudah ada; tidak membuat APK kedua.

## Isi

- `app/src/main/java/com/mora/shield/core/` — Room DB, app registry, event hub, policy engine, command router, sanitizer, model.
- `MoraShieldApplication.kt` — inisialisasi Core saat proses aplikasi dibuat.
- `MoraCoreCard.kt` — ringkasan registry dan timeline pada menu Alat.
- `apply-mora-core-phase1.py` — installer idempotent ke project yang sudah di-clone.
- `docs/MORA_CORE_PHASE1.md` — rincian fase dan batas keamanan.
- `VALIDATION.md` — hasil pemeriksaan yang dilakukan dan batas validasinya.

## Pemasangan

Ekstrak ZIP, lalu jalankan:

```bash
python3 apply-mora-core-phase1.py ~/privacy-shild
cd ~/privacy-shild
./gradlew assembleRelease --no-daemon
```

Periksa hasil perubahan:

```bash
git status --short
git diff -- app/build.gradle.kts build.gradle.kts app/src/main/AndroidManifest.xml
```

Setelah build berhasil:

```bash
git add -A
git commit -m "Add Mora Core Phase 1 master controller"
git push
```

## Catatan fase

Ini adalah Phase 1: Core + database + registry + policy + event timeline + audit. Connector IPC yang diekspor dan perintah lintas-aplikasi belum diaktifkan; itu sengaja ditunda ke Phase 2 sampai verifikasi caller UID, package dan sertifikat penanda tangan selesai diuji. Perintah eksternal default-nya ditolak.

Patch ini menaikkan versi Mora Shield dari 3.4.0 (versionCode 11) ke 3.5.0 (versionCode 12). Jika project sudah memiliki versi lebih tinggi, sesuaikan kenaikan versionCode secara manual sebelum build.
