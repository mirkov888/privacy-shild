# Mora Shield build repair

## Changes
- Added the shared `sdk/src/main/java` source directory to the app module so the host service can compile against `com.mora.connector.sdk.MoraConnectorContract`.
- Added the Kotlin KAPT plugin and Room runtime, KTX, and compiler dependencies required by `CoreDatabase` and `MoraCore`.
- Removed the obsolete duplicate `MoraEvent` / `MoraSeverity` declarations from `core/ConnectorContracts.kt`; the canonical event model is now `core/CoreModels.kt` using `EventSeverity`.

## Validation
- Static check: every `MoraConnectorContract` symbol used by `MoraConnectorService` exists in the SDK contract.
- Static check: exactly one `MoraEvent` declaration remains in app source.
- Gradle build could not be completed in this environment because the Gradle wrapper distribution could not be downloaded (`UnknownHostException: services.gradle.org`). Run `./gradlew assembleRelease --no-daemon` in GitHub Actions or a network-enabled Android build environment.

## Important
This is a source-level repair, not a claim that the release APK has built or that Binder IPC has been tested on-device.
