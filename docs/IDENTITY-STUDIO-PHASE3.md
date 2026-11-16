# Identity Studio — Phase 3: Mora WebView Preview
## Implemented
- A Mora-owned Android WebView preview applies the selected profile User-Agent.
- Displays navigator.userAgent, navigator.platform and viewport information.
- Local-only preview: network loads, remote images, file access and content access are disabled.
- No INTERNET permission is required; Chrome, Firefox and other applications are not modified.
## Manual verification
1. Open Mora Shield → Mora Identity Studio.
2. Select a profile such as iPhone 16 Pro Max under iOS / iPadOS.
3. Expand the local WebView preview.
4. Compare navigator.userAgent with the selected profile's copied value.
5. Select another profile and verify the preview changes.
## Scope
An Android WebView remains Android WebView. A User-Agent string does not emulate Safari/WebKit, iOS APIs, Windows/macOS APIs, hardware, or an actual device.
