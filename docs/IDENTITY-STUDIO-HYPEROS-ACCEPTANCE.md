# Mora Shield — HyperOS physical-device acceptance

This procedure is for the actual Android phone. GitHub Actions cannot verify Xiaomi/HyperOS background policies, the installed WebView provider, LSPosed activation or browser behavior on the user's device.

## ADB preflight

On Debian/Ubuntu:

    sudo apt install adb
    adb devices
    bash scripts/mora-hyperos-acceptance.sh

For multiple connected phones:

    ANDROID_SERIAL=<adb-device-serial> bash scripts/mora-hyperos-acceptance.sh

The script records manufacturer/model, Android/HyperOS release properties, WebView provider, installed browser packages, Mora Shield package metadata, a launch smoke test, JobScheduler/battery-policy snapshots and relevant recent logs. It intentionally does not query Android hardware identifier properties such as IMEI, device serial property, Android ID, MAC address or build fingerprint. The ADB device selector is used internally and redacted from output. The report file is created with owner-only permissions.

Install the debug APK first when needed:

    adb install -r app-debug.apk

## Manual Identity Studio checks

1. Open Mora Shield → Lainnya → Mora Identity Studio. Confirm the profile list, search, favorites and current selection render without crashes.
2. Run **Scan kompatibilitas Android**. Record the WebView provider/version and whether User-Agent Metadata and Document Start Script are supported.
3. Open the offline WebView preview. Record a baseline, toggle Canvas/WebGL/WebRTC protections one by one, create a new session and compare the JSON reports. A disabled API may break websites; the preview should remain local and must not navigate to external URLs.
4. Create a custom profile and export it. Import the same file again; verify duplicates are not created. Import an invalid/oversized file and verify rejection.
5. Export a full Identity Studio backup. Change profile and protection settings, restore the backup, then test rollback. Verify the original-device baseline on the phone remains local and is not replaced by another device's backup.
6. Enable randomizer scheduling. Inspect the JobScheduler snapshot, reboot the phone and check again. Xiaomi battery/background restrictions may delay WorkManager jobs; record whether the schedule is pending, running or deferred rather than assuming the timer ran exactly on time.
7. For the LSPosed companion hook, use only a non-critical test app. Verify the package scope in LSPosed Manager, force-stop/reopen the test app, confirm only the explicitly supported public Build labels are affected, then disable the profile and verify recovery. Do not test with banking, payment, identity-verification or other critical apps.
8. On the same trusted Wi-Fi/LAN as Debian, start `python3 desktop-identity/mora_identity.py sync-serve --minutes 15`. Enter the private IPv4, temporary token and SHA-256 certificate pin in the LAN sync card. Sync twice and confirm the second run reports existing profiles as unchanged. Stop the server with Ctrl+C.
9. Keep the phone on its normal battery policy for one test, then repeat with the app allowed to run in the background if needed. Record any difference. Do not treat an app being absent from the device-idle whitelist as an automatic failure.

## Acceptance record

Record:
- Phone model / HyperOS version / Android API.
- WebView provider and version.
- Mora Shield APK version and commit SHA.
- User-Agent Metadata / Document Start Script support.
- Canvas / WebGL / WebRTC before-and-after result.
- Backup / restore / rollback result.
- WorkManager behavior across reboot and battery restrictions.
- LSPosed version, target package scope and recovery result.
- LAN sync result and confirmation that the normal Linux browser profile is unchanged.
- Any crash log excerpt from the generated ADB report.

Do not include IMEI, serial, Android ID, MAC address, access tokens, certificate private keys or other secrets in a shared test report.
