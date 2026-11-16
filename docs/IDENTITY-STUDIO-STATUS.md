# Mora Identity Studio — Phase 1–10 implementation status

This matrix distinguishes code that is present from platform coverage that still needs validation. A profile label is not a physical device emulator.

| Phase | Current state | Implemented now | Remaining verification or gap |
|---|---|---|---|
| 1 — Identity Engine | Implemented for app-scoped browser compatibility | Read-only Android device snapshot; separate local browser profiles; select/reset/favorites/custom profiles | No global device identity rewrite; external browsers are not silently modified |
| 2 — Profile Database | Partial / extensible | Android brand/model compatibility presets; built-in Android/iPhone/iPad/Windows/macOS/Linux browser profiles | Catalogue is curated, not exhaustive for every historic/current device, laptop SKU or browser release; verify model data before adding entries |
| 3 — Randomizer Engine | Implemented with Android scheduling caveat | Reusable seed; deterministic seed/scope/run selection; one-shot and 6–168 hour WorkManager schedule; optional randomization of enabled per-app non-security labels | Android may defer periodic work; target app processes may need restart; real-device battery/reboot testing required |
| 4 — Browser Identity Engine | Partial | User-Agent selection; supported Chromium User-Agent Metadata/Client Hints; custom profiles; favorites; Android/desktop schema-1 import/export | Client Hints are engine/version dependent; Safari/Firefox hints are not fabricated; desktop helper requires explicit command-line use |
| 5 — Fingerprint Protection | Partial, scoped | Canvas and WebGL readback protections, fresh session seed, isolated local preview WebView, WebRTC/media restrictions | Protections only apply to Mora's offline preview WebView; not global Android/external browser protections; surfaces such as fonts/audio/GPU timing are not normalized |
| 6 — Android Integration & Recovery | Partial | WebView integration; browser discovery; read-only KSU/Magisk/LSPosed footprint checks; per-app compatibility labels; versioned backup/restore/rollback | External browsers are discovery-only; hook activation and process behavior must be verified with the actual ROM/LSPosed setup |
| 7 — Linux Browser Integration | Partial but functional CLI bridge | Firefox, LibreWolf, Zen, Brave, Chrome/Chromium isolated profile creation/launch; backups, restore, rollback; doctor; import/export; local diagnostics | No full Linux GUI dashboard inside Mora Shield; binary names and individual browser versions require machine testing |
| 8 — Cross-Platform UI | Partial | Android dashboard inside Mora Shield; Linux companion bridge and portable JSON exchange | No shared live profile database or unified Linux GUI; Android and Linux exchange profiles explicitly through files |
| 9 — Fingerprint Diagnostics | Implemented for local preview / partial for complete browser surface | Android JSON reports, baseline, before/after comparison, clipboard export; Linux diagnostics HTML, download and CLI comparison | Diagnostics cannot prove anonymity or network leak freedom; test actual browser behavior manually |
| 10 — Build, Testing & Release | CI pipeline present; device validation pending | Android Gradle unit-test/debug APK CI; Linux Python compile/unit tests; protected signed release workflow | Must verify latest CI after each commit and test debug/release APKs on real Android hardware and supported Linux browsers; no physical device farm is attached to this PR |

## Performance and safety boundaries

- The Android Identity Studio stays in the existing Mora Shield application; no second Android app is introduced.
- Randomizer and diagnostic state remain app-private. Desktop profiles are stored under Mora's own configuration directory and use separate browser data directories.
- The Android preview blocks network loads and external navigation. WebView is destroyed when the preview leaves composition.
- Linux helper uses Python standard library only. Browser launching remains opt-in and opens only Mora-created profiles.
- No IMEI, serial, Android ID, MAC address, build fingerprint, security patch, hardware attestation or Play Integrity value is fabricated, modified or bypassed.
- iOS, Windows, macOS and Linux labels are browser compatibility test profiles, not real device/OS emulation.

## Final acceptance checklist

1. Latest Android CI and signed release workflow both complete successfully for the current PR head.
2. Install the APK on a physical Android device and test profile switching, Client Hints support, Canvas/WebGL/WebRTC controls, scheduling, backup/restore and rollback.
3. Test per-app compatibility only on a non-critical app with the intended LSPosed scope and verify process restart behavior.
4. On Linux, run doctor and exercise every browser that is actually installed; confirm normal browser profiles remain unchanged.
5. Test Android ↔ Linux schema-1 profile import/export and compare before/after reports.
