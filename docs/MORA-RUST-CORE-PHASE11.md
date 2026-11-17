# Mora Shield Phase 11 — Rust Native Core Integration

## Architecture
Mora Shield remains the primary Android application and existing Room-backed MoraCore remains the ecosystem control plane for app registry, authenticated Connector IPC, policy enforcement, user approval, activity timeline, and command audit.

The Rust Core is an additional in-process native engine:
- libmora_rust_android.so is built for arm64-v8a, armeabi-v7a, and x86_64.
- The JNI bridge expects the class com.mora.rustandroid.NativeCore; the Kotlin JNI declaration intentionally retains that package name even though the host application namespace is com.mora.shield.
- com.mora.shield.core.MoraRustEngine adapts the stable JSON dispatcher (API v9 / schema v1 / capability mask 0x7ff) for the host app.
- The Rust integration card is available from the existing Mora Core tab.

## Included host-app operations
- Native library availability and API/capability negotiation.
- In-memory self-checks for Profile Store, System Config, Runtime Manager, Event Logger, Network Monitor, and Security Auditor.
- Persistent module operations for Profile Store, System Config, Runtime Manager, Event Logger, and Network Monitor, using separate app-private JSON state files and atomic file replacement.
- A persistent six-module health check exposed in the Rust Native Engine card; it also writes an app-scoped security report and records current app-UID network counters.
- Security audit of the owning Mora Shield package metadata and its requested/granted permissions, with a report in app-private storage.
- Network counter snapshots for the Mora Shield UID via Android TrafficStats, retained in app-private storage.

## Persistence and trust boundaries
- Existing Mora Core Room database and its connector/session logic are unchanged.
- Rust module state files are stored separately in filesDir and committed atomically after successful native responses; no existing Room tables or connector records are migrated or overwritten.
- Persistent module dispatch is serialized in-process to prevent concurrent read/modify/write loss.
- Network statistics are only for the current app UID. Android may return unavailable counters (-1), which are recorded as JSON null.
- Security audit does not scan other apps, certify the entire device, request or revoke permissions, or bypass Android security.
- The integration does not change package identity, app signing configuration, or release keystore secrets.
- Connector commands remain deny-by-default and continue to require existing policy approval.

## CI build
Both debug CI and the signed release workflow:
1. Check out the host Mora Shield repository.
2. Check out the pinned mora-rust-android source revision 4613c0bdb3b992e05e62b38287101c07ce8ad512.
3. Install Rust Android targets and Android NDK r27c.
4. Build the native library for all three supported ABIs into app/src/main/jniLibs.
5. Run Gradle unit tests, assemble debug APKs, and run Android emulator instrumentation tests for JNI ABI, all native exports, six-module dispatch, and persistent module state.

## Device acceptance
1. Install the generated Mora Shield APK over the existing app (same package and signing key are required).
2. Open Mora Core and confirm Rust Native Engine reports API 9 and capability 0x7ff.
3. Run Tes Integrasi 6 Modul Rust; expected result is 7/7 checks passed.
4. Run Tes Persistensi 6 Modul Rust; expected result is 6/6 modules persisted successfully.
5. Run the app security audit and confirm the report is limited to Mora Shield's own package.
6. Capture at least two app network snapshots with app network activity between them; verify deltas where Android exposes counters.
7. Confirm existing app registry, connector, policy, timeline and other screens still open and function.

A successful CI job is not a substitute for installation and device acceptance testing.
