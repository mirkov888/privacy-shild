# Mora Connector SDK v1 — Phase 2

Phase 2 adds a real, authenticated IPC contract between the existing Mora Shield master APK and its companion APKs. It does not merge the apps or move their engines into Shield.

## Components

- Host: `MoraConnectorService` inside `com.mora.shield`.
- SDK: source-distributed `com.mora.connector.sdk` package. Copy the same SDK source files into MoraDownloader and MoraSystemManager.
- Transport: Android `Messenger` over a bound service.
- Host service permission: `com.mora.shield.permission.CONNECTOR`, protection level `signature`.
- Runtime authentication: Binder Messenger `Message.sendingUid` -> unique package for UID -> registered app -> current APK signer SHA-256 -> registry signer snapshot.

The caller's claimed package name is never used as identity. Shared UIDs, unknown packages, wrong signer, invalid API version, and non-registered apps are rejected.

## API v1 messages

| Message | Direction | Function |
|---|---|---|
| REGISTER | worker -> Shield | Authenticate package/certificate and announce capabilities |
| REGISTERED | Shield -> worker | Handshake result |
| HEARTBEAT | worker -> Shield | Refresh connector liveness |
| PUBLISH_EVENT | worker -> Shield | Add event to central timeline |
| GET_POLICY | worker -> Shield | Read that app's safe policy projection |
| COMMAND | Shield -> worker | Invoke one app-owned capability handler |
| COMMAND_RESULT | worker -> Shield | Complete an in-flight command audit row |
| DISCONNECT | worker -> Shield | End connector session |

## Trust and policy model

- A signature permission protects the service bind operation.
- The service checks the Binder-provided sender UID on every message.
- The server requires exactly one package for that UID and a matching signer digest recorded in Core's app registry.
- `events.publish` and `policy.read` must be declared at registration.
- Event source package is overwritten with the authenticated package name.
- Event type, package name, timestamp, payload size, and text length are bounded; each connector is limited to 120 events per rolling minute.
- The Core sanitizer removes URLs and common credential/token patterns before persistence.
- Remote commands are denied unless command access is enabled, the capability is allowlisted, the connector declared the capability, and required user approval was supplied.
- Remote command payloads are limited to 4 KB. Each command has a 30-second timeout and an audit row.
- The service contains no shell executor. A worker handles only registered, app-owned commands.

## Signing requirement

All production Mora APKs must be signed with the same controlled signing certificate. Configure one keystore in CI secrets and use it for all three workflows. The repository's existing debug signing setup is not an ecosystem signing strategy. Install Mora Shield before installing/updating companion apps that declare the custom connector permission. Keep one `MoraConnectorClient` instance per companion package/process; for Downloader, let its foreground download service own the client instead of creating separate instances in both Activity and Service.

Do not commit keystore files or passwords.

## Install in Mora Shield

Apply Phase 1 first, then run:

```bash
python3 apply-mora-connector-phase2.py ~/privacy-shild
cd ~/privacy-shild
./gradlew assembleRelease --no-daemon
```

The installer is safe to rerun and expects the Phase 1 database, registry, policy and timeline sources to exist.

## Add the SDK to a companion project

Copy `sdk/src/main/java/com/mora/connector/sdk/*.kt` into:

`app/src/main/java/com/mora/connector/sdk/`

Add the contents of `sdk/manifests/AndroidManifest-snippet.xml` to the companion app manifest. Use one `MoraConnectorClient` for the life of the active component/service, register real command handlers before `connect()`, publish events from the app's existing business logic, and close the client when no longer needed.

## Scope boundary

This phase provides the host IPC service and the companion SDK. It does not yet modify MoraDownloader's Telethon engine or MoraSystemManager's root/Shizuku operations. Those are Phase 3 and Phase 4 integrations. Until a connector is installed and signed correctly, Shield should show the app as INSTALLED rather than CONNECTED.


## Session protection

After registration, Shield issues a random per-session token. The SDK includes it on every request after registration; stale sessions cannot publish events, submit command results, or disconnect a newer session. Remote commands also carry the session token and are accepted only by the matching active client session.
