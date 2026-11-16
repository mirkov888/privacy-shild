# Mora Identity Studio — Phase 3: Randomization and WebView Preview

## Randomizer engine
- A reusable seed is stored locally in Mora-private preferences; a random 128-bit seed is generated when the user leaves it blank.
- Deterministic selection uses SHA-256 over the seed, scope and run number, so the same seed/scope/run index selects the same catalogue entry.
- The user can randomize immediately or schedule a periodic run at 6, 12, 24, 48 or 168 hours with WorkManager.
- The scheduled task can select a browser-compatibility profile for the active platform.
- Optional per-app randomization visits only explicitly enabled per-app compatibility profiles. It changes Manufacturer, Brand, Model, Device and Product labels only. The target app may need a force-stop/restart before its process observes changed labels.
- The randomizer never changes IMEI, serial, Android ID, MAC address, build fingerprint, Android release/SDK, security patch, hardware attestation or Play Integrity.
- WorkManager may defer execution due to Android battery and background-execution policies. The schedule is periodic, not an exact wall-clock alarm.

## Mora WebView preview
- A Mora-owned Android WebView preview applies the selected profile User-Agent.
- Reports navigator.userAgent, platform and viewport.
- Local-only preview: network loads, remote images, file access and content access are disabled.
- No INTERNET permission is required; Chrome, Firefox and other applications are not modified.

## Manual verification
1. Open Mora Shield → More → Mora Identity Studio.
2. Select an Android or iOS/iPadOS compatibility profile.
3. Use Randomize now and verify the selected profile and run counter update.
4. Set a reusable seed, select an interval and enable the schedule; save and verify the scheduled WorkManager request in app behavior/device logs.
5. Optional: enable one per-app compatibility profile, enable per-app randomization, run once, then force-stop and reopen that target app to check the selected non-security labels.
6. Compare the preview's selected User-Agent with the local diagnostic output.

## Scope
An Android WebView remains Android WebView. A User-Agent string does not emulate Safari/WebKit, iOS APIs, Windows/macOS APIs, physical device hardware or actual device identity. Scheduled work can be delayed by the operating system.
