# Mora Identity Studio — Phases 8–10

## Phase 8 — Cross-platform UI and integration boundary
- Keep Identity Studio inside Mora Shield; do not create a second Android application.
- Android remains the control plane for Android-local profiles, WebView preview, backup/restore and capability checks.
- The desktop-identity companion provides Linux browser profile lifecycle for Firefox, LibreWolf, Zen, Brave, Chrome and Chromium.
- Shared JSON metadata includes id, browser, platform_test_label, user_agent and scope for future import/export compatibility.
- External browser profiles are never silently injected into or modified.

## Phase 9 — Fingerprint diagnostics
The local diagnostics page reports User-Agent, Client Hints when exposed, navigator platform, locale, timezone, screen, WebGL availability/vendor/renderer when the debug extension is exposed, Canvas API availability and WebRTC API presence. Reports remain local. Compare two downloaded JSON reports for before/after review. No diagnostic result proves anonymity or physical-device identity.

## Phase 10 — Build, tests and release validation
- Android CI continues Gradle unit tests and debug APK assembly.
- A Linux CI job compiles the helper and runs Python standard-library tests.
- Release signing remains in the existing protected workflow.
- Real-device Android WebView and actual Firefox/Chromium compatibility still require manual verification.

## Limitations
- User-Agent overrides do not emulate an OS/device and may not synchronize every Client Hint.
- WebGL/Canvas behavior depends on browser and version.
- WebRTC controls are best-effort, not a VPN or leak-proof guarantee.
- No IMEI, serial, Android ID, MAC, build fingerprint, hardware attestation or Play Integrity values are changed.
