# Mora Identity Studio — Phase 1–10 implementation status

This matrix distinguishes code that is present from platform coverage that still needs validation. A profile label is not a physical device emulator.

| Phase | Current state | Implemented now | Remaining verification or gap |
|---|---|---|---|
| 1 — Identity Engine | Implemented for app-scoped browser compatibility | Read-only Android device snapshot; separate local browser profiles; select/reset/favorites/custom profiles | No global device identity rewrite; external browsers are not silently modified |
| 2 — Profile Database | Expanded curated catalogue | Android OEMs and legacy models; Pixel 11 / Pixel 11 Pro / Pixel 11 Pro XL, Galaxy S26 series; iPhone 18 Pro/Pro Max; iPhone/iPad generations; Windows PC/laptop; macOS hardware generations; Linux distro compatibility templates; search/filter in Android UI | Still not exhaustive; newest presets are browser compatibility fixtures, not verified physical-device emulation; some older UA strings are historical samples |
| 3 — Randomizer Engine | Implemented with Android scheduling caveat | Reusable seed; deterministic seed/scope/run selection; one-shot and 6–168 hour WorkManager schedule; optional randomization of enabled per-app non-security labels | Android may defer periodic work; target app processes may need restart; real-device battery/reboot testing required |
| 4 — Browser Identity Engine | Partial | User-Agent selection; supported Chromium User-Agent Metadata/Client Hints; custom profiles; favorites; Android Storage Access Framework file import/export plus schema-1 profile exchange | Client Hints are engine/version dependent; Safari/Firefox hints are not fabricated; desktop helper requires explicit command-line use |
| 5 — Fingerprint Protection | Partial, scoped | Canvas readback/export and OffscreenCanvas conversion guards, WebGL renderer masking, fresh session seed, isolated offline preview, WebRTC/media restrictions | Protections only apply to Mora's preview WebView; not global Android/external browser protections; fonts/audio/GPU timing and some engine-specific paths are not normalized |
| 6 — Android Integration & Recovery | Partial | WebView integration; browser discovery; read-only KSU/Magisk/LSPosed footprint checks; per-app compatibility labels; versioned backup/restore/rollback | External browsers are discovery-only; hook activation and process behavior must be verified with the actual ROM/LSPosed setup |
| 7 — Linux Browser Integration | Functional isolated desktop companion | Firefox, LibreWolf, Zen, Brave, Chrome/Chromium; CLI plus lightweight Tk dashboard; native and common Flatpak browser detection/launch; separate profile directories; per-browser protection capability reporting; doctor, import/export, diagnostics, backup/restore/rollback | Browser-specific toggles are best-effort and version-dependent; Chromium Canvas protection is explicitly unsupported; actual installed-browser testing remains required |
| 8 — Cross-Platform UI | Partial, two native front ends | Android Compose Identity Studio inside Mora Shield with overview metrics, animated profile details and retained navigation state; Android file-based profile/backup transfer through the Storage Access Framework; Linux Tk dashboard in the desktop helper; searchable profile catalogue, protection and recovery controls; explicit portable JSON exchange | No live shared database or real-time sync; Linux dashboard is a lightweight companion and Android remains the main app; no second Android app |
| 9 — Fingerprint Diagnostics | Expanded local surface report | Android/Linux UA, Client Hints when available, Canvas, WebGL, WebRTC API, locale, timezone and screen; Android before/after baseline plus identity-surface report; Linux JSON audit and comparison | Reports inspect browser APIs/metadata, not public IP, DNS or routes; API visibility is not proof of an actual network leak |
| 10 — Build, Testing & Release | CI pipeline present; device validation pending | Android Gradle unit-test/debug APK CI; Linux Python compile/unit tests; protected signed release workflow; file-based profile import/export and full backup/restore controls | Must verify latest CI after each commit and test debug/release APKs on real Android hardware and supported Linux browsers; no physical device farm is attached to this PR |

## Performance and safety boundaries

- The Android Identity Studio stays in the existing Mora Shield application; no second Android app is introduced.
- Randomizer and diagnostic state remain app-private. Empty profile catalogues are handled without crashing a scheduled randomizer; manual randomization refreshes the visible selected profile. Desktop profiles are stored under Mora's own configuration directory and use separate browser data directories.
- The Android preview blocks network loads and external navigation. WebView is destroyed when the preview leaves composition.
- Linux helper uses Python standard library only; Tkinter is loaded only when the GUI command is used. Browser launching remains opt-in and opens only Mora-created profiles.
- Original-device backup stores only read-only public Build labels and is reference metadata; restoring a backup never rewrites Android's live device identity.
- No IMEI, serial, Android ID, MAC address, build fingerprint, security patch, hardware attestation or Play Integrity value is fabricated, modified or bypassed.
- iOS, Windows, macOS and Linux labels are browser compatibility test profiles, not real device/OS emulation.

## Final acceptance checklist

1. Latest Android CI and signed release workflow both complete successfully for the current PR head.
2. Install the APK on a physical Android device and test profile switching, Client Hints support, Canvas/WebGL/WebRTC controls, scheduling, backup/restore and rollback.
3. Test per-app compatibility only on a non-critical app with the intended LSPosed scope and verify process restart behavior.
4. On Linux, run doctor and exercise every browser that is actually installed; confirm normal browser profiles remain unchanged.
5. Test Android ↔ Linux schema-1 profile import/export and compare before/after reports.
6. On Linux, run `python mora_identity.py gui`; verify each installed browser and protection option in its own isolated profile.
