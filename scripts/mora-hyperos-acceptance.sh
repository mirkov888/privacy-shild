#!/usr/bin/env bash
# Read-only ADB preflight for manual Mora Shield acceptance on HyperOS / Android.
# Does not read IMEI, serial, Android ID, MAC address or build fingerprint.
set -uo pipefail

APP_ID="com.mora.shield"
if ! command -v adb >/dev/null 2>&1; then
  echo "adb tidak ditemukan. Debian/Ubuntu: sudo apt install adb" >&2
  exit 2
fi

SERIAL="${ANDROID_SERIAL:-}"
if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if [[ "${#DEVICES[@]}" -eq 0 ]]; then
    echo "Tidak ada perangkat ADB yang terhubung dan diizinkan." >&2
    exit 2
  elif [[ "${#DEVICES[@]}" -gt 1 ]]; then
    echo "Lebih dari satu perangkat terhubung. Set ANDROID_SERIAL terlebih dahulu (nilainya tidak akan dicetak)." >&2
    exit 2
  fi
  SERIAL="${DEVICES[0]}"
fi

if ! adb -s "$SERIAL" get-state >/dev/null 2>&1; then
  echo "Perangkat $SERIAL belum siap atau belum mengizinkan debugging USB." >&2
  exit 2
fi

umask 077
OUT="${1:-mora-hyperos-acceptance-$(date +%Y%m%d-%H%M%S).txt}"
exec > >(tee "$OUT") 2>&1

property() {
  local key="$1"
  local value
  value="$(adb -s "$SERIAL" shell getprop "$key" 2>/dev/null | tr -d '\r')"
  printf '%-34s %s\n' "$key" "${value:-unavailable}"
}

section() {
  printf '\n========== %s ==========\n' "$1"
}

section "DEVICE / ROM"
echo "ADB device: connected (device selector redacted)"
property ro.product.manufacturer
property ro.product.model
property ro.build.version.release
property ro.build.version.sdk
property ro.miui.ui.version.name
property ro.miui.ui.version.code
property ro.mi.os.version.name

section "WEBVIEW PROVIDER"
adb -s "$SERIAL" shell dumpsys webviewupdate 2>&1 |
  grep -Ei 'Current WebView|WebView package|Fallback|valid package' || true

section "INSTALLED BROWSER / WEBVIEW PACKAGES"
adb -s "$SERIAL" shell pm list packages 2>/dev/null |
  grep -Ei 'webview|chrome|firefox|brave|zen|librewolf|browser' || true

section "MORA SHIELD PACKAGE"
APK_PATH="$(adb -s "$SERIAL" shell pm path "$APP_ID" 2>/dev/null | head -n 1)"
if [[ "$APK_PATH" == package:* ]]; then
  adb -s "$SERIAL" shell dumpsys package "$APP_ID" 2>/dev/null |
    grep -E 'versionName=|versionCode=|targetSdk=|userId=|granted=true|POST_NOTIFICATIONS|INTERNET' |
    head -n 60 || true

  section "APP LAUNCH SMOKE TEST"
  adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell monkey -p "$APP_ID" 1 2>&1 || true
  sleep 3
  PID="$(adb -s "$SERIAL" shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  echo "Mora Shield PID after launch: ${PID:-not-running}"

  section "WORKMANAGER / JOBSCHEDULER"
  adb -s "$SERIAL" shell dumpsys jobscheduler 2>/dev/null |
    grep -F -A 8 -B 3 "$APP_ID" | head -n 120 || true

  section "BATTERY / BACKGROUND POLICY"
  adb -s "$SERIAL" shell dumpsys deviceidle whitelist 2>/dev/null |
    grep -F "$APP_ID" || echo "Mora Shield tidak tercantum dalam device-idle whitelist (belum tentu masalah)."
  adb -s "$SERIAL" shell cmd appops get "$APP_ID" RUN_IN_BACKGROUND 2>&1 || true

  section "RECENT MORA / CRASH LOGS"
  adb -s "$SERIAL" logcat -d -t 4000 2>/dev/null |
    grep -Ei 'FATAL EXCEPTION|AndroidRuntime|com\.mora\.shield|Mora Shield|Mora identity' |
    tail -n 120 || true
else
  echo "Mora Shield belum terpasang. Install APK terlebih dahulu:"
  echo "  adb -s $SERIAL install -r MoraShield-debug.apk"
  echo "Preflight hanya mengambil informasi; tidak memasang atau mengubah aplikasi."
  exit 3
fi

section "NEXT MANUAL CHECKS"
cat <<'EOF'
[ ] Buka Identity Studio dan pastikan katalog serta profil terpilih tampil.
[ ] Jalankan Scan kompatibilitas Android; catat WebView provider dan fitur yang tersedia.
[ ] Buka preview offline; bandingkan baseline sebelum/sesudah Canvas, WebGL dan WebRTC.
[ ] Buat profil kustom, ekspor, impor kembali, lalu pastikan profil duplikat tidak menggandakan data.
[ ] Buat backup lengkap, ubah pilihan/proteksi, restore, lalu uji rollback.
[ ] Aktifkan jadwal randomizer; reboot; periksa status JobScheduler dan kebijakan baterai Xiaomi.
[ ] Uji hook hanya pada aplikasi non-kritis; verifikasi scope di LSPosed Manager dan rollback.
[ ] Uji sinkronisasi LAN dengan Linux; cocokkan certificate pin; ulangi sync untuk cek idempotency.
[ ] Uji setiap browser Linux yang terpasang dan pastikan profil browser utama tidak berubah.
EOF

echo
echo "Report saved: $OUT"
echo "Catatan: ini ADB preflight + launch smoke test, bukan pengganti pemeriksaan UI/manual di perangkat."
