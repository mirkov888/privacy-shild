package com.mora.shield.security

import android.content.Context
import android.content.Intent
import com.mora.shield.widget.MoraShieldWidget

/** Sampling lokal setiap 15 menit; nilai tetap kumulatif sejak boot dan reset setelah reboot. */
object TrafficHistory {
    private const val PREFS = "mora_traffic_history"
    private const val INTERVAL = 15L * 60L * 1000L

    fun record(context: Context, rx: Long, tx: Long, appCounters: List<Triple<String, Long, Long>>, eventCountToday: Int) {
        if (rx < 0 || tx < 0) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_sample", 0L) < INTERVAL) return
        val rows = (p.getString("samples", "") ?: "").split('\n').filter { it.isNotBlank() }.toMutableList()
        rows += "$now,$rx,$tx"
        val kept = rows.takeLast(7 * 24 * 4)
        val apps = appCounters.filter { it.second >= 0 && it.third >= 0 }.joinToString("\n") { "$now,${it.first},${it.second},${it.third}" }
        val appRows = ((p.getString("app_samples", "") ?: "").split('\n').filter { it.isNotBlank() } + apps.lines().filter { it.isNotBlank() }).takeLast(20_000)
        p.edit().putLong("last_sample", now).putString("samples", kept.joinToString("\n"))
            .putString("app_samples", appRows.joinToString("\n"))
            .putLong("latest_rx", rx).putLong("latest_tx", tx).putInt("events_today", eventCountToday).apply()
        context.sendBroadcast(Intent(context, MoraShieldWidget::class.java).setAction(MoraShieldWidget.ACTION_REFRESH))
    }



    fun appDailySeries(context: Context, packageName: String): List<Long> {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val rows = (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("app_samples", "") ?: "")
            .lines().mapNotNull { row ->
                val p = row.split(',')
                if (p.size != 4 || p[1] != packageName) null else runCatching { Quad(p[0].toLong(), p[1], p[2].toLong(), p[3].toLong()) }.getOrNull()
            }.filter { it.ts >= cutoff }.sortedBy { it.ts }
        return rows.zipWithNext().map { (a, b) ->
            val rx = if (b.rx >= a.rx) b.rx - a.rx else b.rx
            val tx = if (b.tx >= a.tx) b.tx - a.tx else b.tx
            rx + tx
        }
    }

    fun appDailyDelta(context: Context): Map<String, Pair<Long, Long>> {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val grouped = (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("app_samples", "") ?: "")
            .lines().mapNotNull { row ->
                val p = row.split(',')
                if (p.size != 4) null else runCatching { Quad(p[0].toLong(), p[1], p[2].toLong(), p[3].toLong()) }.getOrNull()
            }.filter { it.ts >= cutoff }.groupBy { it.pkg }
        return grouped.mapValues { (_, rows) ->
            var rx = 0L; var tx = 0L
            rows.sortedBy { it.ts }.zipWithNext().forEach { (a, b) ->
                rx += if (b.rx >= a.rx) b.rx - a.rx else b.rx
                tx += if (b.tx >= a.tx) b.tx - a.tx else b.tx
            }
            rx to tx
        }
    }

    private data class Quad(val ts: Long, val pkg: String, val rx: Long, val tx: Long)

    fun dailyDelta(context: Context): Pair<Long, Long>? {
        val rows = (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("samples", "") ?: "")
            .lines().mapNotNull { row -> row.split(',').takeIf { it.size == 3 }?.let { p ->
                runCatching { Triple(p[0].toLong(), p[1].toLong(), p[2].toLong()) }.getOrNull()
            } }.filter { it.first >= System.currentTimeMillis() - 24L * 60 * 60 * 1000 }
        if (rows.size < 2) return null
        var rxDelta = 0L
        var txDelta = 0L
        rows.zipWithNext().forEach { (a, b) ->
            // Jika counter turun, anggap reboot/reset dan hitung nilai setelah reset sebagai segmen baru.
            rxDelta += if (b.second >= a.second) b.second - a.second else b.second
            txDelta += if (b.third >= a.third) b.third - a.third else b.third
        }
        return rxDelta to txDelta
    }
}
