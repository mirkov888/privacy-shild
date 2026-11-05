package com.mora.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Notif {
    const val CH_SERVICE = "svc"
    const val CH_STATS = "stats"
    const val CH_ACCESS = "access"
    const val CH_ALERT = "alert"
    const val ACT_BLOCK = "com.mora.shield.BLOCK"
    const val ACT_ALLOW = "com.mora.shield.ALLOW"
    const val ACT_IGNORE = "com.mora.shield.IGNORE"

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Pemantau berjalan", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STATS, "Statistik Mora Shield", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ACCESS, "Akses sensor dan data", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Peringatan penting", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun foreground(c: Context, title: String = "Mora Shield memantau", body: String = "Menunggu kejadian dari modul"): Notification {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(c, CH_STATS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(body.lines().firstOrNull() ?: "")
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    /** Pasangan (judul, isi) statistik gaya TCCM. Memanggil su, jadi jangan di thread utama. */
    fun statsText(c: Context): Pair<String, String> {
        val cfg = Ps.sync()
        val score = SCORE_KEYS.count { cfg.on(it) } * 100 / SCORE_KEYS.size
        val startDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }.timeInMillis / 1000
        val evs = Ps.events(1500).filter { it.ts >= startDay }
        fun n(t: String) = evs.count { it.type == t }
        val blocked = (cfg["fw_pkgs"] ?: "").split(",").count { it.isNotBlank() }
        val batt = KernelManager.battery()
        val ram = KernelManager.ramPercent()
        val sb = StringBuilder()
        sb.append("Hari ini: mikrofon ${n("MIC")} · kamera ${n("CAM")} · lokasi ${n("LOC")} · kontak ${n("CONTACTS")}\n")
        sb.append("Data keluar besar ${n("NETBIG")} · aplikasi baru ${n("NEWAPP")} · diblokir $blocked\n")
        val bt = if (batt.first >= 0) "Baterai ${batt.first}%" + (if (batt.second >= 0) " · ${batt.second}°C" else "") else ""
        val rm = if (ram >= 0) "RAM $ram%" else ""
        val line3 = listOf(bt, rm).filter { it.isNotEmpty() }.joinToString(" · ")
        if (line3.isNotEmpty()) sb.append(line3).append("\n")
        val patch = cfg["patch"] ?: ""
        sb.append("Modul ${cfg["mod_version"] ?: "?"}" + (if (patch.isNotEmpty()) " · patch $patch" else "") +
            (if ((cfg["vpn_active"] ?: "0") != "0") " · VPN aktif" else ""))
        return Pair("Mora Shield · skor $score% · ${evs.size} kejadian", sb.toString())
    }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        pkg
    }

    private fun act(c: Context, action: String, pkg: String, type: String, id: Int): PendingIntent {
        val i = Intent(c, ActionReceiver::class.java)
            .setAction(action)
            .putExtra("pkg", pkg)
            .putExtra("type", type)
            .putExtra("nid", id)
        return PendingIntent.getBroadcast(
            c, (action + pkg + type).hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun show(c: Context, ev: Ps.Ev) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val name = label(c, ev.pkg)
        val what = when (ev.type) {
            "MIC" -> "memakai mikrofon"
            "CAM" -> "memakai kamera"
            "LOC" -> "mengakses lokasi"
            "CONTACTS" -> "membaca kontak"
            "SMS" -> "membaca SMS"
            "CALLLOG" -> "membaca log panggilan"
            "NETBIG" -> "mengirim data besar (${ev.detail})"
            "NEWAPP" -> "aplikasi baru diblokir dari internet"
            "ACTION" -> ev.detail
            "BOOTGUARD" -> ev.detail
            else -> ev.type
        }
        val urgent = ev.type == "NETBIG" || ev.type == "NEWAPP" || ev.type == "ACTION" || ev.type == "BOOTGUARD"
        val id = (ev.type + ev.pkg).hashCode()
        val b = Notification.Builder(c, if (urgent) CH_ALERT else CH_ACCESS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(if (ev.pkg.isEmpty()) "Mora Shield" else name)
            .setContentText(what)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    c, 1, Intent(c, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        if (ev.pkg.isNotEmpty() && ev.type != "ACTION" && ev.type != "BOOTGUARD") {
            if (ev.type == "NEWAPP") {
                b.addAction(android.R.drawable.ic_menu_add, "Izinkan internet", act(c, Notif.ACT_ALLOW, ev.pkg, ev.type, id))
            }
            b.addAction(android.R.drawable.ic_delete, "Blokir internet", act(c, Notif.ACT_BLOCK, ev.pkg, ev.type, id))
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Abaikan", act(c, Notif.ACT_IGNORE, ev.pkg, ev.type, id))
        }
        nm.notify(id, b.build())
    }
}
