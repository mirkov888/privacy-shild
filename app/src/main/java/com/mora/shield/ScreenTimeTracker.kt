package com.mora.shield

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily screen-on / standby accumulator.
 * Standby means device interactive session with display off; no time is counted while powered off.
 */
object ScreenTimeTracker {
    private const val PREFS = "mora_screen_time"
    private const val KEY_DAY = "day"
    private const val KEY_ON = "screen_on_ms"
    private const val KEY_OFF = "standby_ms"
    private const val KEY_LAST = "last_sample_ms"
    private const val KEY_INTERACTIVE = "interactive"

    data class Snapshot(val screenOnMs: Long, val standbyMs: Long, val isScreenOn: Boolean)

    @Synchronized
    fun setInteractive(context: Context, interactive: Boolean): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        rollDay(p)
        val now = System.currentTimeMillis()
        accrue(p, now)
        p.edit().putBoolean(KEY_INTERACTIVE, interactive).putLong(KEY_LAST, now).apply()
        return snapshot(context)
    }

    @Synchronized
    fun tick(context: Context, interactive: Boolean): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        rollDay(p)
        val now = System.currentTimeMillis()
        val storedInteractive = p.getBoolean(KEY_INTERACTIVE, interactive)
        if (storedInteractive != interactive) {
            accrue(p, now)
            p.edit().putBoolean(KEY_INTERACTIVE, interactive).putLong(KEY_LAST, now).apply()
        } else {
            accrue(p, now)
            p.edit().putLong(KEY_LAST, now).apply()
        }
        return snapshot(context)
    }

    @Synchronized
    fun snapshot(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        rollDay(p)
        return Snapshot(
            p.getLong(KEY_ON, 0L),
            p.getLong(KEY_OFF, 0L),
            p.getBoolean(KEY_INTERACTIVE, true)
        )
    }

    private fun accrue(p: android.content.SharedPreferences, now: Long) {
        val last = p.getLong(KEY_LAST, now)
        val delta = (now - last).coerceIn(0L, 90_000L)
        if (p.getBoolean(KEY_INTERACTIVE, true)) {
            p.edit().putLong(KEY_ON, p.getLong(KEY_ON, 0L) + delta).apply()
        } else {
            p.edit().putLong(KEY_OFF, p.getLong(KEY_OFF, 0L) + delta).apply()
        }
    }

    private fun rollDay(p: android.content.SharedPreferences) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (p.getString(KEY_DAY, null) != today) {
            p.edit()
                .putString(KEY_DAY, today)
                .putLong(KEY_ON, 0L)
                .putLong(KEY_OFF, 0L)
                .putLong(KEY_LAST, System.currentTimeMillis())
                .putBoolean(KEY_INTERACTIVE, true)
                .apply()
        }
    }

    fun format(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
