# Phase 12 — Per-App Control

## Implemented
- Dedicated App Control screen accessible from More.
- Lists third-party installed packages and checks disabled package state.
- Per-package freeze and restore via KernelSU/Magisk root and Android package manager.
- Explicit confirmation before each state change.
- Protects Mora Shield and excludes system apps from the UI and package allowlist.
- Search and filters for all, active, and frozen apps.
- Freeze preserves app data; it disables the package for Android user 0 and removes its launcher entry as a side effect.
- Unit tests cover package allowlisting and package-list parsing.

## Scope and limitations
- Does not uninstall APKs or delete user data.
- Does not hide a package from another app's package queries. That is a separate compatibility-sensitive feature and is not represented as complete.
- Freeze can affect apps that depend on the selected package. Avoid freezing core services or companion packages.
- Root access must be granted to Mora Shield. Behavior may vary across Android ROMs and OEM package managers.
- Build/CI success is not equivalent to on-device validation. Test freeze/restore on a non-critical third-party app first.
