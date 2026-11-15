# Phase 16 — Mora Shield module parity

This phase integrates the v3.9.4 module-management UI into the existing app without replacing the newer Mora Core, Connector, per-app privacy, or Room-backed architecture.

## Implemented in this commit
- Hosts management card: status summary, update action, source add/remove/enable/disable, blacklist and whitelist, and bh_on/bh_days controls.
- AdAway/bindhosts compatibility card: aw_yield/aw_export, detected status, bindhosts sync, and guidance.
- Module health card: module version, disabled status, failed boot count, and known hosts conflict indicators.
- TRAFSPIKE human-readable event label and urgent notification.
- Activity list search, app/package filter, and date range.
- Input validation for host domains and HTTPS source URLs; no raw unvalidated values are sent to the root shell.

## Existing features retained
The current ModuleV36Screen already contains nw_act/nw_dry, border/home profiles, traffic/Wi-Fi/all purge controls, and the module test action. The diagnostic screen reads the selftest rows returned by the module dynamically.

## Still not implemented by this phase
- Daily scheduled digest notification (network warnings, permission revocations, traffic spikes).
- Widget vault status and quick actions.
- Dedicated Quick Settings tiles for vault and border.
- One-tap whitelist from a blocked-domain event or app list.
- Password-encrypted settings export/import (the current encrypted audit export is not yet a reversible settings backup).
- Temporary guest/borrow-phone mode with app lock and timed vault restore.

## Validation
GitHub Actions must pass on the branch before this phase is considered build-verified. No keystore or secret material belongs in the repository.
