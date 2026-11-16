# Mora Identity Studio — Phase 1 & 2

## Phase 1 — Identity Engine
- Captures a read-only snapshot of the actual Android build identity through public Android APIs.
- Stores the selected compatibility profile in app-private preferences.
- Provides profile lookup, selection, reset, and random selection per platform.
- Exposes the selected catalogue User-Agent for future opt-in use by Mora-owned WebViews.
- Does not modify global Android properties, hardware identifiers, attestation, Play Integrity, or third-party apps.

## Phase 2 — Profile Database
The bundled starter catalogue covers Android, iOS/iPadOS, Windows, macOS, and Linux browser profiles. The browser picker reuses the curated Android app-compatibility model catalogue, including Google Pixel, Samsung Galaxy, Xiaomi/Redmi/POCO, OnePlus, OPPO, vivo, ASUS, Sony, Motorola and Nothing entries. Apple profiles include multiple iPhone generations and iPad; desktop profiles include Windows 10/11 PCs and laptops, Mac/MacBook and Linux desktop/laptop layouts. The catalogue remains curated rather than exhaustive and can be extended with additional verified devices and browser versions.

## Behavior and limitations
- Selecting a profile stores it locally; it does not silently change Chrome, Firefox, or other external browsers.
- iOS, Windows, macOS, and Linux profiles simulate browser identity for layout and compatibility testing only. Static User-Agent versions are test templates and must be refreshed/verified against the browser under test.
- Android system identity remains read-only in this phase.
- Existing Per-App Device Identity remains unchanged and separate.
- Randomize selects a catalogue profile; it does not generate fake serial numbers, IMEI, MAC addresses, or attestation data.

## Validation
Unit tests cover unique profile IDs, platform coverage, and User-Agent presence. A full Android Gradle build and device-side WebView integration test remain required before release.
