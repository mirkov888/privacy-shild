# Mora Shield Phase 3 — Downloader Control Center

Host-side companion patch for the MoraDownloader Phase 3 worker integration.

## Adds

- `MoraConnectorWakeManager`: binds the signed Downloader wake service while the Shield Activity is started; unbinds when Shield leaves foreground.
- `MoraDownloaderControlCard`: Tools > MoraDownloader Central Control, with monitoring policy, command-access master switch, per-capability allowlist and remote status/start/pause/resume/cancel controls.
- Start and cancel use explicit confirmation dialogs. The user must enable command access and check each capability; nothing is allowed by default.
- Version bump from 3.6.0 / 13 to 3.6.1 / 14.

## Install

Apply after Phase 1, Phase 2 and the MoraDownloader Phase 3 worker patch:

```bash
python3 apply-mora-shield-phase3.py ~/privacy-shild
cd ~/privacy-shild
./gradlew assembleRelease --no-daemon
```

The installer checks for the Phase 2 remote dispatch API before modifying the project and is idempotent.

## Use

1. Install Mora Shield first, signed with the shared production key.
2. Install MoraDownloader 1.1.0 signed with the same key.
3. Open Shield > Alat. Shield binds Downloader's protected wake service while its Control Center is visible.
4. In MoraDownloader · Central Control, enable activity monitoring.
5. Enable remote command access and allow only the capabilities you want. `download.start` and `download.cancel` require per-action confirmation.
6. Use Status / Mulai / Jeda / Lanjut / Batal. Downloader continues working independently when Shield is closed.

The downloader engine pauses/cancels at safe checkpoints. An in-progress Telethon file transfer may finish before pause/cancel takes effect.
