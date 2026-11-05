#!/usr/bin/env python3
"""Add the Downloader wake binding and central policy/control card to Mora Shield."""
from __future__ import annotations
import argparse
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "app" / "src" / "main" / "java" / "com" / "mora" / "shield"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise RuntimeError(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 0:
        if new in text:
            return text
        raise RuntimeError(f"Anchor {label} tidak ditemukan; source mungkin berbeda.")
    if count != 1:
        raise RuntimeError(f"Anchor {label} ditemukan {count} kali; hentikan untuk menghindari patch salah.")
    return text.replace(old, new, 1)


def patch_main_activity(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "MoraConnectorWakeManager.bindKnownCompanions(this)" in text:
        return
    old = '''    override fun onResume() {
        super.onResume()
'''
    new = '''    override fun onStart() {
        super.onStart()
        com.mora.shield.connector.MoraConnectorWakeManager.bindKnownCompanions(this)
    }

    override fun onStop() {
        com.mora.shield.connector.MoraConnectorWakeManager.unbindAll(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
'''
    text = replace_once(text, old, new, "MainActivity wake binding lifecycle")
    path.write_text(text, encoding="utf-8")


def patch_tools(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "MoraDownloaderControlCard()" in text:
        return
    insertion = "        LinkCard()\n"
    if "MoraCoreCard()" not in text:
        insertion += "        MoraCoreCard()\n"
    insertion += "        MoraDownloaderControlCard()\n"
    text = replace_once(
        text,
        "        LinkCard()\n",
        insertion,
        "ToolsScreen central Core/Downloader cards",
    )
    path.write_text(text, encoding="utf-8")


def patch_gradle(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r'versionName\s*=\s*"3\.6\.0"', 'versionName = "3.6.1"', text, count=1)
    text = re.sub(r'versionCode\s*=\s*13\b', 'versionCode = 14', text, count=1)
    path.write_text(text, encoding="utf-8")


def append_readme(path: Path) -> None:
    marker = "## Phase 3 — MoraDownloader central control"
    content = path.read_text(encoding="utf-8") if path.exists() else "# Mora Shield\n"
    if marker in content:
        return
    section = '''

## Phase 3 — MoraDownloader central control

- The visible Shield Control Center binds the signature-protected `MoraDownloaderWakeService` while the Activity is started. This wakes the Downloader process and lets its own SDK client register with Core, without a permanent foreground service.
- Tools > MoraDownloader · Central Control exposes monitoring policy, a command-access master switch, per-capability allowlisting, status, start, pause, resume and cancel.
- Remote commands remain disabled until explicitly enabled and allowlisted. `download.start` and `download.cancel` require per-action user confirmation.
- Install the same-signed MoraDownloader 1.1.0 companion build. Shield must be installed first so its signature permission exists.
'''
    path.write_text(content.rstrip() + section, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Apply Mora Shield Phase 3 host-side Downloader control")
    parser.add_argument("project", type=Path, help="Root of the existing privacy-shild project")
    args = parser.parse_args()
    project = args.project.expanduser().resolve()
    require(project.is_dir(), f"Project folder not found: {project}")
    main_activity = project / "app/src/main/java/com/mora/shield/MainActivity.kt"
    tools_screen = project / "app/src/main/java/com/mora/shield/ScreenTools.kt"
    gradle = project / "app/build.gradle.kts"
    core = project / "app/src/main/java/com/mora/shield/core/MoraCore.kt"
    service = project / "app/src/main/java/com/mora/shield/connector/MoraConnectorService.kt"
    for path in (main_activity, tools_screen, gradle, core, service):
        require(path.is_file(), f"File wajib hilang: {path.relative_to(project)}")
    require("dispatchCommand" in service.read_text(encoding="utf-8"), "Host Phase 2 Connector API belum terpasang.")
    require("fun appPolicy(" in core.read_text(encoding="utf-8"), "Mora Core Phase 1 tidak terdeteksi.")

    dest = project / "app/src/main/java/com/mora/shield"
    wake_dest = dest / "connector"
    wake_dest.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SRC / "MoraDownloaderControlCard.kt", dest / "MoraDownloaderControlCard.kt")
    shutil.copy2(SRC / "connector/MoraConnectorWakeManager.kt", wake_dest / "MoraConnectorWakeManager.kt")

    patch_main_activity(main_activity)
    patch_tools(tools_screen)
    patch_gradle(gradle)
    append_readme(project / "README.md")
    print("Mora Shield Phase 3 berhasil diterapkan.")
    print("Version target: 3.6.1 / versionCode 14")
    print("Next: ./gradlew assembleRelease --no-daemon")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print("ERROR:", error, file=sys.stderr)
        raise SystemExit(2)
