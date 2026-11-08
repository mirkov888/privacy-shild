# Phase 13 — Per-App Location Control

## Architecture
- Mora Shield stores each app profile and its named presets locally.
- `location-hook` is a separate legacy Xposed-compatible companion APK. Install it and explicitly enable its scope in LSPosed for each target app.
- The companion hooks `android.location.Location.getLatitude()` and `getLongitude()` only inside the scoped target process. It never calls Android's global mock-location provider.
- The hook reads a read-only profile bridge from Mora Shield's exported provider. The provider derives the caller package from Binder UID and returns only that caller's own profile.
- Random world points use a uniform spherical distribution. Radius points use `sqrt(U) * radius` and a spherical destination calculation to avoid center bias.
- Random coordinates are generated lazily on location reads and remain stable for the configured interval. Profile and interval settings persist across reboot; the interval phase restarts from elapsed realtime after reboot.

## Setup
1. Install Mora Shield and the companion `Mora-Location-Hook` APK.
2. In LSPosed, enable Mora Location Hook and select only the apps that should receive simulated coordinates.
3. Reboot or force-stop and relaunch the selected target app.
4. Open Mora Shield > Per-App Location Control. The backend heartbeat should appear for the target app before enabling spoofing.
5. Test one target app and one unselected control app.

## Limitations
- This is a process-level Location getter interception backend, not a system-wide location provider replacement.
- Apps that use proprietary location SDKs, native location APIs, cached values, fused APIs that bypass `Location` getters, integrity checks, or direct GNSS access may not be affected. Verify each app separately.
- Do not enable the hook for Android/system processes. Do not use it as a substitute for physical-device testing.
- Clearing Mora Shield app data removes saved profiles and presets; the companion then returns real location because no enabled profile can be read.
- CI can compile and unit-test the engine, but only a rooted Redmi Note 12 Pro 4G with compatible LSPosed can verify end-to-end isolation.
