# Mora Shield Phase 3 validation

## Passed

- Installer fixture test confirms host-side wake manager and control card are copied.
- Installer is idempotent: no duplicate lifecycle hooks or UI cards on a second run.
- Gradle version bump is exactly 3.6.1 / versionCode 14.
- The command card uses `MoraCore.updateAppPolicy` and the host's authenticated `MoraConnectorService.dispatchCommand` API.

## Not verified

- Full Android Gradle build and device Binder testing were not run in this environment.
- A real end-to-end command requires same-signed Shield and Downloader APKs installed on the device, with the worker wake service patch installed.
