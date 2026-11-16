# Identity Studio — Phase 5: System & Fingerprint Protection

## Delivered

- Canvas protection for Mora-owned preview WebViews: a minimal, deterministic pixel perturbation is applied to readback/export paths. The result is stable during one session and changes when a new session seed is created.
- WebGL protection: generic vendor/renderer values are exposed and `WEBGL_debug_renderer_info` is withheld for the preview.
- Profile isolation: switching profile or session creates a fresh WebView instance. Network loads, network images, DOM storage, file/content access and cache reuse are disabled; the preview uses only local HTML.
- User-Agent and User-Agent Client Hints synchronization: the selected User-Agent is paired with AndroidX WebKit `UserAgentMetadata` for Chromium-style profiles when the installed WebView supports the feature. Safari/Firefox profiles do not receive fabricated Chromium Client Hints.
- Per-session randomization: each new session gets a cryptographically random seed; a profile-stable seed can be used when session randomization is disabled.
- Linux browser profiles: Firefox-family browsers use their built-in `privacy.resistFingerprinting` preference; Chromium-family profiles can load a Mora Manifest V3 extension that blocks common page-context Canvas readback methods. Worker contexts and all other fingerprint surfaces are not claimed as covered.
- Per-app Android compatibility properties are limited to `Build.MANUFACTURER`, `BRAND`, `MODEL`, `DEVICE`, and `PRODUCT` for explicitly selected packages via LSPosed.

## Explicitly unchanged

- IMEI, serial number, Android ID, MAC address and other hardware/device identifiers.
- `Build.FINGERPRINT`, Android release/SDK, security patch level, verified boot state, hardware attestation and Play Integrity.
- The global Android system identity and non-selected applications.
- Ordinary Android third-party browser processes. Linux protection applies only to Mora-created isolated browser profiles; Android Chrome/Firefox are not modified by this phase.

Legacy fingerprint/release/patch values may remain in older local settings for compatibility, but the Phase 5 hook no longer applies them.

## Validation

Automated unit tests cover Canvas/WebGL injection selection, protection disablement, session seed generation and seed sanitization. CI builds the app and LSPosed companion APK and runs the existing test suite.

## Limits

Fingerprint protection is best-effort and scoped: Android controls apply to the Mora offline preview; Linux controls apply to Mora-created isolated profiles. It does not normalize every browser surface (fonts, audio, GPU timing, screen metrics, APIs, worker contexts, or network-level behavior). A WebView remains Chromium on Android even when its User-Agent profile describes another platform. User-Agent Client Hints depend on the installed WebView implementation and are not available for every profile.
