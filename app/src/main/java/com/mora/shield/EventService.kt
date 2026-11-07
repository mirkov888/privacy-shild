package com.mora.shield

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.os.IBinder

/** Membaca events.log modul secara langsung dan menampilkan notifikasi. */
class EventService : Service() {
    @Volatile private var running = false
    private var proc: Process? = null
    private val lastShown = HashMap<String, Long>()
    private var screenReceiverRegistered = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> ScreenTimeTracker.setInteractive(context, true)
                Intent.ACTION_SCREEN_OFF -> ScreenTimeTracker.setInteractive(context, false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notif.ensureChannels(this)
        startForeground(1, Notif.foreground(this))
        if (!running) { startReader(); startStats() }
        registerScreenReceiver()
        return START_STICKY
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
            ScreenTimeTracker.setInteractive(this, getSystemService(PowerManager::class.java).isInteractive)
        } catch (_: Exception) {
            // Screen Time card still samples while the UI is open.
        }
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
                        val power = getSystemService(PowerManager::class.java)
                        val screen = ScreenTimeTracker.tick(this, power.isInteractive)
                        val screenLine = "Layar ${ScreenTimeTracker.format(screen.screenOnMs)} · standby ${ScreenTimeTracker.format(screen.standbyMs)}"
                        getSystemService(android.app.NotificationManager::class.java).notify(
                            1,
                            Notif.foreground(this, st.first, st.second + "\n" + screenLine)
                        )
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
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        super.onDestroy()
    }
}
