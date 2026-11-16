# Mora Linux Environment (Android)

Mora Linux Environment mengelola Linux userspace di Android melalui Termux + proot-distro. Ini bukan virtual machine dan tidak menyediakan kernel Linux terpisah.

## Distro yang didukung

- Debian (`debian`) — direkomendasikan untuk mulai.
- Ubuntu (`ubuntu`).
- Arch Linux (`archlinux`).

Nama distro dan ketersediaannya mengikuti versi proot-distro yang terpasang.

## Integrasi langsung Mora Shield

Buka **Lainnya → Mora Linux Environment**.

Mora Shield menggunakan API resmi Termux `com.termux.RUN_COMMAND` untuk menjalankan daftar perintah terbatas:
- Memeriksa daftar distro yang terpasang.
- Memasang distro terpilih.
- Memasang XFCE, D-Bus, dan TigerVNC.
- Memulai VNC display `:1` yang hanya bind ke localhost.
- Menghentikan VNC display `:1`.
- Menerima stdout/stderr dan exit code dari Termux, lalu menampilkan hasil operasi.

Mora Shield tidak menyediakan kolom arbitrary shell command, tidak menjalankan root, dan meminta konfirmasi sebelum aksi instalasi/start/stop. Status distro disegarkan berkala; status sesi XFCE diperiksa lewat `vncserver -list`. Jika perintah gagal atau versi Termux tidak mendukung API hasil, status akan tetap tidak diketahui dan hasil terakhir dapat dilihat pada layar.

## Persiapan satu kali

1. Pasang Termux dari [rilis resmi Termux](https://github.com/termux/termux-app/releases) atau F-Droid. Jangan mencampur Termux dan plugin dari sumber tanda tangan berbeda.
2. Buka Termux sekali dan jalankan:
   ```sh
   mkdir -p ~/.termux
   printf 'allow-external-apps=true\\n' >> ~/.termux/termux.properties
   ```
   Jika file sudah memiliki pengaturan tersebut, pastikan hanya ada satu nilai aktif. Restart Termux setelah mengubah properti.
3. Di Android, buka **Info aplikasi Mora Shield → Izin → Izin tambahan → Run commands in Termux environment**, lalu izinkan. Mora Shield meminta izin ini saat diperlukan.
4. Pastikan Termux tidak dibatasi optimasi baterai jika sesi desktop harus tetap hidup.
5. Instal aplikasi VNC Viewer terpisah untuk melihat desktop.

API Termux memerlukan izin `com.termux.permission.RUN_COMMAND` dan `allow-external-apps=true`. Pengaturan tersebut memang sengaja memerlukan tindakan pengguna; Mora Shield tidak mengaktifkannya secara diam-diam.

## Alur penggunaan

1. Pilih Debian, Ubuntu, atau Arch Linux.
2. Tekan **Install proot-distro** untuk memperbarui paket Termux dan memasang pengelola distro.
3. Tekan **Install distro** dan setujui konfirmasi. Tunggu proses selesai di notifikasi/sesi Termux; periksa status kembali.
4. Tekan **Install XFCE + VNC** dan tunggu sampai Termux mengembalikan exit code sukses.
5. Tekan **Start XFCE**. Jika server meminta password pada pemakaian pertama, selesaikan pengaturan tersebut di Termux.
6. Di VNC Viewer, sambungkan ke host `127.0.0.1`, port `5901` (display `:1`).
7. Tekan **Stop XFCE** saat selesai.

Jika Termux tidak mengembalikan hasil, buka Termux dan periksa notifikasi atau sesi perintahnya. Mora Shield tidak boleh menampilkan status sukses hanya karena perintah sudah dikirim.

## Penyimpanan dan performa

XFCE dan dependensinya dapat menggunakan beberapa GB. Mulailah dengan resolusi VNC 1280×720. Turunkan resolusi atau color depth jika terasa lambat. Simpan salinan file penting sebelum menghapus atau memasang ulang distro.

## Batasan teknis

- PRoot berbagi kernel Android; bukan VM dengan kernel sendiri.
- systemd sebagai PID 1, Docker, modul kernel, privileged mounts, hardware virtualization, dan akselerasi GPU penuh tidak dijamin.
- Android/HyperOS dapat menghentikan Termux di latar belakang. Izinkan aktivitas latar dan sesuaikan optimasi baterai jika diperlukan.
- Start/stop mengelola VNC display `:1`; bukan menghidupkan/mematikan seluruh distro.
- Distro lain dapat ditambahkan jika didukung proot-distro, tetapi belum tersedia sebagai preset UI.
