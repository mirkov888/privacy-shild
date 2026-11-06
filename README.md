# Mora Ecosystem Phase 5 — Central Policy UI

Adds a MoraSystemManager policy card to Mora Shield Settings and places the existing MoraDownloader central-control card in the same central settings area.

## Controls
- Worker policy enable/disable
- Event monitoring enable/disable
- Network policy value: inherit / allow / block / Wi-Fi only / mobile only
- Background policy value: normal / restricted / allowed
- Live connector status and policy refresh

## Safety boundary
This phase adds policy UI only. It does not expose arbitrary shell, Root, Shizuku, package uninstall, property editing, or service management commands through Connector IPC. Network/background values are persisted centrally; OS-level enforcement requires a separately reviewed worker-side capability/handler and is not claimed here.

## Apply
```bash
python3 apply-mora-ecosystem-phase5.py ~/privacy-shild
cd ~/privacy-shild
./gradlew assembleDebug --no-daemon
```

Install the updated Mora Shield APK. For live worker status, install/open the matching worker APKs. Connector IPC still requires matching signing certificates across the release APKs.

## Validation
The installer is designed to be idempotent and uses an exact `MonitorCard(ctx)` insertion anchor. Build and device IPC validation must be run against the user's current checkout. This package does not include a verified APK.


## Phase 11 — Security & Privacy Extensions

See `PHASE11-SECURITY-PRIVACY-EXTENSIONS.md` for persistent GitHub release signing, link-cleaner tests, network-risk heuristics, permission overview, dry-run unused-app review, traffic history, home-screen widget, reversible app hide/restore, and encrypted audit export. Configure the documented GitHub Actions secrets before the release workflow is expected to pass.
