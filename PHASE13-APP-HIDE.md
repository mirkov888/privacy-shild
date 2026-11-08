# Phase 13 — Hide / Restore Apps

## Added
- Separate **Sembunyikan / Tampilkan** action alongside Freeze / Activate in App Control.
- Uses Android Package Manager hidden state for user 0 through root:
  - `pm hide --user 0 PACKAGE`
  - `pm unhide --user 0 PACKAGE`
- Reads hidden state from `dumpsys package` and exposes a dedicated **Disembunyikan** filter.
- Hide is available only for enabled third-party apps; frozen apps must be activated first.
- Mora Shield and Android/OEM system packages remain protected.
- Added parser unit coverage for hidden state and user 0 scoping.

## Important behavior
This hides the app from the normal launcher/user-facing app surface while leaving the package installed. It is **not** package-name spoofing or guaranteed concealment from Settings, ADB, privileged apps, or system package queries. Freeze remains a separate operation that disables the package for user 0. App data is not deleted by either operation.

## Validation
CI unit tests and release APK build are triggered by the commits. Actual behavior of `pm hide/unhide` still requires validation on a rooted target device/ROM; CI cannot verify vendor-specific Package Manager behavior.
