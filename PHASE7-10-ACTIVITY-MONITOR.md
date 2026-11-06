# Mora Shield Activity Monitor — Combined Phase 7–10

This source update combines the activity-monitor work into one UI reachable from the existing **Monitor** bottom-navigation tab.

## Included UI

- **Dashboard:** installed-app count, connected Mora connectors, recent Core events, high/critical event count, Core registry summary, and device traffic totals.
- **App Monitor:** installed package inventory (user and system apps), package/UID, Mora Connector connection state, connector monitoring policy where available, event count, and per-UID traffic counters.
- **Activity Timeline:** persisted Mora Core events with event-type filtering. Shield module events are ingested into Core during refresh/polling; Connector events enter through the authenticated Connector service.
- **Network Monitor:** device and per-UID traffic counters from Android `TrafficStats`, plus an optional snapshot of active sockets from `ss -Htunap` when root is already available and the ROM provides `ss`.

## Data and limitations

- No fabricated/demo activity rows or hard-coded statistics are used.
- App inventory comes from PackageManager. Connector state/policy comes from Mora Core. Event history comes from Mora Core. Traffic counters come from Android TrafficStats.
- TrafficStats counters are cumulative since boot, not instantaneous throughput. Android/ROM may return `-1` for unsupported per-UID counters.
- Active socket visibility is best-effort, depends on root and `ss`, and is a point-in-time snapshot. It is not packet capture, DNS history, or a complete network firewall.
- Apps without a Mora Connector are explicitly shown as not connected; Mora Shield cannot infer their internal app events from package inventory alone.
- Permission-sensitive monitoring (usage access, accessibility, VPN capture, or deeper kernel hooks) is not silently enabled by this patch.

## Validation status

Static source checks only. A successful Android Gradle build, install, Binder/Connector integration test, and on-device traffic verification are still required. Do not treat this patch as build-verified until CI completes successfully.
