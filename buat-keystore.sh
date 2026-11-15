#!/bin/sh
# Buat keystore tetap Mora Shield. Jalankan sekali di laptop/Termux milikmu.
# Jangan commit mora-release.jks atau mora-release.jks.b64 ke Git.
set -eu
command -v keytool >/dev/null 2>&1 || {
  echo "keytool tidak ditemukan. Pasang JDK 17 terlebih dahulu."
  exit 1
}
keytool -genkeypair -v -keystore mora-release.jks -storetype PKCS12 -alias mora \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Mora Shield"
if base64 --help 2>&1 | grep -q -- '-w'; then
  base64 -w0 mora-release.jks > mora-release.jks.b64
else
  base64 mora-release.jks | tr -d '\n' > mora-release.jks.b64
fi
echo
echo "Tambahkan empat Repository Secrets berikut di GitHub:"
echo "  MORA_KEYSTORE_BASE64   = seluruh isi mora-release.jks.b64"
echo "  MORA_KEYSTORE_PASSWORD = kata sandi keystore"
echo "  MORA_KEY_ALIAS         = mora"
echo "  MORA_KEY_PASSWORD      = kata sandi key (PKCS12 biasanya sama)"
echo "Simpan .jks di tempat aman dan hapus .b64 setelah secrets tersimpan."
echo "JANGAN unggah kedua berkas tersebut ke repository."
