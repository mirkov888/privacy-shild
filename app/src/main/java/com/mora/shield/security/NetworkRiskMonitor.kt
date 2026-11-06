package com.mora.shield.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** Heuristik lokal. Tidak pernah menyatakan BTS palsu hanya dari satu sinyal. */
object NetworkRiskMonitor {
    data class Signal(val title: String, val detail: String, val severity: String)

    fun inspect(context: Context): List<Signal> {
        val out = mutableListOf<Signal>()
        val prefs = context.getSharedPreferences("mora_network_baseline", Context.MODE_PRIVATE)
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (hasPhone) {
            val current = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            val previous = prefs.getInt("last_data_network_type", TelephonyManager.NETWORK_TYPE_UNKNOWN)
            val wasHighSpeed = previous == TelephonyManager.NETWORK_TYPE_LTE || previous == 20
            val now2g = current == TelephonyManager.NETWORK_TYPE_GPRS || current == TelephonyManager.NETWORK_TYPE_EDGE ||
                current == TelephonyManager.NETWORK_TYPE_CDMA || current == TelephonyManager.NETWORK_TYPE_1xRTT || current == TelephonyManager.NETWORK_TYPE_IDEN
            if (wasHighSpeed && now2g) out += Signal("Perubahan jaringan seluler", "Jaringan turun dari LTE/5G ke 2G. Ini heuristik; bisa terjadi karena cakupan, gangguan, atau perpindahan jaringan dan bukan bukti BTS palsu.", "MEDIUM")
            prefs.edit().putInt("last_data_network_type", current).apply()
            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                val cellKey = runCatching {
                    tm.allCellInfo?.firstOrNull { it.isRegistered }?.cellIdentity?.toString()?.hashCode()?.toString()
                }.getOrNull()
                if (cellKey != null) {
                    val oldCell = prefs.getString("registered_cell_hash", null)
                    if (oldCell != null && oldCell != cellKey) out += Signal(
                        "Identitas sel berubah", "Identitas sel terdaftar berubah sejak pemeriksaan sebelumnya. Handover normal juga menyebabkan ini; tanpa konteks perpindahan, ini bukan bukti BTS palsu.", "INFO"
                    )
                    prefs.edit().putString("registered_cell_hash", cellKey).apply()
                }
            }
        } else {
            out += Signal("Izin telepon belum diberikan", "READ_PHONE_STATE diperlukan untuk memeriksa perubahan tipe jaringan seluler. Tidak ada data lokasi yang dikirim.", "INFO")
        }

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifi = runCatching { wm.connectionInfo }.getOrNull()
        if (wifi != null && wifi.networkId != -1) {
            val ssid = wifi.ssid?.removePrefix("\"")?.removeSuffix("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            val bssid = wifi.bssid?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }
            if (ssid != null && bssid != null) {
                val oldSsid = prefs.getString("wifi_ssid", null)
                val oldBssid = prefs.getString("wifi_bssid", null)
                if (oldSsid == ssid && oldBssid != null && oldBssid != bssid) {
                    out += Signal("BSSID Wi-Fi berubah", "SSID yang sama ($ssid) kini memakai BSSID berbeda. Ini bisa normal (mesh/roaming) atau perlu diverifikasi.", "MEDIUM")
                }
                val encryption = runCatching {
                    wm.scanResults.firstOrNull { it.SSID == ssid && (it.BSSID == bssid || it.BSSID.equals(bssid, true)) }?.capabilities
                        ?.let { caps -> when {
                            caps.contains("WPA3", true) -> "WPA3"
                            caps.contains("WPA2", true) -> "WPA2"
                            caps.contains("WPA", true) -> "WPA"
                            caps.contains("WEP", true) -> "WEP"
                            else -> "OPEN/UNKNOWN"
                        } }
                }.getOrNull()
                val oldEncryption = prefs.getString("wifi_encryption", null)
                if (oldSsid == ssid && encryption != null && oldEncryption != null && oldEncryption != encryption) {
                    out += Signal("Enkripsi Wi-Fi berubah", "SSID yang sama berubah dari $oldEncryption menjadi $encryption. Verifikasi router/AP sebelum mengirim data sensitif.", "HIGH")
                }
                val edit = prefs.edit().putString("wifi_ssid", ssid).putString("wifi_bssid", bssid)
                if (encryption != null) edit.putString("wifi_encryption", encryption)
                edit.apply()
            }
        }
        return out
    }
}
