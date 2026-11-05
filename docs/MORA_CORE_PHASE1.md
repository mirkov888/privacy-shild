
## Mora Core Phase 1

Mora Shield 3.5.0 becomes the local master controller for the Mora ecosystem. Mora Core is embedded in the existing `com.mora.shield` APK; it is not a separate application.

### Included in this phase

- Room database (`mora_core.db`) with app registry, app policies, activity timeline and command audit.
- App registry seeded for Mora Shield, MoraDownloader (`com.mora.nsfwdownloader`) and MoraSystemManager (`com.mora.systemmanager`).
- SHA-256 signer fingerprint capture for installed companion APKs. The upcoming Connector IPC handshake must present the exact installed signer fingerprint; an empty or mismatched fingerprint is rejected.
- Event hub that persists Shield module events and trims the timeline to the newest 5,000 rows.
- Local policy engine with monitoring enabled by default, command access disabled by default, explicit capability allowlists and approval requirements.
- Command router with no shell executor. It only dispatches app-owned handlers registered for a specific package and capability, after authentication and policy checks.
- Bounded audit records for denied, pending, running, successful and failed command requests.
- Mora Core card in Tools showing real database counts, app registry and activity timeline.
- Sensitive key/value fields and URLs are redacted before event details are persisted.

### Safety defaults

- Companion apps are shown as `INSTALLED`, not `CONNECTED`, until the authenticated Connector SDK handshake exists.
- `commandAccessEnabled` defaults to `false` and `allowedCommands` defaults to empty.
- No external connector can submit arbitrary shell commands through Mora Core.
- High-impact operations should be exposed as specific app-owned capabilities and receive a user confirmation in Mora Shield.
- App credentials, Telegram session files, OTPs, passwords and API hashes are not copied into the Core database.

### Build

```bash
chmod +x ./gradlew
./gradlew assembleRelease --no-daemon
```

This patch uses Room 2.6.1 with Kotlin Kapt and Kotlin Coroutines 1.9.0. The database schema is version 1. No destructive migration is configured. Future schema changes must add explicit Room migrations.

### Not yet included

The exported Messenger IPC service, companion-side client SDK and actual remote commands are deliberately deferred to Phase 2. This prevents shipping an externally reachable service before caller UID, package name and signing certificate verification are implemented and tested end-to-end.
