# Pemasangan Mora Core & Connector

Paket ini menambahkan Core/Connector ke project Mora Shield yang ada. Tidak membuat APK atau aplikasi kedua.

## Cara otomatis

Dari Linux, ekstrak ZIP lalu jalankan:

```bash
python3 /path/ke/mora-core-connector-patch/apply-mora-core.py /path/ke/privacy-shild
cd /path/ke/privacy-shild
./gradlew assembleRelease
```

Script aman dijalankan ulang: perubahan yang sudah terpasang tidak ditambahkan dua kali. Tetap simpan backup atau commit lokal sebelum menjalankannya.

## Perubahan otomatis

- Menyalin source di `app/src/main/java/com/mora/shield/core/` dan `connector/`.
- Menginisialisasi Mora Core dari `MainActivity`.
- Memasukkan event dari `Store` dan `EventService` ke event hub.
- Menambahkan kartu **Mora Core & Connector** pada tab **Alat**.
- Mendaftarkan Messenger service dengan permission tingkat `signature` di manifest.
- Menaikkan versi aplikasi ke `3.5.0` (versionCode `12`).
- Menambahkan dokumentasi API di `docs/MORA_CORE_CONNECTOR_API.md`.

## Integrasi manual jika script tidak cocok

1. Salin semua file di folder `app/` ke path yang sama di project.
2. Pada `MainActivity.kt`, import `com.mora.shield.core.MoraCore` dan panggil `MoraCore.initialize()` setelah `enableEdgeToEdge()` di `onCreate()`.
3. Pada `Store.kt`, import `MoraCore`, lalu panggil `MoraCore.ingestShieldEvents(events)` setiap kali `events = Ps.events(3000)` dijalankan.
4. Pada `EventService.kt`, import `MoraCore` dan panggil `MoraCore.publishShieldEvent(ev)` segera setelah parsing `Ps.Ev` berhasil.
5. Pada `ScreenTools.kt`, panggil `MoraCoreCard()` tepat setelah `KernelCard()`.
6. Di `AndroidManifest.xml`, deklarasikan permission berikut sebagai child langsung `<manifest>`:

```xml
<permission
    android:name="com.mora.shield.permission.CONNECTOR"
    android:protectionLevel="signature" />
```

7. Sebagai child `<application>`, daftarkan service berikut:

```xml
<service
    android:name=".connector.MoraConnectorService"
    android:exported="true"
    android:permission="com.mora.shield.permission.CONNECTOR">
    <intent-filter>
        <action android:name="com.mora.shield.action.CONNECTOR" />
    </intent-filter>
</service>
```

8. Pada `app/build.gradle.kts`, ubah `versionCode` menjadi `12` dan `versionName` menjadi `"3.5.0"`.
