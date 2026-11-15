# Mora Shield — Phase 15 implementation

Phase 15 adds an app-level control screen reachable from **Lainnya → Per-App Permission & Sensor Control**.

## Implemented in the app

- Lists third-party applications and exposes only permissions requested by the selected package.
- Supports explicit, confirmed grant/revoke attempts for camera, microphone, location, contacts, SMS and supported media/storage runtime permissions.
- Records the pre-change permission state and AppOps mode locally in private app preferences for per-package restore.
- Clipboard restriction uses the `READ_CLIPBOARD` AppOp only when the ROM exposes it. Mora Shield never reads clipboard text or content.
- Background restriction uses `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` AppOps. Android/ROM policy can override or reject these operations.
- Sensor activity view reads AppOps metadata only; it does not capture camera, microphone, location or other sensor payloads.
- Per-app network mode reuses the existing Mora Privacy Shield module `fwset` backend (free, Wi-Fi-only, mobile-only, or all-network block).
- Emergency Restore replays the saved original permission/AppOps/firewall values for the selected package.
- System packages, Mora Shield, VPN service packages and registered connector service packages are excluded from mutation targets.

## Security boundaries

- Root/ROM support is required for permission mutation, AppOps changes and module firewall commands. The UI reports command failures instead of pretending a change succeeded.
- Some permissions are system-restricted, special permissions or non-revocable on particular Android releases. Such operations may fail and must be verified in Android Settings.
- `READ_CLIPBOARD` is not exposed on every Android build. An unsupported AppOp remains unavailable; Android's built-in clipboard privacy behavior remains in effect.
- AppOps background controls are not a guarantee that every OEM background scheduler will stop an app.
- Firewall is enforced by the installed Mora Privacy Shield module. This app screen does not start a second VPN firewall.
- No clipboard contents or sensor payloads are collected, persisted, or uploaded.
- The app-level UI is per-package. No global permission/sensor state is represented as per-app state.

## Validation checklist

1. Run `./gradlew testDebugUnitTest --no-daemon`; Phase 15 allowlist tests must pass with existing tests.
2. Build signed release APK with the repository's persistent GitHub signing secrets.
3. On the target Redmi Note 12 Pro 4G / Android 16, verify grant/revoke for a disposable third-party test app.
4. Confirm unsupported AppOps show as unavailable and no success message is shown on command failure.
5. Verify clipboard restriction without reading clipboard contents.
6. Verify background restrictions and restore behavior after force-stop/reboot.
7. Verify all firewall modes, then Emergency Restore; confirm Mora Shield, system UI, settings, VPN and connector services remain reachable.
8. Confirm the saved original values are restored after app process restart and device reboot.

A green CI build does not establish OEM/ROM enforcement. Device compatibility remains unverified until the checklist above is completed on the target phone.
