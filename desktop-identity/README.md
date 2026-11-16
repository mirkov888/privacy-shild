# Mora Shield Desktop Identity Bridge

Linux companion utility for Mora Shield with a CLI and a lightweight Tkinter dashboard. It manages isolated browser compatibility profiles and never edits the user's normal browser profile, OS identity, hardware identifier, certificate or attestation.

Supported Linux browser targets: Firefox, LibreWolf, Zen, Brave, Chrome and Chromium. Firefox-family profiles use dedicated profile directories and user.js; Chromium-family profiles use a separate user-data directory and launcher. Native packages and common Flatpak app IDs are detected; Flatpak launches receive access only to the selected Mora profile directory.

Compatibility labels: Android, iPhone, iPad, Windows, macOS and Linux. Labels select a User-Agent test string only. They do not emulate operating systems, hardware, fonts, codecs, GPU, Client Hints, browser engines or mobile touch behavior. An iPhone/Safari User-Agent opened in Firefox remains Firefox.

## Commands

Start the lightweight Linux dashboard (Tkinter):

    python3 mora_identity.py gui

The dashboard contains profile search/create, browser launch and local diagnostics, protection capability status, backup/restore/rollback, JSON import/export, report comparison and audit. It uses the same profile store and helper functions as the CLI. On Debian/Ubuntu, install the optional UI runtime with `sudo apt install python3-tk` when Tkinter is missing.

    python3 mora_identity.py doctor
    python3 mora_identity.py list
    python3 mora_identity.py create --id android-test --browser firefox --platform android
    python3 mora_identity.py create --id iphone-test --browser zen --platform iphone
    python3 mora_identity.py create --id win-test --browser brave --platform windows
    python3 mora_identity.py launch --id android-test
    python3 mora_identity.py backup --id android-test --output ~/mora-android-test.zip
    python3 mora_identity.py rollback --id android-test
    python3 mora_identity.py export --output ~/mora-identity-profiles.json
    python3 mora_identity.py import --input ~/mora-identity-profiles.json
    python3 mora_identity.py compare --before ~/before.json --after ~/after.json
    python3 mora_identity.py sync-serve --minutes 15

Default storage: ~/.config/mora-shield/identity-profiles. Supply --root PATH before the subcommand for portable storage or tests.

## Profile isolation and recovery

- New profiles use dedicated directories. A matching existing ID is never overwritten.
- backup creates an archive and stores a rollback snapshot under <root>/.rollback/.
- rollback restores that last explicit backup. The current profile is first preserved as <id>.pre-rollback/ and <id>.pre-rollback.zip; nothing is silently discarded. Run backup before making changes you may need to undo.
- restore validates archive paths, symbolic links, size and the profile manifest before restoring to a new ID. Existing destinations are refused.
- export and import use the same schema-1 profile fields as Mora Android's custom-profile exchange: schema, profiles, favorites, id, platform, model and userAgent. Imports are validated and do not overwrite existing profile IDs.
- compare compares two local diagnostic JSON files; reports are not uploaded.

Every profile contains diagnostics.html. Open it locally to inspect User-Agent, Client Hints when available, platform, locale, timezone, screen, WebGL, Canvas output and WebRTC API availability. WebRTC restrictions are best-effort browser preferences, not a VPN or leak-proof guarantee.

## Scope limits

- Browser detection via doctor checks known native executable names and common Flatpak app IDs. Unusual custom install paths or renamed Flatpak IDs may still require manual setup.
- User-Agent overrides do not provide true device emulation or guarantee consistent Client Hints across browsers.
- Android external browsers are not modified by this helper.
- LAN sync is user-initiated, profile-only and session-based, not an always-on shared database. It synchronizes user-created browser profiles; it does not sync Android built-in presets, randomizer schedules, app settings or browser data directories.
- The Linux dashboard is an optional Tkinter front-end to this existing companion bridge; it is not a second Android app.
- No IMEI, serial, Android ID, MAC, build fingerprint, security attestation or Play Integrity values are read or changed.

## Optional browser protection controls

Protection switches are opt-in because disabling browser APIs can break websites.

```bash
python mora_identity.py create --id firefox-private --browser firefox --platform linux --protect-canvas --protect-webgl
python mora_identity.py create --id chrome-private --browser chrome --platform windows --protect-webgl
python mora_identity.py launch --id firefox-private --diagnostics
python mora_identity.py audit --input mora-diagnostic.json
```

- Firefox Canvas reduction uses `privacy.resistFingerprinting`; Firefox WebGL protection disables WebGL and can break 3D content.
- Chromium Canvas protection uses a Mora Manifest V3 extension loaded only in the isolated profile. It blocks common page-context Canvas readback methods (`getImageData`, `toDataURL`, `toBlob`, `OffscreenCanvas.convertToBlob`). Worker contexts and browser internals are not covered; websites using Canvas APIs may break. Chromium WebGL protection disables WebGL.
- Firefox WebRTC protection disables peer-connection APIs. Chromium uses a non-proxied UDP restriction, which is best-effort and is not a VPN.
- The audit reports visible browser APIs and User-Agent / Client Hints inconsistencies. It does not test public IP, DNS, or network routes.

## Direct Android ↔ Linux LAN sync

The desktop sync server requires Python 3 and OpenSSL (`sudo apt install openssl`). Run it from this directory:

    python3 mora_identity.py sync-serve --minutes 15

Keep Android and Linux on the same trusted private Wi-Fi/LAN. The terminal prints the Linux private IPv4 address, a temporary bearer token and a SHA-256 certificate pin. In Mora Shield → Identity Studio → Sinkronisasi langsung Android ↔ Linux, enter those values and press **Sinkronkan dua arah**.

- The server uses a temporary self-signed TLS certificate; Android accepts it only when its SHA-256 pin matches the value shown in the Linux terminal.
- The bearer token is generated per run, is not written to disk and expires with the server session (1–60 minutes; default 15).
- The server binds only to private IPv4 interfaces, limits payloads to 256 KB / 100 profiles, and never logs authorization headers or profile contents.
- Repeated sync is idempotent. Identical profile IDs are treated as unchanged; conflicting IDs are rejected and local profile data is never overwritten.
- The sync endpoint is not exposed to the public internet by the tool. A host firewall may still need a temporary LAN-only allow rule for the selected TCP port.
- This is manual, direct profile sync, not continuous background sync. Built-in device catalogue presets, favorites, schedules, app settings and browser history are not synced.
- Stop it with Ctrl+C when finished. Do not share the token or certificate pin with anyone else.
