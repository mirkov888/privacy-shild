package com.mora.shield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object GuestModeScheduler {
    private const val PREFS = "mora_guest_mode"
    private const val KEY_UNTIL = "until_ms"
    private const val REQUEST = 7016

    fun schedule(context: Context, minutes: Int) {
        val until = System.currentTimeMillis() + minutes.coerceIn(15, 720) * 60_000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_UNTIL, until).apply()
        alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending(context))
    }

    fun cancel(context: Context) {
        alarm(context).cancel(pending(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_UNTIL).apply()
    }

    fun reschedule(context: Context) {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)
        if (until <= 0L) return
        if (until <= System.currentTimeMillis()) {
            context.sendBroadcast(Intent(context, GuestModeReceiver::class.java).setAction(GuestModeReceiver.ACTION_EXPIRE))
        } else {
            alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pending(context))
        }
    }

    private fun alarm(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, GuestModeReceiver::class.java).setAction(GuestModeReceiver.ACTION_EXPIRE)
        return PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
