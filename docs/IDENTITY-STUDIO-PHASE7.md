# Mora Identity Studio — Phase 7: Identity Privacy Control

## Added

- Read-only scan of installed packages that declare phone-state, phone-number, SMS, call-log, or contacts permissions.
- Shows Android's recorded permission grant flag for each matching app and offers a shortcut to that app's system settings. The user must review and change permissions manually.
- Checks whether the current Mora app can read its own Android ID, but never returns, displays, copies, logs, or backs up the raw value.
- Explains Android ID's app-signing-key, Android-user, and device scope.
- Documents that IMEI and serial are not queried or stored by this feature.
- Documents hardware attestation and Play Integrity as unmodified trust signals; no spoofing or fabricated verdict is attempted.

## Important limits

- The report reads package permission declarations and the system's granted-permission flags. It does not prove that an app accessed, transmitted, or retained data.
- A granted READ_PHONE_STATE permission does not imply access to IMEI or serial on modern Android. Those identifiers remain restricted by platform and privileged-permission rules.
- Permission controls are opened in Android Settings; Mora does not silently revoke permissions or alter another app.
- The audit includes Mora Shield itself, so its own declared permissions are visible and its Android permission settings can be opened.
- Android ID availability is checked only for Mora's own application context. It is not a universal view of every app's Android ID.
- No identifier randomization, IMEI/serial spoofing, hardware attestation change, or Play Integrity bypass is implemented.
- Advertising ID management is not part of this phase; use the operating system / Google settings.

## Validation

Unit tests cover tracked-permission filtering, granted-state handling, and missing permission metadata.

## Linux dashboard and profile controls

The desktop helper now includes a lightweight Tkinter dashboard:

```bash
python mora_identity.py gui
```

The dashboard can create, search via the profile list, launch, backup, rollback, import/export JSON profiles, inspect browser support, and audit a saved diagnostic report. It launches only Mora-created isolated profile directories. The GUI loads Tkinter lazily; CLI use does not require a graphical session.

Optional controls:
- Firefox Canvas reduction: `privacy.resistFingerprinting`.
- Firefox WebGL reduction: disable WebGL.
- Chromium WebGL reduction: `--disable-webgl`; Canvas protection is marked unsupported by this launcher.
- WebRTC restriction remains browser-specific and best-effort, not a VPN.

This dashboard is a Linux companion interface. Android remains the primary Mora Shield app; profiles are exchanged through explicit JSON import/export rather than live sync.
