# Mora Connector SDK v1 (source distribution)

This is a small Android SDK made only from Android framework APIs. It has no extra Maven dependency. Copy the Kotlin files from `src/main/java/com/mora/connector/sdk/` into the same package path in the companion APK.

## Required setup

1. Install Mora Shield first.
2. Sign Mora Shield and the companion APK with the same production signing certificate.
3. Add `manifests/AndroidManifest-snippet.xml` to the companion manifest.
4. Copy the SDK Kotlin source package into the companion APK.
5. Create one `MoraConnectorClient` per companion package/process and keep it owned by the long-lived component that represents the app's active work (for MoraDownloader, the download foreground service is the intended owner).
6. Register handlers before connecting, then call `connect()`. The client sends a heartbeat every 30 seconds while registered. Shield issues a random per-session token; every post-registration request includes it, so messages from an old session are rejected after reconnect.
7. Call `close()` when the owning component is being permanently destroyed. Avoid creating separate client instances in both an Activity and a Service: Shield tracks one authenticated session per package.

The connector reports its own status and executes only app-owned capability handlers. It does not expose root, arbitrary shell, raw filesystem access, or secrets to Mora Shield.

## Minimal example

```kotlin
private lateinit var moraConnector: MoraConnectorClient

override fun onCreate() {
    super.onCreate()
    moraConnector = MoraConnectorClient(
        context = this,
        connectorId = "mora-downloader",
        listener = object : MoraConnectorListener {
            override fun onConnected() { /* update local connection indicator */ }
            override fun onConnectionError(message: String) { /* show non-secret status */ }
        }
    )

    moraConnector.registerCommandHandler("download.pause") { command ->
        // Validate payload and invoke the app's existing pause operation.
        // Do not run shell strings received from Mora Shield.
        val success = pauseExistingDownloadEngine()
        MoraConnectorCommandResult(
            requestId = command.requestId,
            commandId = command.commandId,
            success = success,
            summary = if (success) "Download dijeda" else "Tidak dapat menjeda download"
        )
    }
    moraConnector.connect()
}
```

`pauseExistingDownloadEngine()` is an example placeholder: connect it to a real function already implemented by the companion app. Do not ship a handler that always returns success.

## Event example

```kotlin
moraConnector.publishEvent(
    MoraConnectorEvent(
        eventType = "DOWNLOAD_COMPLETED",
        title = "Download selesai",
        detail = "Satu file berhasil diunduh",
        severity = "info"
    )
)
```

`publishEvent()` returns whether the message was queued to Binder, not whether Shield persisted it. Use `onEventAccepted` for the receipt and keep a small app-owned outbox for retry when disconnected or rate-limited. Shield currently accepts at most 120 events per connector per minute. Do not put API hashes, OTPs, passwords, Telegram sessions, private channel URLs, access tokens, or file contents in event details.

## API v1

- `events.publish`: send a sanitized event to the Core timeline.
- `policy.read`: read the companion's effective high-level policy.
- App-owned command IDs (for example `download.pause`, `system.package.refresh`) are advertised by registering a handler before connection.

All commands remain denied in Mora Shield until the user explicitly enables command access, allowlists a capability, and confirms operations whose policy requires approval. A connector must still implement and validate its own handler.
