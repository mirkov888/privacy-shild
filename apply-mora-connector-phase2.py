#!/usr/bin/env python3
"""Install Mora Connector SDK v1 host integration into a Phase-1 Mora Shield project.

Idempotent. Replaces only Core-owned files that gain Phase-2 APIs, adds the host
service and SDK source package, then performs narrowly scoped manifest/version edits.
"""
from pathlib import Path
import shutil
import sys
import re

SOURCE_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else Path.cwd().resolve()

REQUIRED = [
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/mora/shield/core/CoreDatabase.kt",
    "app/src/main/java/com/mora/shield/core/CommandRouter.kt",
    "app/src/main/java/com/mora/shield/core/PolicyEngine.kt",
    "app/src/main/java/com/mora/shield/core/MoraCore.kt",
]
missing = [path for path in REQUIRED if not (PROJECT_ROOT / path).is_file()]
if missing:
    raise SystemExit(
        "Mora Shield Phase 1 belum terpasang atau project tidak lengkap. File hilang:\n"
        + "\n".join(missing)
    )

COPY_FILES = [
    "app/src/main/java/com/mora/shield/core/CoreDatabase.kt",
    "app/src/main/java/com/mora/shield/core/CommandRouter.kt",
    "app/src/main/java/com/mora/shield/core/PolicyEngine.kt",
    "app/src/main/java/com/mora/shield/core/MoraCore.kt",
    "app/src/main/java/com/mora/shield/core/AppRegistry.kt",
    "app/src/main/java/com/mora/shield/connector/ConnectorSecurity.kt",
    "app/src/main/java/com/mora/shield/connector/MoraConnectorService.kt",
    "docs/MORA_CONNECTOR_PHASE2.md",
]
for relative in COPY_FILES:
    source = SOURCE_ROOT / relative
    target = PROJECT_ROOT / relative
    if not source.is_file():
        raise SystemExit(f"File patch tidak ditemukan: {source}")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.read_bytes() == source.read_bytes():
        print(f"tidak berubah: {relative}")
    else:
        shutil.copy2(source, target)
        print(f"dipasang: {relative}")

# The portable SDK bundle is kept at repository root for copying into worker APKs.
sdk_source = SOURCE_ROOT / "sdk"
sdk_target = PROJECT_ROOT / "connector-sdk"
for source in sdk_source.rglob("*"):
    if source.is_dir():
        continue
    relative = source.relative_to(sdk_source)
    target = sdk_target / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.read_bytes() == source.read_bytes():
        continue
    shutil.copy2(source, target)
    print(f"dipasang: connector-sdk/{relative.as_posix()}")
    if relative.parts[:3] == ("src", "main", "java"):
        app_target = PROJECT_ROOT / "app/src/main/java" / Path(*relative.parts[3:])
        app_target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, app_target)
        print(f"dipasang: app/src/main/java/{Path(*relative.parts[3:]).as_posix()}")


def edit(relative: str, transform) -> None:
    path = PROJECT_ROOT / relative
    original = path.read_text(encoding="utf-8")
    updated = transform(original)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        print(f"diubah: {relative}")
    else:
        print(f"sudah terintegrasi: {relative}")


def integrate_manifest(text: str) -> str:
    permission_name = 'android:name="com.mora.shield.permission.CONNECTOR"'
    if permission_name not in text:
        permission = '''\n    <permission
        android:name="com.mora.shield.permission.CONNECTOR"
        android:protectionLevel="signature"
        android:label="Mora ecosystem connector" />\n'''
        anchor = "<application"
        if anchor not in text:
            raise SystemExit("Tag <application> tidak ditemukan pada AndroidManifest.xml")
        text = text.replace(anchor, permission + "\n    " + anchor, 1)

    service_name = 'android:name=".connector.MoraConnectorService"'
    if service_name not in text:
        service = '''
        <service
            android:name=".connector.MoraConnectorService"
            android:exported="true"
            android:permission="com.mora.shield.permission.CONNECTOR">
            <intent-filter>
                <action android:name="com.mora.shield.action.CONNECTOR" />
            </intent-filter>
        </service>
'''
        closing = "</application>"
        if closing not in text:
            raise SystemExit("Tag </application> tidak ditemukan")
        text = text.replace(closing, service + "\n    " + closing, 1)
    return text


def bump_version(text: str) -> str:
    if 'versionName = "3.5.0"' in text:
        text = text.replace('versionName = "3.5.0"', 'versionName = "3.6.0"', 1)
    if re.search(r"versionCode\s*=\s*12\b", text):
        text = re.sub(r"versionCode\s*=\s*12\b", "versionCode = 13", text, count=1)
    return text


def append_readme(text: str) -> str:
    heading = "# Mora Connector SDK v1 — Phase 2"
    if heading in text:
        return text
    docs = (SOURCE_ROOT / "docs/MORA_CONNECTOR_PHASE2.md").read_text(encoding="utf-8")
    return text.rstrip() + "\n\n" + docs

edit("app/src/main/AndroidManifest.xml", integrate_manifest)
edit("app/build.gradle.kts", bump_version)
readme = PROJECT_ROOT / "README.md"
if readme.exists():
    edit("README.md", append_readme)
else:
    shutil.copy2(SOURCE_ROOT / "README.md", readme)
    print("dibuat: README.md")

print("\nMora Connector SDK v1 host integration terpasang.")
print("Copy SDK dari connector-sdk/src/main/java/com/mora/connector/sdk ke masing-masing companion APK.")
print("Build Shield: ./gradlew assembleRelease --no-daemon")
