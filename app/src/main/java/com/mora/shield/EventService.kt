package com.mora.shield

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/** Membaca events.log modul secara langsung dan menampilkan notifikasi. */
class EventService : Service() {
    @Volatile private var running = false
    private var proc: Process? = null
    private val lastShown = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notif.ensureChannels(this)
        startForeground(1, Notif.foreground(this))
        if (!running) { startReader(); startStats() }
        return START_STICKY
    }

    private fun startReader() {
        running = true
        val t = Thread {
            while (running) {
                try {
                    val p = ProcessBuilder("su", "-c", "tail -n 0 -F ${Ps.DATA}/events.log")
                        .redirectErrorStream(true).start()
                    proc = p
                    p.inputStream.bufferedReader().forEachLine { handle(it) }
                    p.waitFor()
                } catch (e: Exception) {
                    // coba lagi setelah jeda
                }
                if (running) {
                    try {
                        Thread.sleep(10000)
                    } catch (e: InterruptedException) {
                        return@Thread
                    }
                }
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun startStats() {
        val t = Thread {
            while (running) {
                try {
                    val prefs = getSharedPreferences("mora", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("statnotif", true) && Root.available()) {
                        val st = Notif.statsText(this)
                        getSystemService(android.app.NotificationManager::class.java).notify(1, Notif.foreground(this, st.first, st.second))
                    }
                } catch (e: Exception) {
                    // abaikan; coba lagi menit depan
                }
                try {
                    Thread.sleep(60000)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun handle(line: String) {
        val ev = Ps.parseEv(line) ?: return
        val prefs = getSharedPreferences("mora", Context.MODE_PRIVATE)
        val ignored: Set<String> = prefs.getStringSet("ignored", emptySet<String>()) ?: emptySet<String>()
        val key = ev.type + ":" + ev.pkg
        if (ignored.contains(key)) return
        val now = System.currentTimeMillis()
        val last = lastShown[key] ?: 0L
        if (now - last < 120000L) return
        lastShown[key] = now
        Notif.show(this, ev)
    }

    override fun onDestroy() {
        running = false
        proc?.destroy()
        super.onDestroy()
    }
}
