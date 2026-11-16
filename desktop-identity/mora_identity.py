#!/usr/bin/env python3
"""Mora Shield isolated desktop browser profiles and portable identity-profile exchange."""
import argparse
import json
import os
import re
import shutil
import stat
import sys
import tempfile
import zipfile
from pathlib import Path

DEFAULT_ROOT = Path.home() / ".config/mora-shield/identity-profiles"
BROWSERS = {
    "firefox": ("firefox", "firefox"),
    "librewolf": ("librewolf", "firefox"),
    "zen": ("zen", "firefox"),
    "brave": ("brave-browser", "chromium"),
    "chrome": ("google-chrome", "chromium"),
    "chromium": ("chromium", "chromium"),
}
PLATFORMS = {
    "android": "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
    "iphone": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
    "ipad": "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
    "windows": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "macos": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "linux": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
}
PLATFORM_CODES = {
    "android": "ANDROID", "iphone": "IOS", "ipad": "IOS",
    "windows": "WINDOWS", "macos": "MACOS", "linux": "LINUX",
}
CODE_TO_PLATFORM = {
    "ANDROID": "android", "IOS": "iphone", "WINDOWS": "windows",
    "MACOS": "macos", "LINUX": "linux",
}
DIAGNOSTICS = """<!doctype html><meta charset=utf-8><title>Mora Browser Diagnostics</title>
<h1>Mora Browser Diagnostics</h1><p>Local-only; nothing is uploaded.</p><pre id=o>Loading</pre>
<button id=d>Download JSON</button><script>
(async()=>{let r={time:new Date().toISOString(),userAgent:navigator.userAgent,
clientHints:navigator.userAgentData?{brands:navigator.userAgentData.brands,mobile:navigator.userAgentData.mobile,platform:navigator.userAgentData.platform}:null,
platform:navigator.platform,language:navigator.language,languages:navigator.languages,
timezone:Intl.DateTimeFormat().resolvedOptions().timeZone,
screen:{width:screen.width,height:screen.height,depth:screen.colorDepth,dpr:devicePixelRatio},
webRtc:{peerConnection:typeof RTCPeerConnection!=="undefined",mediaDevices:!!navigator.mediaDevices},webgl:null,canvas:null};
try{let c=document.createElement('canvas'),g=c.getContext('webgl'),e=g&&g.getExtension('WEBGL_debug_renderer_info');
r.webgl=g?{vendor:e?g.getParameter(e.UNMASKED_VENDOR_WEBGL):'masked/unavailable',renderer:e?g.getParameter(e.UNMASKED_RENDERER_WEBGL):'masked/unavailable'}:null}catch(x){r.webgl={error:String(x)}}
try{let c=document.createElement('canvas');c.width=160;c.height=48;let x=c.getContext('2d');x.fillText('Mora privacy test',2,20);let v=c.toDataURL(),h=2166136261;for(let i=0;i<v.length;i++){h^=v.charCodeAt(i);h=Math.imul(h,16777619)}r.canvas={available:true,hash:(h>>>0).toString(16),dataUrlLength:v.length}}catch(x){r.canvas={available:false,error:String(x)}}
window.moraReport=r;o.textContent=JSON.stringify(r,null,2);
d.onclick=()=>{let a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(r,null,2)],{type:'application/json'}));a.download='mora-diagnostic.json';a.click()}})();</script>"""

def safe_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,47}", value):
        raise ValueError("ID hanya boleh berisi huruf, angka, _ dan - (maks. 48 karakter).")
    return value

def profile_dir(root, profile_id):
    return root / safe_id(profile_id)

def _validate_user_agent(user_agent):
    if not isinstance(user_agent, str) or not user_agent or len(user_agent) > 1024:
        raise ValueError("User-Agent tidak valid.")
    if any(ord(char) < 32 or ord(char) == 127 for char in user_agent):
        raise ValueError("User-Agent tidak valid.")
    return user_agent

def create_profile(root, profile_id, browser, platform, user_agent=None, protect_webrtc=True, model=None):
    if browser not in BROWSERS or platform not in PLATFORMS:
        raise ValueError("Browser/platform tidak didukung.")
    ua = _validate_user_agent(user_agent or PLATFORMS[platform])
    target = profile_dir(root, profile_id)
    if target.exists():
        raise FileExistsError("Profil sudah ada; tidak ditimpa.")
    target.mkdir(parents=True)
    engine = BROWSERS[browser][1]
    meta = {
        "schema": 1, "id": profile_id, "browser": browser, "engine": engine,
        "platform_test_label": platform, "model": (model or platform)[:120],
        "user_agent": ua, "webrtc_restriction_requested": bool(protect_webrtc),
        "scope": "isolated browser compatibility profile; not device emulation",
    }
    (target / "profile.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    (target / "diagnostics.html").write_text(DIAGNOSTICS, encoding="utf-8")
    if engine == "firefox":
        prefs = [
            "// Mora isolated test profile; existing profiles are untouched.",
            'user_pref("general.useragent.override", ' + json.dumps(ua) + ');',
        ]
        if protect_webrtc:
            prefs += [
                'user_pref("media.peerconnection.enabled", false);',
                'user_pref("media.navigator.enabled", false);',
            ]
        (target / "user.js").write_text("\n".join(prefs) + "\n", encoding="utf-8")
    else:
        exe = BROWSERS[browser][0]
        args = [exe, "--user-data-dir=" + str(target / "chromium-data"),
                "--user-agent=" + ua, "--no-first-run"]
        if protect_webrtc:
            args.append("--force-webrtc-ip-handling-policy=disable_non_proxied_udp")
        quote = lambda value: "'" + value.replace("'", "'\\''") + "'"
        launcher = target / "launch.sh"
        launcher.write_text("#!/bin/sh\nset -eu\nexec " + " ".join(quote(arg) for arg in args) + ' "$@"\n', encoding="utf-8")
        launcher.chmod(0o700)
    return target

def profiles(root):
    result = []
    for item in sorted(root.glob("*/profile.json")) if root.exists() else []:
        try:
            value = json.loads(item.read_text(encoding="utf-8"))
            if isinstance(value, dict) and safe_id(value.get("id", "")) == item.parent.name:
                result.append(value)
        except (OSError, ValueError, json.JSONDecodeError):
            continue
    return result

def _platform_for_import(code, model, user_agent):
    value = str(code or "").upper()
    if value in CODE_TO_PLATFORM:
        if value == "IOS" and ("ipad" in str(model).lower() or "ipad" in user_agent.lower()):
            return "ipad"
        return CODE_TO_PLATFORM[value]
    aliases = {"iphone": "iphone", "ipad": "ipad", "android": "android",
               "windows": "windows", "macos": "macos", "linux": "linux"}
    return aliases.get(str(code or "").lower())

def _browser_for_import(browser, user_agent):
    raw = str(browser or "").strip().lower().replace(" ", "")
    aliases = {"googlechrome": "chrome", "chromium": "chromium",
               "microsoftedge": "chrome", "edge": "chrome", "safari": "firefox"}
    raw = aliases.get(raw, raw)
    if raw in BROWSERS:
        return raw
    if "firefox/" in user_agent.lower():
        return "firefox"
    if "chrome/" in user_agent.lower() or "chromium" in user_agent.lower():
        return "chrome"
    # Safari is not available as a native Linux browser here. Firefox is only the
    # transport used for compatibility checks; the User-Agent remains user supplied.
    if "safari/" in user_agent.lower():
        return "firefox"
    raise ValueError("Browser asal tidak dikenali; pilih --browser secara eksplisit.")

def export_exchange(root, output):
    records = []
    for item in profiles(root):
        platform = item.get("platform_test_label")
        if platform not in PLATFORM_CODES:
            continue
        records.append({
            "id": item["id"],
            "platform": PLATFORM_CODES[platform],
            "model": item.get("model") or item["id"],
            "userAgent": item["user_agent"],
        })
    payload = {"schema": 1, "profiles": records, "favorites": []}
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, output)
    return len(records)

def import_exchange(root, source, browser_override=None):
    payload = json.loads(Path(source).read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or payload.get("schema") != 1 or not isinstance(payload.get("profiles"), list):
        raise ValueError("Format pertukaran Mora tidak didukung (schema 1 diperlukan).")
    entries = payload["profiles"]
    if len(entries) > 100:
        raise ValueError("Maksimal 100 profil per impor.")
    prepared = []
    ids = set()
    for item in entries:
        if not isinstance(item, dict):
            raise ValueError("Entri profil tidak valid.")
        profile_id = safe_id(item.get("id", ""))
        if profile_id in ids or profile_dir(root, profile_id).exists():
            raise FileExistsError("ID profil duplikat atau sudah ada: " + profile_id)
        ids.add(profile_id)
        ua = _validate_user_agent(item.get("userAgent"))
        model = str(item.get("model") or profile_id)[:120]
        platform = _platform_for_import(item.get("platform"), model, ua)
        if not platform:
            raise ValueError("Platform tidak didukung untuk profil " + profile_id)
        browser = browser_override or _browser_for_import(item.get("browser"), ua)
        if browser not in BROWSERS:
            raise ValueError("Browser tidak didukung: " + str(browser))
        prepared.append((profile_id, browser, platform, ua, model))
    created = []
    try:
        for profile_id, browser, platform, ua, model in prepared:
            create_profile(root, profile_id, browser, platform, ua, model=model)
            created.append(profile_id)
    except Exception:
        for profile_id in created:
            shutil.rmtree(profile_dir(root, profile_id), ignore_errors=True)
        raise
    return created

def compare_reports(before_path, after_path):
    before = json.loads(Path(before_path).read_text(encoding="utf-8"))
    after = json.loads(Path(after_path).read_text(encoding="utf-8"))
    if not isinstance(before, dict) or not isinstance(after, dict):
        raise ValueError("Laporan harus berupa objek JSON.")
    fields = ("userAgent", "clientHints", "platform", "language", "languages",
              "timezone", "screen", "webgl", "canvas", "webRtc")
    changes = []
    for field in fields:
        old = before.get(field)
        new = after.get(field)
        if old != new:
            changes.append({"field": field, "before": old, "after": new})
    return {"changed": len(changes), "changes": changes}

def doctor():
    return {
        name: {"executable": command, "installed": shutil.which(command) is not None,
               "engine": engine}
        for name, (command, engine) in BROWSERS.items()
    }

def _safe_extract_profile(archive, destination, expected_id):
    destination.mkdir(parents=True, exist_ok=False)
    total_size = 0
    with zipfile.ZipFile(archive) as source:
        members = source.infolist()
        if not members or len(members) > 500:
            raise ValueError("Isi backup kosong atau terlalu banyak file.")
        for member in members:
            name = member.filename.replace("\\", "/")
            parts = Path(name).parts
            mode = (member.external_attr >> 16) & 0xFFFF
            if name.startswith("/") or ".." in parts or stat.S_ISLNK(mode):
                raise ValueError("Arsip mengandung path atau link yang tidak aman.")
            total_size += member.file_size
            if total_size > 50 * 1024 * 1024:
                raise ValueError("Ukuran hasil ekstraksi melebihi 50 MB.")
            target = destination.joinpath(*parts)
            if member.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(member) as input_file, target.open("wb") as output_file:
                    shutil.copyfileobj(input_file, output_file)
    metadata = destination / "profile.json"
    if not metadata.is_file():
        raise ValueError("Backup tidak berisi profile.json.")
    value = json.loads(metadata.read_text(encoding="utf-8"))
    if value.get("id") != expected_id or value.get("browser") not in BROWSERS or value.get("platform_test_label") not in PLATFORMS:
        raise ValueError("Metadata backup tidak cocok atau tidak valid.")
    _validate_user_agent(value.get("user_agent"))
    return value

def backup_profile(root, profile_id, output):
    destination = profile_dir(root, profile_id)
    if not (destination / "profile.json").is_file():
        raise FileNotFoundError("Profil tidak ditemukan.")
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    archive = Path(shutil.make_archive(str(output.with_suffix("")), "zip", destination))
    rollback_dir = root / ".rollback"
    rollback_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(archive, rollback_dir / (safe_id(profile_id) + ".zip"))
    return archive

def restore_profile(root, profile_id, archive):
    destination = profile_dir(root, profile_id)
    if destination.exists():
        raise FileExistsError("Target sudah ada; tidak ditimpa.")
    root.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".mora-restore-", dir=root))
    staging.rmdir()
    try:
        _safe_extract_profile(archive, staging, safe_id(profile_id))
        os.replace(staging, destination)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return destination

def rollback_profile(root, profile_id):
    profile_id = safe_id(profile_id)
    archive = root / ".rollback" / (profile_id + ".zip")
    if not archive.is_file():
        raise FileNotFoundError("Belum ada backup terakhir untuk profil ini. Jalankan backup dulu.")
    destination = profile_dir(root, profile_id)
    if destination.exists():
        raise FileExistsError("Target aktif ada. Pindahkan atau backup profil aktif sebelum rollback.")
    return restore_profile(root, profile_id, archive)

def main(argv=None):
    parser = argparse.ArgumentParser(description="Mora Shield isolated desktop browser profiles")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest="cmd", required=True)
    commands.add_parser("list")
    commands.add_parser("doctor")
    create = commands.add_parser("create")
    create.add_argument("--id", required=True)
    create.add_argument("--browser", choices=BROWSERS, required=True)
    create.add_argument("--platform", choices=PLATFORMS, required=True)
    create.add_argument("--model")
    create.add_argument("--user-agent")
    create.add_argument("--allow-webrtc", action="store_true")
    launch = commands.add_parser("launch")
    launch.add_argument("--id", required=True)
    backup = commands.add_parser("backup")
    backup.add_argument("--id", required=True)
    backup.add_argument("--output", type=Path, required=True)
    restore = commands.add_parser("restore")
    restore.add_argument("--id", required=True)
    restore.add_argument("--archive", type=Path, required=True)
    rollback = commands.add_parser("rollback")
    rollback.add_argument("--id", required=True)
    export = commands.add_parser("export")
    export.add_argument("--output", type=Path, required=True)
    imp = commands.add_parser("import")
    imp.add_argument("--input", type=Path, required=True)
    imp.add_argument("--browser", choices=BROWSERS)
    compare = commands.add_parser("compare")
    compare.add_argument("--before", type=Path, required=True)
    compare.add_argument("--after", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.cmd == "list":
            print(json.dumps(profiles(args.root), indent=2))
        elif args.cmd == "doctor":
            print(json.dumps(doctor(), indent=2))
        elif args.cmd == "create":
            dest = create_profile(args.root, args.id, args.browser, args.platform,
                                  args.user_agent, not args.allow_webrtc, args.model)
            print("Created:", dest)
        elif args.cmd == "launch":
            dest = profile_dir(args.root, args.id)
            metadata = json.loads((dest / "profile.json").read_text(encoding="utf-8"))
            if metadata["engine"] == "firefox":
                command = [BROWSERS[metadata["browser"]][0], "-no-remote", "-profile", str(dest)]
            else:
                command = [str(dest / "launch.sh")]
            os.execvp(command[0], command)
        elif args.cmd == "backup":
            print("Backup:", backup_profile(args.root, args.id, args.output))
        elif args.cmd == "restore":
            print("Restored:", restore_profile(args.root, args.id, args.archive))
        elif args.cmd == "rollback":
            print("Restored last saved backup:", rollback_profile(args.root, args.id))
        elif args.cmd == "export":
            print("Exported profiles:", export_exchange(args.root, args.output))
        elif args.cmd == "import":
            print("Imported profiles:", ", ".join(import_exchange(args.root, args.input, args.browser)))
        elif args.cmd == "compare":
            print(json.dumps(compare_reports(args.before, args.after), indent=2))
        return 0
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        print("mora-identity:", error, file=sys.stderr)
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
