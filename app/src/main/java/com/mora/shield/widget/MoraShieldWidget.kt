package com.mora.shield.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mora.shield.MainActivity
import com.mora.shield.R

class MoraShieldWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(android.content.ComponentName(context, MoraShieldWidget::class.java)).forEach { update(context, manager, it) }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val p = context.getSharedPreferences("mora_traffic_history", Context.MODE_PRIVATE)
        val rx = p.getLong("latest_rx", -1L)
        val tx = p.getLong("latest_tx", -1L)
        val events = p.getInt("events_today", 0)
        val views = RemoteViews(context.packageName, R.layout.widget_mora_shield)
        views.setTextViewText(R.id.widget_rx, "↓ ${format(rx)}")
        views.setTextViewText(R.id.widget_tx, "↑ ${format(tx)}")
        views.setTextViewText(R.id.widget_events, "$events event / 24 jam")
        val open = Intent(context, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(context, 0, open, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        manager.updateAppWidget(id, views)
    }

    private fun format(v: Long): String {
        if (v < 0) return "—"
        if (v < 1024) return "$v B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var n = v.toDouble(); var i = -1
        do { n /= 1024.0; i++ } while (n >= 1024 && i < units.lastIndex)
        return String.format(java.util.Locale.getDefault(), "%.1f %s", n, units[i])
    }

    companion object { const val ACTION_REFRESH = "com.mora.shield.action.REFRESH_WIDGET" }
}
