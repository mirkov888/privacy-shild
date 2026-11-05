# Mora Core & Connector API v1

## Peran

- **Mora Core** adalah registry connector dan event hub lokal milik Mora Shield.
- **Shield Module Connector** membaca status modul `privacy_shield` melalui adapter `Ps` yang sudah ada.
- **Device Telemetry Connector** membaca RAM, baterai, dan suhu melalui pembaca sistem yang sudah ada.
- **Mora Connector Service** menerima event dari aplikasi Mora lain melalui Android Messenger IPC.
- **Mora Connector Client** adalah helper yang dapat disalin/dibagikan ke aplikasi pendamping.

Core menyimpan paling banyak 500 event terakhir di memori. Tidak ada upload jaringan, database baru, atau mekanisme eksekusi shell dari connector eksternal.

## Keamanan IPC

Host mendaftarkan permission `com.mora.shield.permission.CONNECTOR` dengan protection level `signature`. Aplikasi connector wajib meminta permission tersebut dan harus ditandatangani dengan sertifikat yang sama dengan Mora Shield. Ini sengaja membatasi akses hanya untuk APK dalam rantai build/penandatanganan tepercaya.

IPC v1 hanya menerima `MSG_PUBLISH_EVENT`. Tidak ada endpoint untuk menjalankan perintah root, mengubah firewall, membaca file privat, atau mengeksekusi kode dari aplikasi pengirim. Identitas sumber diambil dari UID pengirim Android, bukan dari nama paket yang dikirim di payload.

Jangan kirim token, isi pesan pribadi, password, atau data sensitif melalui field `detail`. Gunakan metadata seperlunya dan singkat.

## Mengintegrasikan ke Mora Shield

1. Salin direktori `app/` dari paket ini ke root repository Mora Shield.
2. Jalankan `python3 apply-mora-core.py /path/ke/privacy-shild` untuk memasang file dan mengubah titik integrasi yang diperlukan.
3. Buka `INTEGRATION.md` bila ingin melakukan perubahan secara manual.
4. Build dengan `./gradlew assembleRelease`.

## Integrasi aplikasi connector

Tambahkan permission ke manifest aplikasi pendamping:

```xml
<uses-permission android:name="com.mora.shield.permission.CONNECTOR" />
```

Salin file `MoraConnectorContract.kt` dan `MoraConnectorClient.kt` ke aplikasi pendamping (atau jadikan keduanya modul library bersama). Contoh:

```kotlin
private lateinit var moraConnector: MoraConnectorClient

fun connectToMora(context: Context) {
    moraConnector = MoraConnectorClient(context) { connected ->
        // Perbarui indikator koneksi UI bila diperlukan.
    }
    moraConnector.connect()
}

fun reportDownloadFinished(context: Context) {
    moraConnector.publishEvent(
        connectorId = "mora-downloader",
        eventType = "DOWNLOAD_FINISHED",
        title = "Unduhan selesai",
        packageName = context.packageName,
        detail = "1 file berhasil disimpan",
        severity = "info"
    )
}

fun disconnectFromMora() {
    moraConnector.disconnect()
}
```

`publishEvent()` mengembalikan `true` bila pesan berhasil dikirim ke Messenger; itu bukan jaminan bahwa event sudah diproses. Sambungkan ulang ketika service terputus. Host package saat ini `com.mora.shield`.

## Batasan versi pertama

- Event disimpan dalam memori dan akan hilang saat proses Mora Shield berhenti.
- Status connector diperiksa saat pengguna menekan **Periksa** pada kartu Mora Core.
- Akses penggunaan jaringan per aplikasi tetap berasal dari modul/fitur Android yang memang menyediakan data itu; Core tidak mengklaim bisa melihat seluruh paket jaringan hanya dari API Android biasa.
- Pengiriman event lintas aplikasi memerlukan sertifikat tanda tangan yang sama. Build debug dari mesin berbeda bisa memakai debug keystore berbeda; gunakan signing key bersama yang dikelola aman untuk APK produksi.
