#!/usr/bin/env python3
"""Mora Shield isolated desktop browser profiles and portable identity-profile exchange."""
import argparse
import hashlib
import hmac
import ipaddress
import json
import os
import re
import secrets
import shutil
import socket
import ssl
import stat
import sys
import subprocess
import tempfile
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
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
# Flatpak IDs are optional fallbacks for common Linux desktop installations.
FLATPAK_APPS = {
    "firefox": "org.mozilla.firefox",
    "librewolf": "io.gitlab.librewolf-community",
    "zen": "app.zen_browser.zen",
    "brave": "com.brave.Browser",
    "chrome": "com.google.Chrome",
    "chromium": "org.chromium.Chromium",
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
webRtc:{peerConnection:typeof RTCPeerConnection!=="undefined",mediaDevices:!!navigator.mediaDevices,
ice:{supported:false,complete:false,candidateTypes:[],addressCandidates:0,mdnsCandidates:0}},webgl:null,canvas:null};
if(typeof RTCPeerConnection!=="undefined"){
 try{
  const pc=new RTCPeerConnection({iceServers:[]}),seen=new Set();
  r.webRtc.ice.supported=true;
  const finish=()=>new Promise(resolve=>{
   let done=false;
   const end=()=>{if(done)return;done=true;resolve()};
   pc.addEventListener("icecandidate",event=>{
    if(!event.candidate){r.webRtc.ice.complete=true;end();return}
    const value=String(event.candidate.candidate||"");
    const type=(value.match(/\btyp\s+(host|srflx|prflx|relay)\b/)||[])[1];
    if(type)seen.add(type);
    const address=(value.match(/candidate:\S+\s+\d+\s+\S+\s+\d+\s+([^\s]+)\s+\d+/)||[])[1]||"";
    if(address){
     if(address.toLowerCase().endsWith(".local"))r.webRtc.ice.mdnsCandidates++;
     else if(/^(?:\d{1,3}\.){3}\d{1,3}$/.test(address)||address.includes(":"))r.webRtc.ice.addressCandidates++;
    }
   });
   pc.createDataChannel("mora-diagnostic");
   pc.createOffer().then(offer=>pc.setLocalDescription(offer)).catch(()=>end());
   setTimeout(end,1800);
  });
  await finish();r.webRtc.ice.candidateTypes=[...seen].sort();pc.close();
 }catch(x){r.webRtc.ice.error=String(x).slice(0,160)}
}
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

def _write_chromium_privacy_extension(profile_dir):
    """Install an opt-in MV3 extension into a Mora-owned Chromium profile."""
    extension_dir = profile_dir / "mora-privacy-extension"
    extension_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "manifest_version": 3,
        "name": "Mora Shield Privacy Guard",
        "version": "1.0.0",
        "minimum_chrome_version": "111",
        "description": "Blocks common Canvas readback surfaces in this isolated Mora profile.",
        "content_scripts": [{
            "matches": ["<all_urls>"],
            "js": ["privacy.js"],
            "run_at": "document_start",
            "all_frames": True,
            "world": "MAIN",
        }],
    }
    script = r'''(() => {
  "use strict";
  if (globalThis.__moraCanvasGuardInstalled) return;
  try {
    Object.defineProperty(globalThis, "__moraCanvasGuardInstalled", {
      value: true, configurable: false, enumerable: false, writable: false
    });
  } catch (_) { return; }
  const deny = function () {
    throw new DOMException("Canvas readback blocked by Mora Shield profile protection", "SecurityError");
  };
  const block = (prototype, name) => {
    if (!prototype) return;
    const descriptor = Object.getOwnPropertyDescriptor(prototype, name);
    if (!descriptor || typeof descriptor.value !== "function") return;
    try {
      Object.defineProperty(prototype, name, {
        ...descriptor, value: deny, configurable: false, writable: false
      });
    } catch (_) {}
  };
  try {
    block(globalThis.CanvasRenderingContext2D && CanvasRenderingContext2D.prototype, "getImageData");
    block(globalThis.HTMLCanvasElement && HTMLCanvasElement.prototype, "toDataURL");
    block(globalThis.HTMLCanvasElement && HTMLCanvasElement.prototype, "toBlob");
    block(globalThis.OffscreenCanvas && OffscreenCanvas.prototype, "convertToBlob");
  } catch (_) {}
})();'''
    (extension_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    (extension_dir / "privacy.js").write_text(script + "\n", encoding="utf-8")
    return extension_dir


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
                "applied": bool(protect_canvas),
                "method": (
                    "Firefox privacy.resistFingerprinting"
                    if protect_canvas and engine == "firefox"
                    else ("Mora MV3 extension blocks page Canvas readback APIs; worker surfaces may remain" if protect_canvas else "disabled")
                ),
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
        extension_dir = _write_chromium_privacy_extension(target) if protect_canvas else None
        if extension_dir:
            extension_path = str(extension_dir.resolve())
            args.extend(["--disable-extensions-except=" + extension_path,
                         "--load-extension=" + extension_path])
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

def export_exchange_payload(root):
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
            "browser": item.get("browser"),
        })
    return {"schema": 1, "profiles": records, "favorites": []}


def export_exchange(root, output):
    payload = export_exchange_payload(root)
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, output)
    return len(payload["profiles"])


def merge_exchange_payload(root, payload, browser_override=None):
    if not isinstance(payload, dict) or payload.get("schema") != 1 or not isinstance(payload.get("profiles"), list):
        raise ValueError("Format pertukaran Mora tidak didukung (schema 1 diperlukan).")
    entries = payload["profiles"]
    if len(entries) > 100:
        raise ValueError("Maksimal 100 profil per sinkronisasi.")
    if browser_override is not None and browser_override not in BROWSERS:
        raise ValueError("Browser override tidak didukung.")

    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    prepared = []
    ids = set()
    for item in entries:
        if not isinstance(item, dict):
            raise ValueError("Entri profil tidak valid.")
        profile_id = safe_id(item.get("id", ""))
        if profile_id in ids:
            raise ValueError("ID profil duplikat dalam payload: " + profile_id)
        ids.add(profile_id)
        ua = _validate_user_agent(item.get("userAgent"))
        model = str(item.get("model") or profile_id).strip()[:120]
        if not model:
            raise ValueError("Nama model/profil kosong: " + profile_id)
        platform = _platform_for_import(item.get("platform"), model, ua)
        if not platform:
            raise ValueError("Platform tidak didukung untuk profil " + profile_id)
        browser = browser_override or _browser_for_import(item.get("browser"), ua)
        if browser not in BROWSERS:
            raise ValueError("Browser tidak didukung: " + str(browser))
        prepared.append((profile_id, browser, platform, ua, model))

    # Preflight every target before creating anything: conflicts never leave a partial sync.
    to_create = []
    unchanged = 0
    for profile_id, browser, platform, ua, model in prepared:
        destination = profile_dir(root, profile_id)
        if destination.is_symlink():
            raise ValueError("Target profil berupa symbolic link: " + profile_id)
        if not destination.exists():
            to_create.append((profile_id, browser, platform, ua, model))
            continue
        metadata_path = destination / "profile.json"
        try:
            existing = json.loads(metadata_path.read_text(encoding="utf-8"))
        except (OSError, ValueError, json.JSONDecodeError) as error:
            raise FileExistsError("Profil lokal tidak dapat diverifikasi: " + profile_id) from error
        matches = (
            existing.get("user_agent") == ua
            and existing.get("platform_test_label") == platform
            and existing.get("model") == model
            and existing.get("browser") == browser
        )
        if matches:
            unchanged += 1
            continue
        raise FileExistsError("Konflik ID profil; data lokal tidak ditimpa: " + profile_id)

    created = []
    try:
        for profile_id, browser, platform, ua, model in to_create:
            create_profile(root, profile_id, browser, platform, ua, model=model)
            created.append(profile_id)
    except Exception:
        for rollback_id in created:
            shutil.rmtree(profile_dir(root, rollback_id), ignore_errors=True)
        raise
    return {"created": created, "unchanged": unchanged}


def import_exchange(root, source, browser_override=None):
    payload = json.loads(Path(source).read_text(encoding="utf-8"))
    return merge_exchange_payload(root, payload, browser_override)["created"]


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


MAX_SYNC_BYTES = 256 * 1024


def make_sync_request_handler(root, token, expires_at):
    """Build an authenticated handler. Production serves it only inside a pinned TLS session."""
    root = Path(root)

    class MoraSyncHandler(BaseHTTPRequestHandler):
        server_version = "MoraShieldSync/1"

        def log_message(self, _format, *_args):
            # Do not log tokens, profile payloads, or request headers.
            return

        def _json(self, status, payload):
            raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(raw)

        def _authorized(self):
            if time.monotonic() >= expires_at:
                self._json(410, {"ok": False, "error": "Sesi sinkronisasi telah kedaluwarsa."})
                return False
            supplied = self.headers.get("Authorization", "")
            expected = "Bearer " + token
            if not hmac.compare_digest(supplied, expected):
                self._json(401, {"ok": False, "error": "Token sinkronisasi tidak valid."})
                return False
            return True

        def do_GET(self):
            if self.path != "/v1/profiles":
                self._json(404, {"ok": False, "error": "Endpoint tidak ditemukan."})
                return
            if not self._authorized():
                return
            self._json(200, export_exchange_payload(root))

        def do_POST(self):
            if self.path != "/v1/profiles":
                self._json(404, {"ok": False, "error": "Endpoint tidak ditemukan."})
                return
            if not self._authorized():
                return
            if self.headers.get_content_type() != "application/json":
                self._json(415, {"ok": False, "error": "Content-Type harus application/json."})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            if length < 2 or length > MAX_SYNC_BYTES:
                self._json(413, {"ok": False, "error": "Ukuran payload tidak valid atau melebihi 256 KB."})
                return
            try:
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                result = merge_exchange_payload(root, payload)
            except (UnicodeDecodeError, ValueError, OSError, json.JSONDecodeError) as error:
                self._json(400, {"ok": False, "error": str(error)[:240]})
                return
            self._json(200, {
                "ok": True,
                "created": len(result["created"]),
                "unchanged": result["unchanged"],
                "message": "Profil berhasil disinkronkan tanpa menimpa konflik lokal.",
            })

    return MoraSyncHandler


def _local_private_ipv4_addresses():
    addresses = set()
    try:
        for result in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            address = result[4][0]
            parsed = ipaddress.ip_address(address)
            if parsed.is_private and not parsed.is_loopback and not parsed.is_link_local:
                addresses.add(address)
    except OSError:
        pass
    return sorted(addresses)


def serve_lan_sync(root, host=None, port=8765, minutes=15):
    """Start an opt-in, short-lived HTTPS profile sync endpoint on one private LAN interface."""
    addresses = _local_private_ipv4_addresses()
    if host is None:
        if len(addresses) != 1:
            raise ValueError(
                "Alamat private LAN tidak tunggal. Tentukan --host dengan IPv4 Wi-Fi/LAN komputer."
            )
        host = addresses[0]
    try:
        bind_address = ipaddress.ip_address(host)
    except ValueError as error:
        raise ValueError("Host harus berupa alamat IPv4 private, bukan nama host.") from error
    if bind_address.version != 4:
        raise ValueError("Sinkronisasi LAN saat ini menggunakan IPv4.")
    if not bind_address.is_private or bind_address.is_loopback or bind_address.is_link_local or bind_address.is_unspecified:
        raise ValueError("Server hanya boleh bind ke satu alamat IPv4 private LAN yang spesifik.")
    if not 1 <= int(port) <= 65535:
        raise ValueError("Port harus 1–65535.")
    if not 1 <= int(minutes) <= 60:
        raise ValueError("Durasi sesi harus 1–60 menit.")
    openssl = shutil.which("openssl")
    if not openssl:
        raise RuntimeError("OpenSSL diperlukan untuk sertifikat TLS sementara. Debian: sudo apt install openssl")

    token = secrets.token_urlsafe(32)
    expires_at = time.monotonic() + (int(minutes) * 60)
    with tempfile.TemporaryDirectory(prefix="mora-lan-sync-") as temporary:
        cert_path = Path(temporary) / "server.crt"
        key_path = Path(temporary) / "server.key"
        subprocess.run(
            [
                openssl, "req", "-x509", "-newkey", "rsa:2048", "-sha256",
                "-nodes", "-days", "1", "-subj", "/CN=MoraShield-LAN-Sync",
                "-keyout", str(key_path), "-out", str(cert_path),
            ],
            check=True, capture_output=True, text=True, timeout=25,
        )
        os.chmod(key_path, 0o600)
        der = ssl.PEM_cert_to_DER_cert(cert_path.read_text(encoding="ascii"))
        fingerprint = hashlib.sha256(der).hexdigest()
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.minimum_version = ssl.TLSVersion.TLSv1_2
        tls.load_cert_chain(certfile=str(cert_path), keyfile=str(key_path))

        handler = make_sync_request_handler(root, token, expires_at)
        server = ThreadingHTTPServer((host, int(port)), handler)
        server.daemon_threads = True
        server.timeout = 0.75
        server.socket = tls.wrap_socket(server.socket, server_side=True)

        print("\nMORA SHIELD — LOCAL LAN SYNC", flush=True)
        print("HTTPS aktif untuk maksimum " + str(minutes) + " menit.", flush=True)
        print("Port: " + str(port), flush=True)
        print("Alamat Android: https://" + host + ":" + str(port) + "/v1/profiles", flush=True)
        print("TOKEN (rahasia; masukkan hanya di Mora Shield): " + token, flush=True)
        print("SHA-256 CERTIFICATE PIN: " + ":".join(
            fingerprint[index:index + 2] for index in range(0, len(fingerprint), 2)
        ), flush=True)
        print("Jangan bagikan token atau pin. Hentikan server dengan Ctrl+C.", flush=True)
        try:
            while time.monotonic() < expires_at:
                server.handle_request()
        except KeyboardInterrupt:
            print("\nSesi sinkronisasi dihentikan.", flush=True)
        finally:
            server.server_close()
    return 0

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
        "canvas": "available: opt-in Mora MV3 extension blocks page Canvas readback (worker surfaces may remain)",
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
    if not rtc.get("peerConnection"):
        checks.append({"surface": "webRtc", "status": "unavailable",
                       "detail": "RTCPeerConnection tidak tersedia"})
    else:
        ice = rtc.get("ice") if isinstance(rtc.get("ice"), dict) else {}
        try:
            address_candidates = max(0, int(ice.get("addressCandidates") or 0))
        except (TypeError, ValueError, OverflowError):
            address_candidates = 0
        try:
            mdns_candidates = max(0, int(ice.get("mdnsCandidates") or 0))
        except (TypeError, ValueError, OverflowError):
            mdns_candidates = 0
        raw_types = ice.get("candidateTypes")
        candidate_types = (
            sorted({item for item in raw_types if isinstance(item, str)})
            if isinstance(raw_types, (list, tuple, set)) else []
        )
        if address_candidates:
            status = "attention"
            detail = ("ICE candidate memuat alamat IP literal; Mora hanya menghitungnya, "
                      "tidak menyimpan alamatnya. Ini bukan bukti alamat IP publik bocor.")
        elif mdns_candidates:
            status = "reduced_exposure"
            detail = "ICE candidate menggunakan hostname mDNS; alamat IP literal tidak terdeteksi."
        else:
            status = "visible"
            detail = "RTCPeerConnection tersedia; belum ada alamat IP literal yang terdeteksi."
        checks.append({"surface": "webRtc", "status": status, "detail": detail,
                       "address_candidates": address_candidates,
                       "mdns_candidates": mdns_candidates,
                       "candidate_types": candidate_types})
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
            "webrtc_address_exposures": sum(
                item.get("address_candidates", 0) for item in checks if item["surface"] == "webRtc"
            ),
        },
        "checks": checks,
        "note": "Pemeriksaan API/metadata lokal; tidak menguji IP publik, DNS, VPN, atau rute jaringan.",
    }


def _flatpak_applications():
    flatpak = shutil.which("flatpak")
    if not flatpak:
        return set()
    try:
        result = subprocess.run(
            [flatpak, "list", "--app", "--columns=application"],
            capture_output=True, text=True, timeout=8, check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return set()
    if result.returncode != 0:
        return set()
    return {line.strip() for line in result.stdout.splitlines() if line.strip()}


def browser_installation(name, flatpak_apps=None):
    if name not in BROWSERS:
        raise ValueError("Browser tidak didukung: " + str(name))
    executable, _engine = BROWSERS[name]
    native_path = shutil.which(executable)
    if native_path:
        return {"kind": "native", "executable": native_path}
    apps = _flatpak_applications() if flatpak_apps is None else set(flatpak_apps)
    app_id = FLATPAK_APPS.get(name)
    if app_id and app_id in apps and shutil.which("flatpak"):
        return {"kind": "flatpak", "executable": shutil.which("flatpak"), "app_id": app_id}
    return None


def doctor():
    flatpak_apps = _flatpak_applications()
    result = {}
    for name, (command, engine) in BROWSERS.items():
        installation = browser_installation(name, flatpak_apps)
        result[name] = {
            "executable": installation["executable"] if installation else command,
            "installed": installation is not None,
            "launch_type": installation["kind"] if installation else "unavailable",
            "flatpak_app_id": installation.get("app_id") if installation else None,
            "engine": engine,
            "protection": protection_capabilities(name),
        }
    return result

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
    installation = browser_installation(browser)
    if not installation:
        raise FileNotFoundError(
            "Browser belum terpasang. Pasang paket native atau Flatpak untuk " + browser + "."
        )
    engine = metadata.get("engine")
    if installation["kind"] == "flatpak":
        flatpak = installation["executable"]
        app_id = installation["app_id"]
        command = [flatpak, "run", "--filesystem=" + str(destination.resolve()) + ":rw"]
        if engine == "firefox":
            command += ["--command=" + BROWSERS[browser][0], app_id,
                        "-no-remote", "-profile", str(destination.resolve())]
        else:
            command += [
                app_id,
                "--user-data-dir=" + str((destination / "chromium-data").resolve()),
                "--user-agent=" + _validate_user_agent(metadata.get("user_agent")),
                "--no-first-run",
            ]
            protection = metadata.get("protection", {})
            extension_dir = destination / "mora-privacy-extension"
            if protection.get("canvas", {}).get("applied") and extension_dir.is_dir():
                extension_path = str(extension_dir.resolve())
                command.extend(["--disable-extensions-except=" + extension_path,
                                "--load-extension=" + extension_path])
            if protection.get("webgl", {}).get("applied"):
                command.append("--disable-webgl")
            if protection.get("webrtc", {}).get("applied"):
                command.append("--force-webrtc-ip-handling-policy=disable_non_proxied_udp")
    elif engine == "firefox":
        command = [installation["executable"], "-no-remote", "-profile", str(destination)]
    else:
        launcher = destination / "launch.sh"
        if not launcher.is_file():
            raise FileNotFoundError("Launcher profil Chromium tidak ditemukan.")
        command = [str(launcher)]
    if diagnostics:
        command.append(diagnostics_uri(root, profile_id))
    if installation["kind"] == "native" and engine == "chromium":
        command[0] = str((destination / "launch.sh").resolve())
    return subprocess.Popen(command, cwd=str(destination), start_new_session=True)


def open_desktop_dashboard(root):
    """Open the lightweight Linux dashboard for the existing Mora Shield bridge."""
    try:
        import tkinter as tk
        from tkinter import filedialog, messagebox, simpledialog, ttk
    except ImportError as error:
        raise RuntimeError("Tkinter belum terpasang. Debian/Ubuntu: sudo apt install python3-tk") from error

    base = Path(root).expanduser()
    base.mkdir(parents=True, exist_ok=True)
    window = tk.Tk()
    window.title("Mora Shield • Identity Desk")
    window.geometry("1040x720")
    window.minsize(860, 600)

    style = ttk.Style(window)
    try:
        style.theme_use("clam")
    except tk.TclError:
        pass

    # Compact dark dashboard styling that remains native Tk/ttk (no extra UI dependency).
    palette = {
        "background": "#11131a",
        "surface": "#191d27",
        "surface_alt": "#242a38",
        "text": "#e8eaf2",
        "muted": "#a5adbf",
        "accent": "#7662d8",
        "accent_hover": "#8875ed",
    }
    window.configure(background=palette["background"])
    style.configure(".", font=("Sans", 10))
    style.configure("TFrame", background=palette["background"])
    style.configure("TLabel", background=palette["background"], foreground=palette["text"])
    style.configure("TLabelframe", background=palette["background"], foreground=palette["text"])
    style.configure("TLabelframe.Label", background=palette["background"], foreground=palette["muted"])
    style.configure(
        "TButton", padding=(10, 7), background=palette["surface_alt"],
        foreground=palette["text"], borderwidth=0,
    )
    style.map(
        "TButton",
        background=[("disabled", palette["surface"]), ("active", palette["accent_hover"])],
        foreground=[("disabled", palette["muted"]), ("active", "#ffffff")],
    )
    style.configure(
        "TEntry", fieldbackground=palette["surface"], foreground=palette["text"],
        insertcolor=palette["text"], padding=6,
    )
    style.configure(
        "TCombobox", fieldbackground=palette["surface"], foreground=palette["text"],
        arrowcolor=palette["text"], padding=6,
    )
    style.configure(
        "Treeview", background=palette["surface"], fieldbackground=palette["surface"],
        foreground=palette["text"], rowheight=29, borderwidth=0,
    )
    style.configure(
        "Treeview.Heading", background=palette["surface_alt"],
        foreground=palette["text"], padding=(8, 7), relief="flat",
    )
    style.map(
        "Treeview",
        background=[("selected", palette["accent"])],
        foreground=[("selected", "#ffffff")],
    )
    style.configure("TCheckbutton", background=palette["background"], foreground=palette["text"])
    style.map("TCheckbutton", foreground=[("active", palette["text"])])

    selected_id = tk.StringVar()
    search_text = tk.StringVar()
    profile_id = tk.StringVar()
    browser_var = tk.StringVar(value="firefox")
    platform_var = tk.StringVar(value="android")
    model_var = tk.StringVar()
    ua_var = tk.StringVar()
    canvas_var = tk.BooleanVar(value=False)
    webgl_var = tk.BooleanVar(value=False)
    rtc_var = tk.BooleanVar(value=True)
    status_var = tk.StringVar(value="Profil lokal: " + str(base))

    outer = ttk.Frame(window, padding=14)
    outer.pack(fill="both", expand=True)
    ttk.Label(outer, text="Mora Shield Identity Desk", font=("Sans", 19, "bold"), foreground=palette["text"]).pack(anchor="w")
    ttk.Label(
        outer,
        text="Kelola profil browser Linux yang terisolasi. Profil browser utama tidak disentuh.",
        foreground=palette["muted"],
    ).pack(anchor="w", pady=(2, 10))

    create_box = ttk.LabelFrame(outer, text="Buat profil baru", padding=10)
    create_box.pack(fill="x", pady=(0, 10))
    ttk.Label(create_box, text="ID profil").grid(row=0, column=0, sticky="w", padx=4, pady=3)
    ttk.Entry(create_box, textvariable=profile_id, width=20).grid(row=1, column=0, sticky="ew", padx=4)
    ttk.Label(create_box, text="Browser").grid(row=0, column=1, sticky="w", padx=4, pady=3)
    ttk.Combobox(create_box, textvariable=browser_var, values=list(BROWSERS), state="readonly", width=14).grid(row=1, column=1, sticky="ew", padx=4)
    ttk.Label(create_box, text="Label platform").grid(row=0, column=2, sticky="w", padx=4, pady=3)
    ttk.Combobox(create_box, textvariable=platform_var, values=list(PLATFORMS), state="readonly", width=13).grid(row=1, column=2, sticky="ew", padx=4)
    ttk.Label(create_box, text="Model / label").grid(row=0, column=3, sticky="w", padx=4, pady=3)
    ttk.Entry(create_box, textvariable=model_var, width=22).grid(row=1, column=3, sticky="ew", padx=4)
    ttk.Label(create_box, text="User-Agent kustom (opsional)").grid(row=0, column=4, sticky="w", padx=4, pady=3)
    ttk.Entry(create_box, textvariable=ua_var, width=34).grid(row=1, column=4, sticky="ew", padx=4)
    for column in range(5):
        create_box.columnconfigure(column, weight=1)

    options = ttk.Frame(create_box)
    options.grid(row=2, column=0, columnspan=5, sticky="w", pady=(8, 2))
    ttk.Checkbutton(options, text="Canvas guard", variable=canvas_var).pack(side="left", padx=(4, 12))
    ttk.Checkbutton(options, text="Nonaktifkan WebGL", variable=webgl_var).pack(side="left", padx=12)
    ttk.Checkbutton(options, text="Batasi WebRTC", variable=rtc_var).pack(side="left", padx=12)

    toolbar = ttk.Frame(outer)
    toolbar.pack(fill="x", pady=(0, 8))
    ttk.Label(toolbar, text="Cari profil").pack(side="left")
    search_entry = ttk.Entry(toolbar, textvariable=search_text, width=32)
    search_entry.pack(side="left", padx=(6, 12))
    tree_frame = ttk.Frame(outer)
    tree_frame.pack(fill="both", expand=True)
    columns = ("id", "model", "browser", "platform", "protection")
    tree = ttk.Treeview(tree_frame, columns=columns, show="headings", height=12, selectmode="browse")
    headers = {"id": "Profile ID", "model": "Model / label", "browser": "Browser", "platform": "Platform", "protection": "Protection"}
    widths = {"id": 160, "model": 210, "browser": 100, "platform": 100, "protection": 250}
    for key in columns:
        tree.heading(key, text=headers[key])
        tree.column(key, width=widths[key], minwidth=70, stretch=key in ("model", "protection"))
    scrollbar = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
    tree.configure(yscrollcommand=scrollbar.set)
    tree.pack(side="left", fill="both", expand=True)
    scrollbar.pack(side="right", fill="y")

    detail_box = ttk.LabelFrame(outer, text="Detail profil terpilih", padding=8)
    detail_box.pack(fill="x", pady=8)
    details = tk.Text(detail_box, height=5, wrap="word", state="disabled", bg=palette["surface"], fg=palette["text"], insertbackground=palette["text"], relief="flat", padx=8, pady=8)
    details.pack(fill="x")

    def set_status(message):
        status_var.set(str(message))

    def selected_profile():
        profile = selected_id.get()
        if not profile:
            choice = tree.selection()
            if choice:
                profile = choice[0]
        if not profile:
            messagebox.showinfo("Mora Shield", "Pilih profil terlebih dahulu.", parent=window)
            return None
        try:
            return profile_dir(base, profile), json.loads((profile_dir(base, profile) / "profile.json").read_text(encoding="utf-8"))
        except (OSError, ValueError, json.JSONDecodeError) as error:
            messagebox.showerror("Mora Shield", str(error), parent=window)
            return None

    def refresh(*_):
        query = search_text.get().strip().lower()
        for item in tree.get_children():
            tree.delete(item)
        for entry in profiles(base):
            haystack = " ".join(str(entry.get(key, "")) for key in ("id", "model", "browser", "platform_test_label")).lower()
            if query and query not in haystack:
                continue
            protection = entry.get("protection", {})
            active = [
                name.upper() for name in ("canvas", "webgl", "webrtc")
                if protection.get(name, {}).get("applied")
            ]
            tree.insert("", "end", iid=entry["id"], values=(
                entry["id"], entry.get("model", ""), entry.get("browser", ""),
                entry.get("platform_test_label", ""), ", ".join(active) or "—"
            ))
        if selected_id.get() and tree.exists(selected_id.get()):
            tree.selection_set(selected_id.get())
        set_status(str(len(profiles(base))) + " profil tersimpan • " + str(base))

    def on_select(_event=None):
        choice = tree.selection()
        if not choice:
            return
        selected_id.set(choice[0])
        found = next((item for item in profiles(base) if item.get("id") == choice[0]), {})
        details.configure(state="normal")
        details.delete("1.0", "end")
        details.insert("1.0", json.dumps(found, indent=2, ensure_ascii=False))
        details.configure(state="disabled")

    def create_action():
        try:
            ident = safe_id(profile_id.get().strip())
            created = create_profile(
                base, ident, browser_var.get(), platform_var.get(),
                user_agent=ua_var.get().strip() or None,
                protect_webrtc=rtc_var.get(),
                model=model_var.get().strip() or None,
                protect_canvas=canvas_var.get(),
                protect_webgl=webgl_var.get(),
            )
            selected_id.set(ident)
            profile_id.set("")
            refresh()
            if tree.exists(ident):
                tree.selection_set(ident)
                on_select()
            set_status("Profil dibuat: " + str(created))
        except (OSError, ValueError) as error:
            messagebox.showerror("Gagal membuat profil", str(error), parent=window)

    def launch_action(with_diagnostics=False):
        selected = selected_profile()
        if not selected:
            return
        destination, metadata = selected
        try:
            process = launch_profile(base, metadata["id"], diagnostics=with_diagnostics)
            set_status("Membuka profil terisolasi: " + metadata["id"] + " (PID " + str(process.pid) + ")")
        except (OSError, ValueError) as error:
            messagebox.showerror("Gagal membuka browser", str(error), parent=window)

    def backup_action():
        selected = selected_profile()
        if not selected:
            return
        destination, metadata = selected
        output = filedialog.asksaveasfilename(
            parent=window, title="Simpan backup profil", defaultextension=".zip",
            initialfile=metadata["id"] + ".zip", filetypes=[("ZIP", "*.zip")]
        )
        if not output:
            return
        try:
            archive = backup_profile(base, metadata["id"], output)
            set_status("Backup tersimpan: " + str(archive))
        except (OSError, ValueError) as error:
            messagebox.showerror("Backup gagal", str(error), parent=window)

    def restore_action():
        archive = filedialog.askopenfilename(parent=window, title="Pilih backup profil", filetypes=[("ZIP", "*.zip")])
        if not archive:
            return
        ident = simpledialog.askstring("Restore profil", "Masukkan Profile ID yang ada di backup:", parent=window)
        if not ident:
            return
        try:
            restored = restore_profile(base, ident.strip(), archive)
            selected_id.set(ident.strip())
            refresh()
            set_status("Profil dipulihkan: " + str(restored))
        except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
            messagebox.showerror("Restore gagal", str(error), parent=window)

    def rollback_action():
        selected = selected_profile()
        if not selected:
            return
        _, metadata = selected
        if not messagebox.askyesno(
            "Konfirmasi rollback",
            "Pulihkan backup terakhir untuk " + metadata["id"] + "?\nProfil aktif saat ini akan disimpan sebagai pre-rollback.",
            parent=window
        ):
            return
        try:
            restored = rollback_profile(base, metadata["id"])
            refresh()
            set_status("Rollback selesai: " + str(restored))
        except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
            messagebox.showerror("Rollback gagal", str(error), parent=window)

    def import_action():
        source = filedialog.askopenfilename(parent=window, title="Impor profil Mora", filetypes=[("JSON", "*.json")])
        if not source:
            return
        try:
            imported = import_exchange(base, source)
            refresh()
            set_status("Profil diimpor: " + (", ".join(imported) if imported else "tidak ada"))
        except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
            messagebox.showerror("Impor gagal", str(error), parent=window)

    def export_action():
        output = filedialog.asksaveasfilename(
            parent=window, title="Ekspor profil Mora", defaultextension=".json",
            initialfile="mora-identity-profiles.json", filetypes=[("JSON", "*.json")]
        )
        if not output:
            return
        try:
            count = export_exchange(base, output)
            set_status(str(count) + " profil diekspor: " + output)
        except (OSError, ValueError) as error:
            messagebox.showerror("Ekspor gagal", str(error), parent=window)

    def compare_action():
        before = filedialog.askopenfilename(parent=window, title="Pilih laporan sebelum", filetypes=[("JSON", "*.json")])
        if not before:
            return
        after = filedialog.askopenfilename(parent=window, title="Pilih laporan sesudah", filetypes=[("JSON", "*.json")])
        if not after:
            return
        try:
            result = compare_reports(before, after)
            messagebox.showinfo("Perbandingan diagnostik", json.dumps(result, indent=2, ensure_ascii=False), parent=window)
        except (OSError, ValueError, json.JSONDecodeError) as error:
            messagebox.showerror("Perbandingan gagal", str(error), parent=window)

    def audit_action():
        source = filedialog.askopenfilename(parent=window, title="Pilih laporan diagnostik", filetypes=[("JSON", "*.json")])
        if not source:
            return
        try:
            report = json.loads(Path(source).read_text(encoding="utf-8"))
            result = audit_report(report)
            messagebox.showinfo("Audit identitas lokal", json.dumps(result, indent=2, ensure_ascii=False), parent=window)
        except (OSError, ValueError, json.JSONDecodeError) as error:
            messagebox.showerror("Audit gagal", str(error), parent=window)

    def doctor_action():
        result = doctor()
        messagebox.showinfo("Browser & protection status", json.dumps(result, indent=2, ensure_ascii=False), parent=window)

    buttons = ttk.Frame(outer)
    buttons.pack(fill="x", pady=(2, 6))
    actions = [
        ("Buat profil", create_action), ("Buka browser", launch_action),
        ("Diagnostik", lambda: launch_action(True)), ("Backup", backup_action),
        ("Restore", restore_action), ("Rollback", rollback_action),
        ("Impor JSON", import_action), ("Ekspor JSON", export_action),
        ("Bandingkan", compare_action), ("Audit", audit_action), ("Doctor", doctor_action),
    ]
    for index, (label, callback) in enumerate(actions):
        ttk.Button(buttons, text=label, command=callback).grid(row=index // 6, column=index % 6, sticky="ew", padx=3, pady=3)
    for column in range(6):
        buttons.columnconfigure(column, weight=1)
    ttk.Label(outer, textvariable=status_var, foreground="#666666").pack(anchor="w", pady=(4, 0))

    tree.bind("<<TreeviewSelect>>", on_select)
    tree.bind("<Double-1>", lambda _event: launch_action())
    search_text.trace_add("write", refresh)
    refresh()
    window.mainloop()


def main(argv=None):
    parser = argparse.ArgumentParser(description="Mora Shield isolated desktop browser profiles")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest="cmd", required=True)
    commands.add_parser("list")
    commands.add_parser("gui", help="open the Linux Mora Shield Identity Desk dashboard")
    commands.add_parser("doctor")
    create = commands.add_parser("create")
    create.add_argument("--id", required=True)
    create.add_argument("--browser", choices=BROWSERS, required=True)
    create.add_argument("--platform", choices=PLATFORMS, required=True)
    create.add_argument("--model")
    create.add_argument("--user-agent")
    create.add_argument("--allow-webrtc", action="store_true")
    create.add_argument("--protect-canvas", action="store_true",
                        help="Firefox RFP atau ekstensi Canvas page-context untuk Chromium; sebagian situs dapat terganggu")
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
    sync = commands.add_parser(
        "sync-serve",
        help="start a short-lived HTTPS profile sync server for a trusted local network",
    )
    sync.add_argument(
        "--host", default=None,
        help="one private LAN IPv4 address; auto-selects only when exactly one is detected",
    )
    sync.add_argument("--port", type=int, default=8765)
    sync.add_argument("--minutes", type=int, default=15, help="session lifetime (1–60 minutes)")
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
        elif args.cmd == "sync-serve":
            return serve_lan_sync(args.root, args.host, args.port, args.minutes)
        return 0
    except (
        OSError, ValueError, KeyError, RuntimeError, subprocess.CalledProcessError,
        subprocess.TimeoutExpired, zipfile.BadZipFile, json.JSONDecodeError,
    ) as error:
        print("mora-identity:", error, file=sys.stderr)
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
