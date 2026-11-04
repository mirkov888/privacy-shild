package com.mora.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("mora", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service", true)) {
            try {
                context.startForegroundService(Intent(context, EventService::class.java))
            } catch (e: Exception) {
                // sistem menolak mulai dari latar; pemantau akan menyala saat aplikasi dibuka
            }
        }
    }
}
