# Mora Shield — Security & Privacy Extensions (Phase 11)

This update extends the existing Phase 7–10 Activity Monitor in the same Mora Shield app.

## Included in this source update

1. **Stable release signing workflow**: GitHub Actions requires four repository secrets and signs every release with the same keystore. The keystore is not stored in this repository. Local builds may still use the debug key when no release keystore environment is supplied.
2. **Link-cleaner regression tests**: JUnit tests cover global tracking parameters, YouTube parameters, Google redirect unwrapping, plain text, and punctuation. CI runs `testDebugUnitTest` before assembling the release APK.
3. **Network-risk heuristics**: checks observed 4G/5G-to-2G type downgrade, registered cell identity changes (when the required permissions are granted), same-SSID BSSID changes, and Wi-Fi encryption changes when scan results are available. These are indicators only; normal roaming, mesh Wi-Fi, coverage changes, and carrier behavior can cause them.
4. **Permission overview**: lists currently granted sensitive runtime permissions and app-level last-used time when Usage Access data is available. Last-used time is not the timestamp of a specific permission access and is not a substitute for a complete AppOps audit.
5. **Unused-app review**: identifies candidate user apps not used for 90 days when Usage Access data exists. This version is dry-run only; it does not automatically revoke permissions. Exclusions include Android system packages and Mora Shield.
6. **Traffic history**: stores 15-minute device and per-package UID counter samples locally, retaining bounded history. Counters are cumulative since boot; reboot/reset boundaries are handled as separate counter segments. Android may return unsupported values for some UIDs.
7. **Home-screen widget**: shows latest traffic counters and 24-hour event count; it refreshes when a new traffic sample is recorded while the Monitor screen is active.
8. **Sensitive app hide/restore**: user-selected packages can be disabled with `pm disable-user` and restored with `pm enable`. A confirmation is shown; core Android packages and Mora Shield are protected. This only disables access/visibility and does not erase data or protect against forensic examination.
9. **Encrypted audit export primitive and UI**: exports the current Mora Core event timeline as AES-256-GCM encrypted bytes using a passphrase-derived key (PBKDF2-HMAC-SHA256, 210,000 iterations). The passphrase must be at least 10 characters and is not saved.
10. **Local automation rules (opt-in)**: configurable Medium+ or High/Critical event thresholds, local notifications only, and a five-minute per-source/type/affected-app cooldown. Rules are disabled by default and evaluated only for newly inserted events from Shield or authenticated connectors. They never execute shell commands, block apps, revoke permissions, or send telemetry.

## Explicit limitations / follow-up

- The permission overview is not yet a full AppOps per-permission access-history implementation. It lists granted sensitive permissions and app-level last-used time only. Android version, OEM behavior, special access, and background restrictions affect availability.
- The unused-app workflow is intentionally dry-run only. This update does not yet expose custom per-package exclusion management or unattended permission revocation.
- Network checks require the relevant runtime/special permissions. Location and phone identifiers are not uploaded; a hashed cell identity is stored locally for change comparison.
- Cell identity change cannot establish that the user was stationary. A handover is normal and does not establish a fake base station.
- The widget currently shows traffic counters and event count, not a separate risk score. It has no independent background polling service; it refreshes when Mora Shield records a new sample.
- The app-hide feature requires working root authorization and may be restricted by the ROM. It is not an emergency data wipe or anti-forensics feature.
- The encrypted export currently covers Mora Core event records; it does not include every Android log, packet capture, or raw permission manager database.
- Local automation rules require notification permission on Android 13+. If permission is absent, rules remain saved but notifications are not posted.
- Local automation only reacts to events that reach Mora Core; it does not independently inspect all apps or run a background scanner.
- No APK build or device test is claimed here. Run CI after adding the signing secrets and verify behavior on the target device.

## GitHub Secrets setup

Generate a private release key locally and keep the `.jks` file outside the repository:

```bash
keytool -genkeypair -v -keystore mora-release.jks -alias mora-shield \\
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 mora-release.jks > mora-release.jks.base64
```

Add these repository Actions secrets:

- `MORA_KEYSTORE_BASE64`: complete contents of `mora-release.jks.base64`
- `MORA_KEYSTORE_PASSWORD`: keystore password
- `MORA_KEY_ALIAS`: `mora-shield` (or the alias selected above)
- `MORA_KEY_PASSWORD`: private-key password

Never commit the keystore, base64 file, or passwords. Back up the keystore securely; losing it prevents future in-place updates signed with this key.

**Important migration note:** the previous workflow used a transient GitHub runner debug key. A release signed with the new permanent key will not update an installation signed by a different certificate. A one-time uninstall/reinstall may therefore be required for the first permanent-key release; after that, updates can install over the previous release as long as the same keystore is retained.
