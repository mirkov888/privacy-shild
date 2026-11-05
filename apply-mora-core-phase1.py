#!/usr/bin/env python3
"""Install Mora Core Phase 1 into an existing Mora Shield Android project.

Safe to rerun. It writes only the listed Core files and makes idempotent edits
around known integration points; it does not overwrite unrelated source files.
"""
from pathlib import Path
import re
import shutil
import sys

SOURCE_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else Path.cwd().resolve()

REQUIRED = [
    "app/build.gradle.kts",
    "build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/mora/shield/ScreenTools.kt",
    "app/src/main/java/com/mora/shield/EventService.kt",
    "app/src/main/java/com/mora/shield/Store.kt",
]
missing = [p for p in REQUIRED if not (PROJECT_ROOT / p).is_file()]
if missing:
    raise SystemExit("Project Mora Shield tidak lengkap. File tidak ditemukan:\n" + "\n".join(missing))

SOURCE_FILES = [
    "app/src/main/java/com/mora/shield/core/CoreDatabase.kt",
    "app/src/main/java/com/mora/shield/core/CoreModels.kt",
    "app/src/main/java/com/mora/shield/core/CoreSanitizer.kt",
    "app/src/main/java/com/mora/shield/core/AppRegistry.kt",
    "app/src/main/java/com/mora/shield/core/PolicyEngine.kt",
    "app/src/main/java/com/mora/shield/core/EventHub.kt",
    "app/src/main/java/com/mora/shield/core/CommandRouter.kt",
    "app/src/main/java/com/mora/shield/core/MoraCore.kt",
    "app/src/main/java/com/mora/shield/MoraShieldApplication.kt",
    "app/src/main/java/com/mora/shield/MoraCoreCard.kt",
    "docs/MORA_CORE_PHASE1.md",
]
for relative in SOURCE_FILES:
    src = SOURCE_ROOT / relative
    dst = PROJECT_ROOT / relative
    if not src.is_file():
        raise SystemExit(f"File patch hilang: {src}")
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.is_file() and dst.read_bytes() == src.read_bytes():
        print(f"tidak berubah: {relative}")
    else:
        shutil.copy2(src, dst)
        print(f"dipasang: {relative}")


def edit(relative: str, transform) -> None:
    path = PROJECT_ROOT / relative
    original = path.read_text(encoding="utf-8")
    updated = transform(original)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"diubah: {relative}")
    else:
        print(f"sudah terintegrasi: {relative}")


def integrate_root_gradle(text: str) -> str:
    plugin = '    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false'
    if 'id("org.jetbrains.kotlin.kapt")' not in text:
        anchor = '    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false'
        if anchor not in text:
            raise SystemExit("Plugin compose Kotlin tidak ditemukan di root build.gradle.kts")
        text = text.replace(anchor, anchor + "\n" + plugin, 1)
    return text


def integrate_app_gradle(text: str) -> str:
    if 'id("org.jetbrains.kotlin.kapt")' not in text:
        anchor = '    id("org.jetbrains.kotlin.plugin.compose")'
        if anchor not in text:
            raise SystemExit("Plugin compose tidak ditemukan di app/build.gradle.kts")
        text = text.replace(anchor, anchor + '\n    id("org.jetbrains.kotlin.kapt")', 1)

    # Keep current app coordinates; only move the known 3.4 -> 3.5 milestone.
    text = text.replace("versionCode = 11", "versionCode = 12")
    text = text.replace('versionName = "3.4.0"', 'versionName = "3.5.0"')

    deps = [
        '    implementation("androidx.room:room-runtime:2.6.1")',
        '    implementation("androidx.room:room-ktx:2.6.1")',
        '    kapt("androidx.room:room-compiler:2.6.1")',
        '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")',
    ]
    for dep in deps:
        if dep not in text:
            marker = "dependencies {"
            start = text.find(marker)
            if start < 0:
                raise SystemExit("Blok dependencies tidak ditemukan")
            end = text.find("\n}", start)
            if end < 0:
                raise SystemExit("Penutup blok dependencies tidak ditemukan")
            text = text[:end] + "\n" + dep + text[end:]
    return text


def integrate_manifest(text: str) -> str:
    # Remove the early prototype's exported IPC service. Phase 2 will add the
    # authenticated Messenger service after certificate pinning is implemented.
    text = re.sub(
        r'\s*<permission\b(?=[^>]*com\.mora\.shield\.permission\.CONNECTOR)[^>]*/>\s*',
        "\n",
        text,
        flags=re.DOTALL,
    )
    text = re.sub(
        r'\s*<service\b(?=[^>]*\.connector\.MoraConnectorService)[\s\S]*?</service>\s*',
        "\n",
        text,
        flags=re.DOTALL,
    )
    if 'android:name=".MoraShieldApplication"' not in text:
        if "<application" not in text:
            raise SystemExit("Tag application tidak ditemukan di manifest")
        text = text.replace("<application", '<application android:name=".MoraShieldApplication"', 1)
    return text


def integrate_event_service(text: str) -> str:
    if "import com.mora.shield.core.MoraCore" not in text:
        anchor = "import android.os.IBinder\n"
        if anchor not in text:
            raise SystemExit("Anchor import EventService tidak ditemukan")
        text = text.replace(anchor, anchor + "import com.mora.shield.core.MoraCore\n", 1)
    marker = "        val ev = Ps.parseEv(line) ?: return\n"
    if marker not in text:
        raise SystemExit("Parser event EventService tidak ditemukan")
    if marker + "        MoraCore.publishShieldEvent(ev)\n" not in text:
        text = text.replace(marker, marker + "        MoraCore.publishShieldEvent(ev)\n", 1)
    return text


def integrate_store(text: str) -> str:
    if "import com.mora.shield.core.MoraCore" not in text:
        anchor = "import kotlin.concurrent.thread\n"
        if anchor not in text:
            raise SystemExit("Anchor import Store tidak ditemukan")
        text = text.replace(anchor, anchor + "import com.mora.shield.core.MoraCore\n", 1)
    lines = text.splitlines()
    out = []
    for i, line in enumerate(lines):
        out.append(line)
        if line.strip() == "events = Ps.events(3000)":
            next_line = lines[i + 1].strip() if i + 1 < len(lines) else ""
            if next_line != "MoraCore.ingestShieldEvents(events)":
                indent = line[:len(line) - len(line.lstrip())]
                out.append(indent + "MoraCore.ingestShieldEvents(events)")
    result = "\n".join(out)
    return result + ("\n" if text.endswith("\n") else "")


def integrate_tools(text: str) -> str:
    if "MoraCoreCard()" in text:
        return text
    pattern = re.compile(r"(?m)^(\s*)LinkCard\(\)\s*$")
    match = pattern.search(text)
    if not match:
        raise SystemExit("Titik LinkCard di ScreenTools.kt tidak ditemukan")
    indent = match.group(1)
    return text[:match.end()] + "\n" + indent + "MoraCoreCard()" + text[match.end():]


edit("build.gradle.kts", integrate_root_gradle)
edit("app/build.gradle.kts", integrate_app_gradle)
edit("app/src/main/AndroidManifest.xml", integrate_manifest)
edit("app/src/main/java/com/mora/shield/EventService.kt", integrate_event_service)
edit("app/src/main/java/com/mora/shield/Store.kt", integrate_store)
edit("app/src/main/java/com/mora/shield/ScreenTools.kt", integrate_tools)

# Upgrade the old in-memory prototype if it was already installed from the earlier patch.
old_files = [
    "app/src/main/java/com/mora/shield/core/ConnectorContracts.kt",
    "app/src/main/java/com/mora/shield/connector/MoraConnectorContract.kt",
    "app/src/main/java/com/mora/shield/connector/MoraConnectorService.kt",
    "app/src/main/java/com/mora/shield/connector/MoraConnectorClient.kt",
    "app/src/main/java/com/mora/shield/connector/ShieldModuleConnector.kt",
]
for relative in old_files:
    path = PROJECT_ROOT / relative
    if path.exists():
        path.unlink()
        print(f"dihapus (prototype lama digantikan): {relative}")

# Remove the old no-argument initialization call if the previous prototype was applied.
main_activity = PROJECT_ROOT / "app/src/main/java/com/mora/shield/MainActivity.kt"
if main_activity.is_file():
    old = main_activity.read_text(encoding="utf-8")
    new = old.replace("MoraCore.initialize()", "MoraCore.initialize(applicationContext)")
    if old != new:
        main_activity.write_text(new, encoding="utf-8")
        print("diubah: MainActivity.kt (kompatibilitas prototype lama)")

readme = PROJECT_ROOT / "README.md"
if readme.is_file():
    old = readme.read_text(encoding="utf-8")
    heading = "## Mora Core Phase 1"
    if heading not in old:
        readme.write_text(old.rstrip() + "\n\n" + (SOURCE_ROOT / "docs/MORA_CORE_PHASE1.md").read_text(encoding="utf-8"), encoding="utf-8")
        print("diubah: README.md")

print("\nMora Core Phase 1 terpasang. Jalankan ./gradlew assembleRelease untuk validasi APK.")
