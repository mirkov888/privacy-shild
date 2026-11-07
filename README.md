# Mora Shield — Phase 3 Integrated Patch (Full)

Patch untuk repository `mirkov888/privacy-shild`; tidak membuat aplikasi kedua dan tidak mengubah signing key.

## Fitur
1. Tab khusus Mora Core di navigasi utama.
2. Registry Mora Shield, MoraDownloader, MoraSystemManager.
3. Status `INTERNAL`, `NOT_INSTALLED`, `INSTALLED`, `CONNECTED`.
4. Ringkasan Core, timeline aktivitas, status konektor dan command audit.
5. Kebijakan per aplikasi: master enable, monitoring, remote command access, capability allowlist.
6. Kontrol Downloader yang sudah ada dan policy UI SystemManager yang sudah ada.
7. Screen Time harian: screen-on dan standby.
8. Terminal lokal dengan mode shell atau root manual.
9. Roadmap update app dan modul.

## Batas keamanan
- Connector command access nonaktif secara default.
- Hanya capability yang diiklankan connector yang dapat di-allowlist.
- Semua perintah connector wajib meminta persetujuan pengguna.
- Tidak ada arbitrary shell command melalui Connector IPC.
- Terminal lokal bukan remote terminal; mode root tetap meminta izin root manager.
- Network/background policy tidak diklaim sebagai OS-level enforcement sebelum worker mengimplementasikan handler.
- Screen Time menghitung total layar aktif/standby, bukan per-app usage.

## Instalasi
Ekstrak ZIP lalu jalankan:

```bash
python3 apply_mora_shield_phase3.py ~/privacy-shild
cd ~/privacy-shild
git diff --check
git diff
bash ./gradlew :app:assembleDebug --no-daemon
```

Installer membuat backup `.phase3.bak` untuk file yang dimodifikasi. Jika file tujuan sudah ada dengan isi berbeda atau source tidak cocok, installer berhenti agar tidak menimpa diam-diam.

Jika build sukses:

```bash
git add app/src/main/java/com/mora/shield docs/MORA_SHIELD_APP_MODULE_ROADMAP.md README.md
git commit -m "Integrate Mora Core Phase 3 control center"
git push
```

## Urutan uji
1. Build dan install Shield terlebih dahulu.
2. Pastikan semua APK companion memakai signing certificate release yang sama.
3. Install dan buka Downloader serta SystemManager.
4. Buka tab Mora Core; pastikan tiga paket muncul.
5. Uji `CONNECTED` setelah connector register dan heartbeat.
6. Force-stop companion; status harus kembali ke `INSTALLED` setelah sesi ditutup/timeout.
7. Uji event timeline, policy deny-by-default, allowlist, approval dan audit.
8. Uji screen time dengan layar ON/OFF.
9. Uji terminal non-root dahulu; aktifkan root hanya untuk perintah yang dipahami.

**Validasi:** installer lolos syntax check dan smoke-test fixture dua kali (termasuk uji idempotensi). Build Gradle dan pengujian perangkat belum dijalankan; APK belum dinyatakan terverifikasi.


## Mora Shield Phase 3 Integrated

- Dedicated Mora Core tab with the three-app registry and exact connection states.
- Per-app policy master switch, activity monitoring, connector command access and capability allowlist.
- Central activity timeline, connector overview and command audit.
- Existing Downloader controls and SystemManager policy UI.
- Daily screen-on/standby tracking and local terminal in Tools.
- See `docs/MORA_SHIELD_APP_MODULE_ROADMAP.md` for future app and module updates.

Connector commands remain deny-by-default and require user approval. Terminal is local to Shield, not exposed through Connector IPC. Network/background policies are not OS-enforced unless the worker implements the corresponding handler.
