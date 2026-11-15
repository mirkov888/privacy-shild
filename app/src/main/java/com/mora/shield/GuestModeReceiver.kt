package com.mora.shield

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class GuestModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXPIRE) return
        val pending = goAsync()
        thread {
            try {
                val result = Ps.run("vault off")
                GuestModeScheduler.cancel(context)
                Notif.ensureChannels(context)
                val body = if (result.ok) "Brankas sementara dipulihkan otomatis." else "Pemulihan brankas gagal: ${result.out.take(100)}"
                val notification = Notification.Builder(context, Notif.CH_ALERT)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle("Mode pinjam HP selesai")
                    .setContentText(body)
                    .setAutoCancel(true)
                    .build()
                context.getSystemService(NotificationManager::class.java).notify(4, notification)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EXPIRE = "com.mora.shield.action.GUEST_MODE_EXPIRE"
    }
}
