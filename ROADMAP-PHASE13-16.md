# Mora Shield Roadmap — Phases 13–16

This roadmap defines the location, identity, permission/sensor, and automation phases. Existing App Control features (Freeze/Unfreeze and Hide/Restore launcher icons) are separate pre-existing functionality; they are NOT part of Phase 13 and must remain available independently.

## Phase 13 — Per-App Location Control

### User-facing capabilities
- Real location (disable spoofing and return to the device/provider's real location).
- Static mock coordinates (latitude, longitude, optional altitude and accuracy).
- Random coordinates.
- Random coordinates constrained to a configured radius around a center point.
- Periodic coordinate changes with a configurable interval.
- Saved location presets assignable independently to each app.
- Per-app spoofing enable/disable switch and a clear active-state indicator.

### Technical approach
Android's ordinary mock-location provider is device-wide and is not sufficient for true per-app isolation. The implementation must use a supported per-process interception backend (prefer an optional LSPosed/Xposed companion module with a narrow package scope) or explicitly report that per-app spoofing is unavailable. Do not silently fall back to global device-wide spoofing.
- Keep profiles in Mora Core/Room, keyed by package name.
- Validate latitude (-90..90), longitude (-180..180), radius, accuracy and interval.
- Use a secure random source for random coordinates; generate points uniformly by area inside the radius, not by independently randomizing latitude/longitude.
- Persist configuration across reboot; restore original behavior when a profile is disabled or removed.
- Provide a backend status/compatibility check and an emergency disable-all action.

## Phase 14 — Device Identity Profiles

### User-facing capabilities
- Device model profile.
- Manufacturer and device name.
- Android release and build properties.
- Screen resolution and density profile.
- Different identity profile per app.
- Built-in presets for compatibility and privacy testing.

### Technical approach
- Store immutable, named profiles and per-package assignments.
- Apply identity overrides in the target app process through the same optional per-process hook backend; changing global system properties is not per-app and must not be presented as such.
- Validate property formats and keep a snapshot of original values.
- Avoid changing verified boot, hardware-backed identifiers, serial numbers, IMEI, advertising IDs, attestation or other security credentials.
- Clearly label simulated values and expose backend compatibility before activation.

## Phase 15 — Per-App Permission & Sensor Control

### User-facing capabilities
- Camera and microphone controls.
- Location, contacts, SMS and storage permission shortcuts/controls.
- Clipboard protection.
- Sensor access monitoring.
- Background activity controls.
- Per-app internet access control using a local firewall.

### Technical approach
- Prefer Android PermissionController/AppOps APIs and supported root shell operations; show unsupported or system-restricted operations as unavailable rather than claiming success.
- Do not revoke permissions automatically without explicit user action.
- Clipboard protection should be transparent and scoped; do not read or upload clipboard contents.
- Sensor monitoring should record access metadata only, not capture sensor payloads.
- Internet firewall should use an explicit, consent-based local VPNService or a carefully scoped root firewall backend. Never run both backends simultaneously; provide a visible active-backend indicator and emergency restore.
- Keep allowlists for Mora Shield, system-critical packages and the active VPN/connector components to prevent lockout.

## Phase 16 — App Profiles & Automation

### User-facing capabilities
- Multiple named profiles per app.
- Apply selected rules when an app enters foreground.
- Restore the previous state when it leaves foreground.
- Scheduled profile activation.
- Import/export configuration.
- Audit log of every applied/reverted change.
- One-tap emergency restore.

### Technical approach
- Build a deterministic profile engine with explicit precedence: manual override > scheduled profile > foreground app profile > system defaults.
- Detect foreground transitions using the least-privileged supported mechanism available; explain when Usage Access or an optional companion is required.
- Apply changes transactionally, record original state before each mutation, and roll back partial failures.
- Never silently override a user's manual permission changes. Detect conflicts and ask before applying.
- Import/export versioned JSON with schema validation; reject unknown privileged commands and unsafe package targets.
- Emergency restore must disable automation first, then restore recorded prior states; preserve an exportable recovery log.

## Delivery sequence and acceptance criteria

1. Phase 13 foundation: profile data model, per-app UI, preset CRUD, coordinate validation, backend capability/status, tests. Enable spoofing only after a real per-process backend is available and verified.
2. Phase 14: identity profile model, package assignment UI, backend integration, validation and tests.
3. Phase 15: permission/AppOps controls, sensor metadata, background controls and firewall backend with lockout protection.
4. Phase 16: profile engine, foreground transitions, schedules, import/export, audit trail and emergency restore.

Each phase must have unit tests, CI build/test, release APK artifact verification, documented limitations and a device test checklist. A successful build alone does not prove that privileged behavior works on every ROM. Test on the target Redmi Note 12 Pro 4G / Android 16 before marking device compatibility as verified.

## Safety and integrity requirements

- Per-app means isolated to the selected package/process. Never disguise a global device-wide change as per-app control.
- No hidden persistence, remote telemetry, or automatic upload of profile data.
- Require confirmation for permission revocation, firewall blocking, identity changes and emergency restoration.
- Preserve original values and provide rollback for every privileged mutation.
- Do not alter hardware-backed identifiers, SIM identifiers, credentials, attestation or boot integrity.
