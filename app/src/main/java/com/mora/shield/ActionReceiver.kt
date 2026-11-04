package com.mora.shield

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("pkg") ?: return
        val type = intent.getStringExtra("type") ?: ""
        val nid = intent.getIntExtra("nid", 0)
        val action = intent.action ?: return
        context.getSystemService(NotificationManager::class.java).cancel(nid)
        val pending = goAsync()
        Thread {
            var msg = ""
            try {
                when (action) {
                    Notif.ACT_BLOCK -> msg = if (Ps.block(pkg)) "Internet diblokir: $pkg" else "Gagal memblokir"
                    Notif.ACT_ALLOW -> msg = if (Ps.allowNew(pkg)) "Diizinkan: $pkg" else "Gagal mengizinkan"
                    Notif.ACT_IGNORE -> {
                        val prefs = context.getSharedPreferences("mora", Context.MODE_PRIVATE)
                        val s = HashSet<String>(prefs.getStringSet("ignored", emptySet<String>()) ?: emptySet<String>())
                        s.add("$type:$pkg")
                        prefs.edit().putStringSet("ignored", s).apply()
                        msg = "Diabaikan: $pkg"
                    }
                }
            } finally {
                pending.finish()
            }
            if (msg.isNotEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
