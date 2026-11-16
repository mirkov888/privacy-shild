# Identity Studio — Phase 6: Android Integration & Recovery

## Implemented

- Android integration diagnostics report the current system WebView provider/version and relevant AndroidX WebKit feature support.
- Installed HTTP(S) browser handlers are enumerated for discovery. External browsers remain read-only integration targets; Mora does not rewrite their settings, inject hooks into them, or claim that a selected Mora profile is applied outside Mora's own WebView.
- A shortcut opens Android's WebView provider settings when the device exposes that settings activity.
- Root diagnostics run only after the user presses Scan. They use read-only `su` checks for root UID and known Magisk, KernelSU, and LSPosed filesystem/command footprints. A detected LSPosed footprint does not prove that the module is enabled or attached to an app process.
- A new WebRTC privacy option is scoped to Mora's own preview WebView. It disables WebRTC peer connection and browser media-enumeration/getUserMedia entry points in the preview, and WebChromeClient denies camera/microphone capture permission requests.
- Per-app compatibility profiles now keep a one-step local rollback snapshot before each save. Emergency Restore uses that snapshot when available and falls back to disabling the selected profile.
- A versioned JSON backup covers custom browser profiles/favorites, selected profile, Identity Studio protection settings, and non-security per-app compatibility labels.
- Backup validation occurs before mutation. Restore automatically creates a rollback point; rollback swaps the last two states so an accidental restoration can be reversed.
- Backup omits build fingerprint, Android release/SDK, security patch, IMEI, serial number, Android ID, MAC address, hardware attestation and Play Integrity data.

## Supported scope

- Mora's own Android WebView is the supported browser integration surface.
- External browser discovery is informational only. Chrome, Firefox, Brave, Samsung Internet and other external browsers are not modified by this phase.
- KernelSU/Magisk checks do not install, update, enable or patch root managers/modules.
- LSPosed detection is a best-effort footprint check; verify module activation and app scope in the LSPosed Manager itself.

## Validation

- Unit tests cover backup schema validation, unsupported schema rejection, unsafe/system package rejection, invalid User-Agent and invalid protection values.
- GitHub Actions runs unit tests and builds both debug APKs for Mora Shield and the companion hook.
