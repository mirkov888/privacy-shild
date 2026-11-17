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


## Mora Linux Environment — Android userspace

Mora Shield integrates with Termux through its documented RUN_COMMAND API for confirmed distro installation, XFCE/VNC package setup, start/stop actions, and returned command status. Supported starter profiles are Debian, Ubuntu, and Arch Linux via proot-distro. Termux's RUN_COMMAND permission and allow-external-apps policy must be explicitly enabled by the user. This is a PRoot userspace sharing the Android kernel, not a full VM. See [docs/MORA-LINUX-ENVIRONMENT.md](docs/MORA-LINUX-ENVIRONMENT.md).

## Mora Identity Studio — Phase 1 & 2

The additive Identity Studio introduces a local profile engine and a starter browser compatibility catalogue for Android, iOS/iPadOS, Windows, macOS, and Linux. It preserves the existing Per-App Device Identity feature. See [docs/IDENTITY-STUDIO-PHASE1-2.md](docs/IDENTITY-STUDIO-PHASE1-2.md) for scope and limitations. The selected User-Agent is exposed for opt-in use by Mora-owned WebViews; external browsers and Android system identity are not modified.


## Phase 11 — Rust Native Core Integration

- Mora Shield remains the only main application. The Rust Core is compiled as libmora_rust_android.so and loaded from the existing app process through JNI.
- The JNI-compatible Kotlin object remains in package com.mora.rustandroid, matching the exported JNI symbols from the pinned Rust Core revision.
- The native engine is exposed in the existing Mora Core tab with API/capability negotiation, a six-module smoke test, an app-scoped security audit, and an app-UID network snapshot.
- Existing Room-backed Mora Core, Connector authentication, policies, command approval, and event timeline remain intact; the Rust module is an additional native engine, not a replacement for that control plane.
- CI checks out the pinned Rust Core commit 4613c0bdb3b992e05e62b38287101c07ce8ad512, builds arm64-v8a, armeabi-v7a, and x86_64 with NDK r27c, and packages the resulting native libraries into Mora Shield APKs.
- The security auditor receives only this app's package metadata and requested/granted permission state. Network counters use Android TrafficStats for the Mora Shield UID only. No other app is scanned, no packet payloads are captured, and no permissions are changed automatically.
- Local builds must generate app/src/main/jniLibs from the same pinned Rust Core source before running Gradle. CI performs this step automatically.

### Phase 11 validation
- GitHub Actions must pass Rust native builds, Android unit tests, and APK packaging before the integration is considered CI-verified.
- Install the generated Mora Shield APK and open Mora Core → Rust Native Engine. Confirm API v9 / capability 0x7ff, run all six module checks, then exercise the app-only audit and UID network snapshot buttons.
- Keep on-device verification separate from CI/build verification.
