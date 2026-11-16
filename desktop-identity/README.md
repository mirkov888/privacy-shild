# Mora Shield Desktop Identity Bridge

Linux companion utility for managing isolated browser compatibility profiles used by Mora Shield. It is a command-line bridge, not a second Android application or a full Linux GUI dashboard. It never edits the user's normal browser profile, OS identity, hardware identifier, certificate or attestation.

Supported Linux browser targets: Firefox, LibreWolf, Zen, Brave, Chrome and Chromium. Firefox-family profiles use dedicated profile directories and user.js; Chromium-family profiles use a separate user-data directory and launcher.

Compatibility labels: Android, iPhone, iPad, Windows, macOS and Linux. Labels select a User-Agent test string only. They do not emulate operating systems, hardware, fonts, codecs, GPU, Client Hints, browser engines or mobile touch behavior. An iPhone/Safari User-Agent opened in Firefox remains Firefox.

## Commands

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

- Browser detection via doctor only checks whether supported executable names are on PATH.
- A browser may use a different binary name or install path; specify the browser manually and check its launch behavior.
- User-Agent overrides do not provide true device emulation or guarantee consistent Client Hints across browsers.
- Android external browsers are not modified by this helper.
- No IMEI, serial, Android ID, MAC, build fingerprint, security attestation or Play Integrity values are read or changed.
