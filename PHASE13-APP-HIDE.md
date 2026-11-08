# Phase 13 — Hide / Restore Launcher Icons

## Added
- Separate **Sembunyikan / Tampilkan** action alongside Freeze / Activate in App Control.
- Queries the app's launcher activities and disables only those activity components for Android user 0.
- The package itself remains installed and enabled; background services and non-launcher activities are not intentionally disabled.
- Stores the exact launcher component names in Mora Shield preferences so **Tampilkan** can restore them.
- Adds a dedicated **Disembunyikan** filter and parser unit coverage.
- Hide is available only for enabled third-party apps; frozen apps must be activated first.
- Mora Shield and Android/OEM system packages remain protected.

## Important behavior
This removes the app's normal launcher icon. It is not package-name spoofing or package concealment: Settings, ADB, and privileged package queries can still see the app. The app may still be started by an explicit intent/deep link or its background components. Freeze remains separate and disables the entire package for user 0. App data is not deleted.

## Compatibility and recovery
The hide action depends on `cmd package query-activities --brief --components` and root permission to disable/enable components. If the ROM does not return launcher components or a command fails, the operation reports an error and rolls back components already changed. The component list is stored locally for restoration; clearing Mora Shield app data while an app is hidden also clears that recovery list.

## Validation
CI unit tests and release APK build are triggered by the commits. Actual launcher behavior still requires validation on the rooted target device/ROM; CI cannot verify vendor launcher behavior.
