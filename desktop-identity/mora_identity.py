#!/usr/bin/env python3
"""Mora Shield isolated desktop browser identity profiles."""
import argparse, json, os, re, shutil, sys
from pathlib import Path

DEFAULT_ROOT = Path.home() / ".config/mora-shield/identity-profiles"
BROWSERS = {
 "firefox": ("firefox", "firefox"), "librewolf": ("librewolf", "firefox"),
 "zen": ("zen", "firefox"), "brave": ("brave-browser", "chromium"),
 "chrome": ("google-chrome", "chromium"), "chromium": ("chromium", "chromium")
}
PLATFORMS = {
 "android": "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
 "iphone": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
 "ipad": "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
 "windows": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
 "macos": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
 "linux": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
DIAGNOSTICS = """<!doctype html><meta charset=utf-8><title>Mora Browser Diagnostics</title>
<h1>Mora Browser Diagnostics</h1><p>Local-only; nothing is uploaded.</p><pre id=o>Loading</pre>
<button id=d>Download JSON</button><script>
(async()=>{let r={time:new Date().toISOString(),userAgent:navigator.userAgent,
clientHints:navigator.userAgentData?{brands:navigator.userAgentData.brands,mobile:navigator.userAgentData.mobile,platform:navigator.userAgentData.platform}:null,
platform:navigator.platform,language:navigator.language,languages:navigator.languages,
timezone:Intl.DateTimeFormat().resolvedOptions().timeZone,
screen:{width:screen.width,height:screen.height,depth:screen.colorDepth,dpr:devicePixelRatio},
webRtc:typeof RTCPeerConnection!=="undefined",webgl:null,canvas:null};
try{let c=document.createElement('canvas'),g=c.getContext('webgl'),e=g&&g.getExtension('WEBGL_debug_renderer_info');
r.webgl=g?{vendor:e?g.getParameter(e.UNMASKED_VENDOR_WEBGL):'masked/unavailable',renderer:e?g.getParameter(e.UNMASKED_RENDERER_WEBGL):'masked/unavailable'}:null}catch(x){r.webgl={error:String(x)}}
try{let c=document.createElement('canvas');r.canvas={available:!!c.getContext('2d'),dataUrlLength:c.toDataURL().length}}catch(x){r.canvas={available:false}}
window.moraReport=r;o.textContent=JSON.stringify(r,null,2);
d.onclick=()=>{let a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(r,null,2)],{type:'application/json'}));a.download='mora-diagnostic.json';a.click()}})();</script>"""

def safe_id(value):
 if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,47}", value):
  raise ValueError("ID hanya boleh berisi huruf, angka, _ dan - (maks. 48 karakter).")
 return value

def profile_dir(root, profile_id):
 return root / safe_id(profile_id)

def create_profile(root, profile_id, browser, platform, user_agent=None, protect_webrtc=True):
 if browser not in BROWSERS or platform not in PLATFORMS: raise ValueError("Browser/platform tidak didukung.")
 ua=user_agent or PLATFORMS[platform]
 if not ua or len(ua)>1024 or any(ord(c)<32 for c in ua): raise ValueError("User-Agent tidak valid.")
 target=profile_dir(root,profile_id)
 if target.exists(): raise FileExistsError("Profil sudah ada; tidak ditimpa.")
 target.mkdir(parents=True)
 engine=BROWSERS[browser][1]
 meta={"id":profile_id,"browser":browser,"engine":engine,"platform_test_label":platform,
       "user_agent":ua,"webrtc_restriction_requested":protect_webrtc,
       "scope":"isolated browser compatibility profile; not device emulation"}
 (target/"profile.json").write_text(json.dumps(meta,indent=2)+"\n",encoding="utf-8")
 (target/"diagnostics.html").write_text(DIAGNOSTICS,encoding="utf-8")
 if engine=="firefox":
  prefs=['// Mora isolated test profile; existing profiles are untouched.',
         'user_pref("general.useragent.override", '+json.dumps(ua)+');']
  if protect_webrtc: prefs += ['user_pref("media.peerconnection.enabled", false);','user_pref("media.navigator.enabled", false);']
  (target/"user.js").write_text("\n".join(prefs)+"\n",encoding="utf-8")
 else:
  exe=BROWSERS[browser][0]
  args=[exe,"--user-data-dir="+str(target/"chromium-data"),"--user-agent="+ua,"--no-first-run"]
  if protect_webrtc: args.append("--force-webrtc-ip-handling-policy=disable_non_proxied_udp")
  quote=lambda s: "'"+s.replace("'","'\\''")+"'"
  launcher=target/"launch.sh"; launcher.write_text("#!/bin/sh\nset -eu\nexec "+" ".join(quote(a) for a in args)+' "$@"\n',encoding="utf-8"); launcher.chmod(0o700)
 return target

def profiles(root):
 result=[]
 for f in sorted(root.glob("*/profile.json")) if root.exists() else []:
  try: result.append(json.loads(f.read_text(encoding="utf-8")))
  except (OSError,json.JSONDecodeError): pass
 return result

def main(argv=None):
 p=argparse.ArgumentParser(description="Mora Shield isolated desktop browser profiles")
 p.add_argument("--root",type=Path,default=DEFAULT_ROOT,help=argparse.SUPPRESS)
 s=p.add_subparsers(dest="cmd",required=True); s.add_parser("list")
 c=s.add_parser("create"); c.add_argument("--id",required=True); c.add_argument("--browser",choices=BROWSERS,required=True); c.add_argument("--platform",choices=PLATFORMS,required=True); c.add_argument("--user-agent"); c.add_argument("--allow-webrtc",action="store_true")
 l=s.add_parser("launch"); l.add_argument("--id",required=True)
 b=s.add_parser("backup"); b.add_argument("--id",required=True); b.add_argument("--output",type=Path,required=True)
 r=s.add_parser("restore"); r.add_argument("--id",required=True); r.add_argument("--archive",type=Path,required=True)
 x=s.add_parser("rollback"); x.add_argument("--id",required=True)
 a=p.parse_args(argv)
 try:
  if a.cmd=="list": print(json.dumps(profiles(a.root),indent=2))
  elif a.cmd=="create":
   dest=create_profile(a.root,a.id,a.browser,a.platform,a.user_agent,not a.allow_webrtc); print("Created:",dest)
  elif a.cmd=="launch":
   dest=profile_dir(a.root,a.id); m=json.loads((dest/"profile.json").read_text())
   cmd=[BROWSERS[m["browser"]][0],"-no-remote","-profile",str(dest)] if m["engine"]=="firefox" else [str(dest/"launch.sh")]
   os.execvp(cmd[0],cmd)
  elif a.cmd=="backup":
   dest=profile_dir(a.root,a.id)
   if not (dest/"profile.json").is_file(): raise FileNotFoundError("Profil tidak ditemukan.")
   a.output.parent.mkdir(parents=True,exist_ok=True); print(shutil.make_archive(str(a.output.with_suffix("")),"zip",dest))
  elif a.cmd=="restore":
   import zipfile
   dest=profile_dir(a.root,a.id)
   if dest.exists(): raise FileExistsError("Target sudah ada; tidak ditimpa.")
   with zipfile.ZipFile(a.archive) as z:
    if any(Path(n).is_absolute() or ".." in Path(n).parts for n in z.namelist()): raise ValueError("Path arsip tidak aman.")
    dest.mkdir(parents=True); z.extractall(dest)
   if not (dest/"profile.json").is_file(): shutil.rmtree(dest); raise ValueError("Backup tidak valid.")
   print("Restored:",dest)
  elif a.cmd=="rollback":
   dest=profile_dir(a.root,a.id)
   if not (dest/"profile.json").is_file(): raise FileNotFoundError("Profil Mora tidak ditemukan.")
   shutil.rmtree(dest); print("Removed:",dest)
  return 0
 except (OSError,ValueError,KeyError,json.JSONDecodeError) as e:
  print("mora-identity:",e,file=sys.stderr); return 2
if __name__=="__main__": raise SystemExit(main())
