# Mora Connector SDK v1 — Phase 2 Patch

Patch for the existing `mirkov888/privacy-shild` project after Mora Core Phase 1 has been installed.

## What is included

- Authenticated Binder/Messenger host service in Mora Shield.
- Signature-level custom permission protecting the exported service.
- Caller UID -> unique package -> signer SHA-256 verification.
- Registration, heartbeat, event publish, policy read, command delivery, command result and disconnect messages.
- Per-session random token to reject stale Binder sessions after reconnect.
- Remote command authorization using Core policy, advertised capabilities, approval gate and command audit.
- Companion-side Kotlin SDK source package for MoraDownloader and MoraSystemManager.
- Idempotent installer, API documentation, signing notes and static tests.

## Install the Shield host side

Extract the ZIP. Then run:

```bash
python3 apply-mora-connector-phase2.py ~/privacy-shild
cd ~/privacy-shild
./gradlew assembleRelease --no-daemon
```

The installer expects Phase 1 files and does not create another APK. It upgrades the Shield project from 3.5.0/versionCode 12 to 3.6.0/versionCode 13 when those exact values are present.

## Prepare companion APKs

Copy these Kotlin files from `sdk/src/main/java/com/mora/connector/sdk/` into the same package path in each worker project:

- `MoraConnectorContract.kt`
- `MoraConnectorModels.kt`
- `MoraConnectorClient.kt`

Add the manifest snippet from `sdk/manifests/AndroidManifest-snippet.xml`. Sign all APKs with the same controlled production signing key. Then integrate a single `MoraConnectorClient` into the long-lived owner component of each application.

The SDK currently supplies transport and lifecycle only. It does not pretend that downloader or system-manager operations are implemented; those app-specific handlers arrive in the next phases.

## Session hardening

API v1 uses a random per-session token issued by Shield. Every worker request after registration carries that token; commands sent from Shield also carry it. This prevents a stale client instance from publishing events, sending command results, or disconnecting a newer session. Core command policy fails closed when the installed signer digest is missing.

## Tests and limitations

Run static/installer tests with:

```bash
python3 -m unittest discover -s tests -v
```

See `VALIDATION.md` for passed checks and items that still require a real Android build/device test.
