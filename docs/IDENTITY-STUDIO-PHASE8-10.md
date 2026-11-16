# Mora Identity Studio — Phases 8–10

## Phase 8 — Mora Shield integration boundary
- Identity Studio remains in the existing Mora Shield Android application; there is no second Android Identity app.
- The Android screen hosts device information, profile catalogue, WebView privacy switches, randomizer, privacy audit, diagnostic controls and recovery.
- Profile JSON and full Identity Studio backups can be imported/exported with Android's Storage Access Framework; backups include a read-only original-device baseline, and restore is validated and requires explicit confirmation before changing profiles or settings. Import does not overwrite the receiving device's local baseline or live Android identity.
- Linux uses the desktop-identity helper with CLI and a lightweight Tkinter dashboard for isolated browser profiles and protection capability reporting. A manual HTTPS LAN sync session now transfers custom profiles bidirectionally between Android and Linux; it uses a short-lived token plus certificate pin, binds to one selected private IPv4 interface, and Android validates the complete payload (schema, IDs, platforms and User-Agent) before import. It does not synchronize other settings or run continuously.
- Supported Linux browser targets: Firefox, LibreWolf, Zen, Brave, Chrome and Chromium.
- Existing user browser profiles are never silently changed. Per-browser profiles live under Mora's own configuration directory.

## Phase 9 — Fingerprint diagnostics
- Android local preview report: User-Agent, Client Hints when available, platform, locale/languages, timezone, viewport/screen, Canvas hash, WebGL vendor/renderer availability and WebRTC/media API availability.
- Android preview can save a local baseline, capture a later report, compare supported fields and copy the JSON report.
- Android exposes a readable identity-surface report for UA/Client Hints consistency, Canvas, WebGL, WebRTC API, locale/timezone and screen. Linux diagnostics page reports analogous browser surfaces and offers JSON download; CLI audit flags surface mismatches and compare reports field-level changes.
- Reports are local only; no diagnostics are uploaded by these components. Results do not prove anonymity, physical-device authenticity or a leak-free network path.

## Phase 10 — Build, tests and release
- Android CI runs Gradle unit tests and assembles debug APKs for Mora Shield and the companion hook.
- Linux CI runs Python syntax compilation and standard-library unit tests for profile isolation, portable exchange, HTTPS LAN sync authorization/idempotency/conflict handling, backup/restore, rollback, browser protection configuration and diagnostic auditing.
- The protected release workflow validates the persistent signing secrets, builds release APKs and uploads artifacts when its required secrets are available.
- Android unit tests cover profile catalogues, randomization selection, diagnostic comparison, backup validation and recovery validation.
- Automated CI cannot replace testing on real Android hardware/ROMs or each installed Linux browser build.

## Manual device/browser verification still required
1. Android: install the generated debug APK on a supported phone, open Identity Studio, switch profiles and verify the preview's User-Agent and Client Hints support status.
2. Android: export/import a custom-profile JSON file and a full backup through the document picker; confirm invalid/oversized files are rejected and rollback works after a restore.
3. Android: toggle Canvas/WebGL/WebRTC protection and compare baseline/after reports; test both supported and unsupported WebView feature paths.
4. Android: enable a per-app compatibility profile only for a non-critical test app, verify LSPosed scope in its own manager, force-stop/reopen the app and test rollback.
5. Android: enable scheduled randomization, verify WorkManager state, test reboot/battery optimization behavior, restore backup and exercise rollback.
6. Linux: run `python mora_identity.py gui` or `doctor`, create and launch isolated profiles separately in Firefox, LibreWolf, Zen, Brave, Chrome and Chromium where installed; verify the normal browser profile is unchanged.
7. Linux: export/import the schema-1 profile exchange, backup, mutate a test profile, rollback it and compare before/after diagnostic JSON.
8. Android ↔ Linux: start the 15-minute LAN sync server on a trusted Wi-Fi network, compare the certificate pin, perform two-way sync, repeat to confirm idempotency, then stop the server.
9. HyperOS: run `bash scripts/mora-hyperos-acceptance.sh` with USB debugging enabled, then complete the on-device WebView, WorkManager, battery, backup/restore and LSPosed checklist in `docs/IDENTITY-STUDIO-HYPEROS-ACCEPTANCE.md`.

## Limitations and safety
- User-Agent presets are compatibility labels, not complete OS/device emulation. Client Hints depend on browser engine/version and feature support.
- Android fingerprint protection applies only to Mora's own offline preview WebView. External Android browsers remain discovery-only.
- Linux browser controls use dedicated isolated profiles; Firefox preferences and Chromium extensions/startup flags are best-effort and version-dependent. Chromium Canvas protection blocks common page APIs, not worker contexts or every fingerprint surface.
- WorkManager schedules are inexact and may be deferred by Android background/battery policies.
- WebRTC restrictions are best-effort browser controls, not a VPN or leak-proof guarantee.
- No IMEI, serial, Android ID, MAC, build fingerprint, security patch, hardware attestation or Play Integrity values are changed, fabricated or bypassed.
