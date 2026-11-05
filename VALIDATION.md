# Phase 2 validation report

Date: 2026-11-05

## Passed

- 9 Python static/installer tests passed.
- Command policy rejects a missing signer digest (fail-closed).
- Shared API message IDs are unique.
- Every `MoraConnectorContract.KEY_*` referenced by host service and SDK exists in the contract.
- Host authenticates the Messenger-provided `Message.sendingUid`, resolves a unique package for that UID, and computes a SHA-256 signer fingerprint.
- Connector transport/SDK source has no shell executor or `su` command path.
- Host service and SDK Kotlin compile against local Android/Coroutines API stubs (type/syntax check only); this is not a substitute for Android Gradle compilation.
- Remote commands require an advertised capability, Core allowlist/policy approval, and a matching pending audit record.
- A random session token is issued at registration and checked on every post-registration client message; remote commands also carry the active token.
- Registry refresh preserves another companion's current CONNECTED state.
- Phase 2 installer ran twice on a Phase 1 project fixture without duplicating manifest service/permission entries or changing files on the second run.
- Resulting AndroidManifest.xml parsed successfully as XML.

## Not verified in this environment

- Full Android Gradle compilation and APK assembly were not run: no Android SDK/platform `android.jar` or Gradle executable is available in this environment.
- Binder behavior, signature permission grant, and cross-process handshake still need end-to-end testing on Android.
- The companion apps have not yet integrated the SDK into their real application services. That is Phase 3 (MoraDownloader) and Phase 4 (MoraSystemManager).

## Important deployment precondition

The production Mora Shield, MoraDownloader, and MoraSystemManager APKs must use the same controlled signing certificate. Do not use unrelated per-project debug keys. Install Mora Shield before the companion packages so the custom signature permission is defined when the worker APKs are installed.
