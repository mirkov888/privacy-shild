# Validation status

- Source basis: public Mora Shield `ScreenSettings.kt`, `MoraDownloaderControlCard.kt`, `AppPolicy`, `MoraCore.updateAppPolicy`; public MoraSystemManager package `com.mora.systemmanager`.
- Patch behavior: adds one policy-only Compose card and inserts it into Settings after the existing monitor card; also places the existing Downloader control card there.
- Not verified in this environment: Gradle compilation, actual installed APK signing identity, Binder connector session, live policy refresh on device, and enforcement of network/background policy by workers.
- Important: network/background settings are stored as central policy metadata in this phase, not enforced by Android automatically.
