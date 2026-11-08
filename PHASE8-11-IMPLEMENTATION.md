# Mora Shield Phases 8–11 — Implementation Notes

This document records the current source implementation and its boundaries. All monitoring and history are local to the device unless an installed, authenticated Mora Connector explicitly publishes an event into Mora Core.

## Phase 8 — App Activity & Network Monitor

- Installed app inventory, package name, UID, system/user classification, connector state, monitoring policy, and associated event counts.
- Per-UID and whole-device cumulative network counters through Android TrafficStats.
- Local 15-minute traffic samples and 24-hour estimates, with reset/reboot counter handling and bounded retention.
- Best-effort active socket snapshot through `ss -Htunap` only when root is already available.
- Dashboard device-health values: CPU sample, RAM, internal app-storage capacity, battery percentage/temperature, and uptime.

**Boundaries:** TrafficStats is cumulative, not packet capture or instantaneous throughput. Unsupported UID counters are displayed as unavailable. Socket visibility depends on ROM tools/root and is a point-in-time snapshot.

## Phase 9 — Policy Center & Connector Manager

- Mora Core registry displays installed/connected/not-installed states, app version, API version, capabilities, and last-seen heartbeat.
- Per-app master enable and event-monitoring switches.
- Connector command access is disabled by default; capabilities must be allowlisted and every remote command requires explicit user approval.
- MoraSystemManager policy card exposes monitoring, network-mode, and background-mode settings.
- Connector IPC requires the declared signature permission, installed package registration, matching signing certificate, API version, session token, and heartbeat.

**Boundaries:** Network/background mode values are policy data. They are not OS-level firewall or background-restriction enforcement unless a separately verified worker handler implements them.

## Phase 10 — Security Event Center & Audit

- Activity timeline stores Shield and authenticated Connector events in Mora Core with severity and timestamps.
- Network-risk heuristics are added to the timeline when detected.
- Command audit stores request status, package, command ID, result summary, and timestamps.
- Encrypted event export uses AES-256-GCM with a PBKDF2-HMAC-SHA256 derived key.
- Security screen lists granted sensitive permissions and available usage metadata; unused-app suggestions remain dry-run.

**Boundaries:** Network-risk checks are heuristics, not proof of interception, fake base stations, or malicious access. Permission list and app last-used time are not full AppOps access history.

## Phase 11 — Local Automation & Privacy Rules

- User opt-in local notification rules for Medium+ or High/Critical events.
- Rules default to disabled; notification permission is respected.
- Five-minute cooldown per source package, event type, and affected package.
- Rules execute only after a new event is inserted into Mora Core, including authenticated connector events.
- No automatic shell, package disable, permission revoke, network block, or remote telemetry action is permitted by this rules engine.
- Unit tests cover severity-threshold matching.

## Validation

CI and release APK build must pass before this implementation is considered build-verified. Runtime connector authentication, traffic counters, notification delivery, and OEM permission behavior still require on-device tests.
