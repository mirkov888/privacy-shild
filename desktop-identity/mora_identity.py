#!/usr/bin/env python3
"""Mora Shield isolated desktop browser profiles and portable identity-profile exchange."""
import argparse
import json
import os
import re
import shutil
import stat
import sys
import subprocess
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

def create_profile(root, profile_id, browser, platform, user_agent=None, protect_webrtc=True, model=None, protect_canvas=False, protect_webgl=False):
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
        "protection": {
            "canvas": {
                "requested": bool(protect_canvas),
                "applied": bool(protect_canvas and engine == "firefox"),
                "method": "Firefox privacy.resistFingerprinting" if protect_canvas and engine == "firefox" else
                          ("not available through Chromium startup flags" if protect_canvas else "disabled"),
            },
            "webgl": {
                "requested": bool(protect_webgl),
                "applied": bool(protect_webgl),
                "method": "disable WebGL API" if protect_webgl else "disabled",
            },
            "webrtc": {
                "requested": bool(protect_webrtc),
                "applied": bool(protect_webrtc),
                "method": "disable peer connection API" if protect_webrtc and engine == "firefox" else
                          ("restrict non-proxied UDP; not a VPN" if protect_webrtc else "disabled"),
            },
        },
        "scope": "isolated browser compatibility profile; not device emulation",
    }
    (target / "profile.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    (target / "diagnostics.html").write_text(DIAGNOSTICS, encoding="utf-8")
    if engine == "firefox":
        prefs = [
            "// Mora isolated test profile; existing profiles are untouched.",
            'user_pref("general.useragent.override", ' + json.dumps(ua) + ');',
        ]
        if protect_canvas:
            prefs.append('user_pref("privacy.resistFingerprinting", true);')
        if protect_webgl:
            prefs.append('user_pref("webgl.disabled", true);')
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
        if protect_webgl:
            args.append("--disable-webgl")
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

def protection_capabilities(browser):
    if browser not in BROWSERS:
        raise ValueError("Browser tidak didukung.")
    engine = BROWSERS[browser][1]
    if engine == "firefox":
        return {
            "canvas": "available: privacy.resistFingerprinting (also changes some web-visible values)",
            "webgl": "available: disable WebGL API (may break 3D sites)",
            "webrtc": "available: disable peer-connection API",
        }
    return {
        "canvas": "not available through browser startup flags",
        "webgl": "available: disable WebGL API (may break 3D sites)",
        "webrtc": "best effort: restrict non-proxied UDP; not a VPN or IP-leak guarantee",
    }


def diagnostics_uri(root, profile_id):
    page = profile_dir(root, profile_id) / "diagnostics.html"
    if not page.is_file():
        raise FileNotFoundError("Halaman diagnostik profil tidak ditemukan.")
    return page.resolve().as_uri()


def audit_report(report):
    if not isinstance(report, dict):
        raise ValueError("Laporan diagnostik harus berupa objek JSON.")
    user_agent = str(report.get("userAgent") or "")
    hints = report.get("clientHints")
    ua_platform = None
    ua_lower = user_agent.lower()
    if "android" in ua_lower:
        ua_platform = "android"
    elif "iphone" in ua_lower or "ipad" in ua_lower:
        ua_platform = "ios"
    elif "windows nt" in ua_lower:
        ua_platform = "windows"
    elif "macintosh" in ua_lower:
        ua_platform = "macos"
    elif "linux" in ua_lower:
        ua_platform = "linux"
    hint_platform = str(hints.get("platform") or "").lower() if isinstance(hints, dict) else ""
    checks = [
        {"surface": "userAgent", "status": "visible" if user_agent else "missing",
         "detail": "User-Agent " + ("tersedia" if user_agent else "tidak dilaporkan")},
    ]
    if not isinstance(hints, dict):
        checks.append({"surface": "clientHints", "status": "not_supported",
                       "detail": "Client Hints tidak dilaporkan oleh browser/profil ini"})
    elif ua_platform and hint_platform and ua_platform != hint_platform:
        checks.append({"surface": "clientHints", "status": "mismatch",
                       "detail": "Platform UA=" + ua_platform + " tetapi CH=" + hint_platform})
    else:
        checks.append({"surface": "clientHints", "status": "visible",
                       "detail": "Client Hints tersedia; platform=" + (hint_platform or "tidak diketahui")})
    canvas = report.get("canvas") if isinstance(report.get("canvas"), dict) else {}
    checks.append({"surface": "canvas", "status": "visible" if canvas.get("available") else "unavailable",
                   "detail": "Canvas readback tersedia" if canvas.get("available") else "Canvas readback tidak tersedia"})
    webgl = report.get("webgl") if isinstance(report.get("webgl"), dict) else {}
    checks.append({"surface": "webgl", "status": "visible" if webgl.get("available") else "unavailable",
                   "detail": "WebGL API tersedia" if webgl.get("available") else "WebGL API tidak tersedia"})
    rtc = report.get("webRtc") if isinstance(report.get("webRtc"), dict) else {}
    checks.append({"surface": "webRtc", "status": "visible" if rtc.get("peerConnection") else "unavailable",
                   "detail": "RTCPeerConnection tersedia; ini bukan bukti IP bocor" if rtc.get("peerConnection") else "RTCPeerConnection tidak tersedia"})
    for field in ("timezone", "language", "languages", "screen"):
        present = field in report and report.get(field) not in (None, "", [])
        checks.append({"surface": field, "status": "reported" if present else "missing",
                       "detail": "Informasi " + field + (" dilaporkan" if present else " tidak dilaporkan")})
    return {
        "summary": {
            "checks": len(checks),
            "mismatches": sum(item["status"] == "mismatch" for item in checks),
            "visible_apis": sum(item["status"] == "visible" for item in checks),
            "missing_or_unavailable": sum(item["status"] in ("missing", "unavailable", "not_supported") for item in checks),
        },
        "checks": checks,
        "note": "Pemeriksaan API/metadata lokal; tidak menguji IP publik, DNS, VPN, atau rute jaringan.",
    }


def doctor():
    return {
        name: {
            "executable": command,
            "installed": shutil.which(command) is not None,
            "engine": engine,
            "protection": protection_capabilities(name),
        }
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
    rollback_dir = root / ".rollback"
    archive = rollback_dir / (profile_id + ".zip")
    if not archive.is_file():
        raise FileNotFoundError("Belum ada backup terakhir untuk profil ini. Jalankan backup dulu.")
    destination = profile_dir(root, profile_id)
    staging = Path(tempfile.mkdtemp(prefix=".mora-rollback-", dir=root))
    staging.rmdir()
    rotated = rollback_dir / (profile_id + ".pre-rollback")
    current_archive = rollback_dir / (profile_id + ".pre-rollback.zip")
    try:
        _safe_extract_profile(archive, staging, profile_id)
        if destination.exists():
            if current_archive.exists():
                current_archive.unlink()
            shutil.make_archive(str(current_archive.with_suffix("")), "zip", destination)
            if rotated.exists():
                shutil.rmtree(rotated)
            os.replace(destination, rotated)
        try:
            os.replace(staging, destination)
        except Exception:
            if rotated.exists() and not destination.exists():
                os.replace(rotated, destination)
            raise
        return destination
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise



def launch_profile(root, profile_id, diagnostics=False):
    destination = profile_dir(root, profile_id)
    metadata_path = destination / "profile.json"
    if not metadata_path.is_file():
        raise FileNotFoundError("Profil tidak ditemukan.")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    browser = metadata.get("browser")
    if browser not in BROWSERS:
        raise ValueError("Browser pada profil tidak didukung.")
    if metadata.get("engine") == "firefox":
        command = [BROWSERS[browser][0], "-no-remote", "-profile", str(destination)]
    else:
        launcher = destination / "launch.sh"
        if not launcher.is_file():
            raise FileNotFoundError("Launcher profil Chromium tidak ditemukan.")
        command = [str(launcher)]
    if diagnostics:
        command.append(diagnostics_uri(root, profile_id))
    executable = shutil.which(command[0]) if not Path(command[0]).is_absolute() else command[0]
    if not executable:
        raise FileNotFoundError("Browser belum terpasang atau executable tidak ditemukan: " + command[0])
    command[0] = executable
    return subprocess.Popen(command, cwd=str(destination), start_new_session=True)


def open_desktop_dashboard(root):
    """Launch a lightweight Tk dashboard; all browser data stays in Mora's profile root."""
    try:
        import tkinter as tk
        from tkinter import filedialog, messagebox, ttk
    except ImportError as error:
        raise RuntimeError("Tkinter tidak tersedia. Pasang paket python3-tk lalu coba lagi.") from error

    root = Path(root).expanduser()
    root.mkdir(parents=True, exist_ok=True)
    window = tk.Tk()
    window.title("Mora Shield — Identity Studio")
    window.geometry("940x700")
    window.minsize(760, 560)

    style = ttk.Style(window)
    if "clam" in style.theme_names():
        style.theme_use("clam")

    wrapper = ttk.Frame(window, padding=14)
    wrapper.pack(fill="both", expand=True)
    ttk.Label(wrapper, text="Mora Identity Studio", font=("TkDefaultFont", 17, "bold")).pack(anchor="w")
    ttk.Label(
        wrapper,
        text="Dashboard Linux · isolated profiles · no changes to normal browser profiles",
        foreground="#666666",
    ).pack(anchor="w", pady=(2, 10))

    form = ttk.LabelFrame(wrapper, text="Create compatibility profile", padding=10)
    form.pack(fill="x", pady=(0, 10))
    variables = {
        "id": tk.StringVar(value="my-profile"),
        "model": tk.StringVar(value="Custom compatibility profile"),
        "browser": tk.StringVar(value="firefox"),
        "platform": tk.StringVar(value="linux"),
    }
    ttk.Label(form, text="Profile ID").grid(row=0, column=0, sticky="w")
    ttk.Entry(form, textvariable=variables["id"], width=24).grid(row=1, column=0, sticky="ew", padx=(0, 8))
    ttk.Label(form, text="Model / label").grid(row=0, column=1, sticky="w")
    ttk.Entry(form, textvariable=variables["model"], width=30).grid(row=1, column=1, sticky="ew", padx=(0, 8))
    ttk.Label(form, text="Browser").grid(row=0, column=2, sticky="w")
    ttk.Combobox(form, textvariable=variables["browser"], values=tuple(BROWSERS), state="readonly", width=13).grid(row=1, column=2, sticky="ew", padx=(0, 8))
    ttk.Label(form, text="Test platform").grid(row=0, column=3, sticky="w")
    ttk.Combobox(form, textvariable=variables["platform"], values=tuple(PLATFORMS), state="readonly", width=12).grid(row=1, column=3, sticky="ew")
    for column in range(4):
        form.columnconfigure(column, weight=1)

    protect_canvas = tk.BooleanVar(value=False)
    protect_webgl = tk.BooleanVar(value=False)
    protect_webrtc = tk.BooleanVar(value=True)
    show_diagnostics = tk.BooleanVar(value=True)
    options = ttk.Frame(form)
    options.grid(row=2, column=0, columnspan=4, sticky="w", pady=(8, 0))
    ttk.Checkbutton(options, text="Canvas protection (Firefox only)", variable=protect_canvas).pack(side="left", padx=(0, 12))
    ttk.Checkbutton(options, text="Disable WebGL", variable=protect_webgl).pack(side="left", padx=(0, 12))
    ttk.Checkbutton(options, text="Restrict WebRTC", variable=protect_webrtc).pack(side="left", padx=(0, 12))
    ttk.Checkbutton(options, text="Open diagnostics on launch", variable=show_diagnostics).pack(side="left")

    split = ttk.Panedwindow(wrapper, orient="horizontal")
    split.pack(fill="both", expand=True)
    left = ttk.Frame(split, padding=(0, 0, 10, 0))
    right = ttk.Frame(split)
    split.add(left, weight=1)
    split.add(right, weight=2)
    ttk.Label(left, text="Mora profiles", font=("TkDefaultFont", 11, "bold")).pack(anchor="w")
    profile_list = tk.Listbox(left, height=20, exportselection=False)
    profile_list.pack(fill="both", expand=True, pady=(6, 8))
    output = tk.Text(right, height=16, wrap="word", state="disabled")
    output.pack(fill="both", expand=True)
    status = tk.StringVar(value="Ready")
    ttk.Label(wrapper, textvariable=status, relief="sunken", anchor="w").pack(fill="x", pady=(8, 0))

    selected_ids = []

    def log(message):
        output.configure(state="normal")
        output.insert("end", str(message) + "\n")
        output.see("end")
        output.configure(state="disabled")
        status.set(str(message).splitlines()[0][:160])

    def selected_id():
        selection = profile_list.curselection()
        if not selection:
            raise ValueError("Pilih profil dari daftar terlebih dahulu.")
        return selected_ids[selection[0]]

    def refresh():
        nonlocal selected_ids
        profile_list.delete(0, "end")
        selected_ids = []
        for item in profiles(root):
            profile_id = item["id"]
            selected_ids.append(profile_id)
            label = "{} · {} · {} · {}".format(
                item.get("model", profile_id),
                item.get("browser", "?"),
                item.get("platform_test_label", "?"),
                profile_id,
            )
            profile_list.insert("end", label)
        status.set("{} isolated profile(s)".format(len(selected_ids)))

    def action(callback):
        try:
            callback()
        except Exception as error:
            log("ERROR: " + str(error))
            messagebox.showerror("Mora Identity Studio", str(error), parent=window)

    def create():
        destination = create_profile(
            root,
            variables["id"].get().strip(),
            variables["browser"].get(),
            variables["platform"].get(),
            model=variables["model"].get().strip(),
            protect_webrtc=protect_webrtc.get(),
            protect_canvas=protect_canvas.get(),
            protect_webgl=protect_webgl.get(),
        )
        refresh()
        log("Created isolated profile: " + str(destination))

    def launch():
        profile_id = selected_id()
        process = launch_profile(root, profile_id, diagnostics=show_diagnostics.get())
        log("Started {} (PID {}). Normal browser profile untouched.".format(profile_id, process.pid))

    def backup():
        profile_id = selected_id()
        target = filedialog.asksaveasfilename(
            parent=window, title="Save profile backup",
            initialfile=profile_id + ".zip", defaultextension=".zip",
            filetypes=[("ZIP archive", "*.zip")],
        )
        if target:
            archive = backup_profile(root, profile_id, target)
            log("Backup saved: " + str(archive))

    def rollback():
        profile_id = selected_id()
        if not messagebox.askyesno("Confirm rollback", "Restore the last saved backup for " + profile_id + "?", parent=window):
            return
        destination = rollback_profile(root, profile_id)
        refresh()
        log("Rollback completed: " + str(destination))

    def export_profiles():
        target = filedialog.asksaveasfilename(
            parent=window, title="Export profile exchange",
            initialfile="mora-profiles.json", defaultextension=".json",
            filetypes=[("JSON", "*.json")],
        )
        if target:
            count = export_exchange(root, target)
            log("Exported {} profile(s): {}".format(count, target))

    def import_profiles():
        source = filedialog.askopenfilename(
            parent=window, title="Import profile exchange",
            filetypes=[("JSON", "*.json")],
        )
        if source:
            created = import_exchange(root, source)
            refresh()
            log("Imported profile(s): " + (", ".join(created) if created else "none"))

    def show_doctor():
        report = doctor()
        lines = []
        for name, info in report.items():
            lines.append("{}: {}".format(name, "installed" if info["installed"] else "not found"))
            for surface, capability in info["protection"].items():
                lines.append("  {}: {}".format(surface, capability))
        log("\n".join(lines))

    def audit():
        source = filedialog.askopenfilename(
            parent=window, title="Open diagnostic JSON",
            filetypes=[("JSON", "*.json")],
        )
        if source:
            report = json.loads(Path(source).read_text(encoding="utf-8"))
            result = audit_report(report)
            log(json.dumps(result, indent=2, ensure_ascii=False))

    actions = ttk.Frame(left)
    actions.pack(fill="x")
    ttk.Button(actions, text="Refresh", command=lambda: action(refresh)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Create profile", command=lambda: action(create)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Launch selected", command=lambda: action(launch)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Backup selected", command=lambda: action(backup)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Rollback selected", command=lambda: action(rollback)).pack(fill="x", pady=2)
    ttk.Separator(actions).pack(fill="x", pady=6)
    ttk.Button(actions, text="Import JSON", command=lambda: action(import_profiles)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Export JSON", command=lambda: action(export_profiles)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Browser doctor", command=lambda: action(show_doctor)).pack(fill="x", pady=2)
    ttk.Button(actions, text="Audit report JSON", command=lambda: action(audit)).pack(fill="x", pady=2)
    refresh()
    log("Profile root: " + str(root))
    log("Tip: Canvas protection uses Firefox's built-in privacy.resistFingerprinting setting; Chromium reports it unsupported.")
    window.mainloop()


def main(argv=None):
    parser = argparse.ArgumentParser(description="Mora Shield isolated desktop browser profiles")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest="cmd", required=True)
    commands.add_parser("list")
    commands.add_parser("gui", help="open the lightweight Linux dashboard")
    commands.add_parser("doctor")
    create = commands.add_parser("create")
    create.add_argument("--id", required=True)
    create.add_argument("--browser", choices=BROWSERS, required=True)
    create.add_argument("--platform", choices=PLATFORMS, required=True)
    create.add_argument("--model")
    create.add_argument("--user-agent")
    create.add_argument("--allow-webrtc", action="store_true")
    create.add_argument("--protect-canvas", action="store_true",
                        help="aktifkan anti-canvas bawaan Firefox (Chromium tidak didukung)")
    create.add_argument("--protect-webgl", action="store_true",
                        help="nonaktifkan WebGL API; situs 3D dapat terganggu")
    launch = commands.add_parser("launch")
    launch.add_argument("--id", required=True)
    launch.add_argument("--diagnostics", action="store_true",
                        help="buka halaman diagnostik lokal di profil terisolasi")
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
    audit = commands.add_parser("audit")
    audit.add_argument("--input", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.cmd == "gui":
            open_desktop_dashboard(args.root)
        elif args.cmd == "list":
            print(json.dumps(profiles(args.root), indent=2))
        elif args.cmd == "doctor":
            print(json.dumps(doctor(), indent=2))
        elif args.cmd == "create":
            dest = create_profile(args.root, args.id, args.browser, args.platform,
                                  args.user_agent, not args.allow_webrtc, args.model,
                                  args.protect_canvas, args.protect_webgl)
            print("Created:", dest)
        elif args.cmd == "launch":
            dest = profile_dir(args.root, args.id)
            metadata = json.loads((dest / "profile.json").read_text(encoding="utf-8"))
            if metadata["engine"] == "firefox":
                command = [BROWSERS[metadata["browser"]][0], "-no-remote", "-profile", str(dest)]
            else:
                command = [str(dest / "launch.sh")]
            if args.diagnostics:
                command.append(diagnostics_uri(args.root, args.id))
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
        elif args.cmd == "audit":
            payload = json.loads(args.input.read_text(encoding="utf-8"))
            print(json.dumps(audit_report(payload), indent=2))
        return 0
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        print("mora-identity:", error, file=sys.stderr)
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
