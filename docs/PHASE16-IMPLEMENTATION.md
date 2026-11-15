# Phase 16 — Mora Shield module parity and usability

Branch: `feat/mora-shield-3.9.4-complete`

Phase 16 integrates the v3.9.4 module UI into the existing app without replacing Mora Core, Connector, Room 2.6.1, the SDK source set, or Phase 15 per-app privacy.

## Implemented
- Hosts card: `hosts update`, `hosts show` summary, add/remove/enable/disable sources, whitelist/blacklist, `bh_on`, and `bh_days`.
- AdAway/bindhosts card: `aw_yield`, `aw_export`, `aw_installed`, `bindhosts_on`, bindsync action, and setup/safety notes.
- Module health: version, enabled/disabled state, boot failure count, and known hosts/bindhosts conflict indicators.
- Network controls `nw_act` and `nw_dry`, Border/Home profiles, purge traffic/Wi-Fi/all, and dynamic selftest rows were already present in `ModuleV36Screen` and remain intact.
- `TRAFSPIKE` is labeled as “Lonjakan data”. Network warnings, permission revocations, and traffic spikes are counted in one daily digest after 20:00; those event types no longer emit one notification per event. The digest depends on the foreground event service running.
- Event list supports type, text/domain, app/package, and date-range filtering. A parseable domain in an event detail gets a one-tap whitelist action.
- Widget includes vault status and Aktifkan/Buka actions. The displayed state reflects the latest widget action.
- Quick Settings includes Brankas and Border tiles, alongside existing Lockdown/Panic tiles.
- Password-encrypted settings backup/restore uses PBKDF2-HMAC-SHA256 and AES-GCM with authenticated decryption and input validation.
- Timed guest mode activates the configured vault, locks the screen once, and restores the vault after the selected duration. The timer is re-armed after reboot.
- Persistent signing helper `buat-keystore.sh` matches the four `MORA_*` GitHub secrets used by the release workflow. No keystore or secret is stored in Git.
- App version metadata is set to 3.9.4 / versionCode 24. CI tests are mandatory.

## Important limitation
Guest mode uses the module vault plus a one-time device screen lockdown. It does not create a separate PIN prompt for every protected app. Verify the selected vault package list and the behavior on the target ROM before lending the phone.

## Validation
- Android CI passed on commit `e2880a6bcd47f0d43a9f87cf812838742fb5b7f5` (unit tests, Kotlin compilation, debug APK).
- Release workflow on that commit passed the secret validation and unit-test steps; release APK build/upload was still running when this document was updated.
- Branch is not merged into `main`. Perform on-device checks for hosts, AdAway helper, tiles, widget, timed restore, and signing upgrade before merging.
