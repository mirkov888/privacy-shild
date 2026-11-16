# Mora Shield Desktop Identity Bridge

Companion utility for Linux browser compatibility testing. It creates isolated, disposable profiles and never edits an existing browser profile, browser binary, OS identity, hardware identifier, certificate or attestation.

Supported browsers: Firefox, LibreWolf, Zen, Brave, Chrome and Chromium. Firefox-family profiles use a dedicated profile directory and user.js; Chromium-family profiles use a separate user-data directory and launcher.

Compatibility labels: Android, iPhone, iPad, Windows, macOS and Linux. Labels select a User-Agent test string only. They do not emulate operating systems, hardware, fonts, codecs, GPU, Client Hints or mobile touch behavior.

Usage:
    python3 mora_identity.py list
    python3 mora_identity.py create --id android-test --browser firefox --platform android
    python3 mora_identity.py create --id iphone-test --browser zen --platform iphone
    python3 mora_identity.py create --id win-test --browser brave --platform windows
    python3 mora_identity.py launch --id android-test
    python3 mora_identity.py backup --id android-test --output ~/mora-android-test.zip
    python3 mora_identity.py rollback --id android-test

Default storage: ~/.config/mora-shield/identity-profiles. Supply --root PATH before a subcommand for portable storage or tests.

Every profile contains diagnostics.html. Open it locally to inspect User-Agent, Client Hints when available, platform, locale, timezone, screen, WebGL, Canvas availability and WebRTC API presence. It does not transmit reports. WebRTC restrictions are best-effort and enabled by default; use --allow-webrtc only when needed for compatibility testing.

Rollback removes only a Mora-created profile after checking its manifest. Backups restore only to a new ID; existing targets are never overwritten. A User-Agent override is not proof of device identity.
