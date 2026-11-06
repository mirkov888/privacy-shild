# Validation status

- Source basis: public Mora Shield `ScreenSettings.kt`, `MoraDownloaderControlCard.kt`, `AppPolicy`, `MoraCore.updateAppPolicy`; public MoraSystemManager package `com.mora.systemmanager`.
- Patch behavior: adds one policy-only Compose card and inserts it into Settings after the existing monitor card; also places the existing Downloader control card there.
- Not verified in this environment: Gradle compilation, actual installed APK signing identity, Binder connector session, live policy refresh on device, and enforcement of network/background policy by workers.
- Important: network/background settings are stored as central policy metadata in this phase, not enforced by Android automatically.


## Phase 11 validation

- Python source/contract tests: 16 passed, 1 existing fixture-dependent test skipped.
- Android manifest and widget XML: parsed successfully.
- `EncryptedAuditExport.kt`: standalone Kotlin compilation passed.
- Gradle unit tests and Android release compilation: not run because the environment cannot resolve `services.gradle.org` to download Gradle 8.14.3.
- Device tests for permissions, cell/Wi-Fi signals, widget refresh, root hide/restore, encrypted export, and persistent signing remain required.
- AppOps per-permission last-access history and actual automatic permission revocation remain intentionally incomplete; unused-app review is dry-run only.
