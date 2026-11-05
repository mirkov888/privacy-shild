#!/usr/bin/env python3
"""Copy Mora Core sources and apply safe, idempotent integration edits."""
from pathlib import Path
import shutil
import sys

SOURCE_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else Path.cwd().resolve()

if not (PROJECT_ROOT / "app" / "build.gradle.kts").is_file():
    raise SystemExit(f"Project Mora Shield tidak ditemukan: {PROJECT_ROOT}")

# Copy the new source files while preserving the repository paths.
for relative in (
    Path("app/src/main/java/com/mora/shield/core/ConnectorContracts.kt"),
    Path("app/src/main/java/com/mora/shield/core/MoraCore.kt"),
    Path("app/src/main/java/com/mora/shield/connector/ShieldModuleConnector.kt"),
    Path("app/src/main/java/com/mora/shield/connector/MoraConnectorContract.kt"),
    Path("app/src/main/java/com/mora/shield/connector/MoraConnectorService.kt"),
    Path("app/src/main/java/com/mora/shield/connector/MoraConnectorClient.kt"),
    Path("app/src/main/java/com/mora/shield/MoraCoreCard.kt"),
    Path("docs/MORA_CORE_CONNECTOR_API.md"),
):
    source = SOURCE_ROOT / relative
    destination = PROJECT_ROOT / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and destination.read_text(encoding="utf-8") == source.read_text(encoding="utf-8"):
        print(f"sudah ada: {relative}")
    else:
        shutil.copy2(source, destination)
        print(f"dipasang: {relative}")


def edit(relative: str, transform) -> None:
    path = PROJECT_ROOT / relative
    if not path.is_file():
        raise SystemExit(f"File integrasi tidak ditemukan: {path}")
    original = path.read_text(encoding="utf-8")
    updated = transform(original)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"diubah: {relative}")
    else:
        print(f"sudah terintegrasi: {relative}")


def add_import(text: str, import_line: str, anchor: str) -> str:
    if import_line in text:
        return text
    if anchor not in text:
        raise SystemExit(f"Titik import tidak ditemukan: {anchor.strip()}")
    return text.replace(anchor, anchor + import_line + "\n", 1)


def integrate_main(text: str) -> str:
    text = add_import(text, "import com.mora.shield.core.MoraCore", "import android.os.Bundle\n")
    marker = "        enableEdgeToEdge()\n"
    if "        MoraCore.initialize()\n" not in text:
        if marker not in text:
            raise SystemExit("Titik inisialisasi MainActivity tidak ditemukan")
        text = text.replace(marker, marker + "        MoraCore.initialize()\n", 1)
    return text


def integrate_store(text: str) -> str:
    text = add_import(text, "import com.mora.shield.core.MoraCore", "import kotlin.concurrent.thread\n")
    lines = text.splitlines()
    output = []
    for index, line in enumerate(lines):
        output.append(line)
        if line.strip() == "events = Ps.events(3000)":
            already_added = (
                index + 1 < len(lines)
                and lines[index + 1].strip() == "MoraCore.ingestShieldEvents(events)"
            )
            if not already_added:
                indent = line[:len(line) - len(line.lstrip())]
                output.append(indent + "MoraCore.ingestShieldEvents(events)")
    updated = "\n".join(output)
    return updated + ("\n" if text.endswith("\n") else "")


def integrate_event_service(text: str) -> str:
    text = add_import(text, "import com.mora.shield.core.MoraCore", "import android.os.IBinder\n")
    marker = "        val ev = Ps.parseEv(line) ?: return\n"
    replacement = marker + "        MoraCore.publishShieldEvent(ev)\n"
    if replacement not in text:
        if marker not in text:
            raise SystemExit("Titik event EventService tidak ditemukan")
        text = text.replace(marker, replacement, 1)
    return text


def integrate_tools(text: str) -> str:
    marker = "        KernelCard()\n"
    replacement = marker + "        MoraCoreCard()\n"
    if replacement in text:
        return text
    if marker not in text:
        raise SystemExit("Titik kartu KernelCard tidak ditemukan")
    return text.replace(marker, replacement, 1)


def integrate_manifest(text: str) -> str:
    permission = '''\n    <permission\n        android:name="com.mora.shield.permission.CONNECTOR"\n        android:protectionLevel="signature" />\n'''
    service = '''\n        <service\n            android:name=".connector.MoraConnectorService"\n            android:exported="true"\n            android:permission="com.mora.shield.permission.CONNECTOR">\n            <intent-filter>\n                <action android:name="com.mora.shield.action.CONNECTOR" />\n            </intent-filter>\n        </service>\n'''
    if "com.mora.shield.permission.CONNECTOR" not in text:
        marker = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">"
        if marker not in text:
            raise SystemExit("Root manifest tidak ditemukan")
        text = text.replace(marker, marker + permission, 1)
    if ".connector.MoraConnectorService" not in text:
        marker = "    </application>"
        if marker not in text:
            marker = "</application>"
        if marker not in text:
            raise SystemExit("Penutup application manifest tidak ditemukan")
        text = text.replace(marker, service + marker, 1)
    return text


def integrate_version(text: str) -> str:
    text = text.replace("versionCode = 11", "versionCode = 12")
    text = text.replace('versionName = "3.4.0"', 'versionName = "3.5.0"')
    return text


edit("app/src/main/java/com/mora/shield/MainActivity.kt", integrate_main)
edit("app/src/main/java/com/mora/shield/Store.kt", integrate_store)
edit("app/src/main/java/com/mora/shield/EventService.kt", integrate_event_service)
edit("app/src/main/java/com/mora/shield/ScreenTools.kt", integrate_tools)
edit("app/src/main/AndroidManifest.xml", integrate_manifest)
edit("app/build.gradle.kts", integrate_version)

# Add a short pointer without overwriting the current project README.
readme = PROJECT_ROOT / "README.md"
if readme.is_file():
    old = readme.read_text(encoding="utf-8")
    heading = "## Mora Core & Connector 3.5.0"
    if heading not in old:
        addition = '''\n\n## Mora Core & Connector 3.5.0\n\nMora Core menyediakan event hub lokal (maksimal 500 event dalam memori), registry connector, adapter modul Shield, dan telemetry perangkat. Aplikasi Mora lain dapat mengirim event melalui Messenger IPC yang dilindungi permission signature. API dan batas keamanannya dijelaskan di `docs/MORA_CORE_CONNECTOR_API.md`.\n'''
        readme.write_text(old.rstrip() + addition, encoding="utf-8")
        print("diubah: README.md")

print("Selesai. Jalankan ./gradlew assembleRelease untuk memeriksa build.")
