package com.mora.shield.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mora.shield.MainActivity
import com.mora.shield.Ps
import com.mora.shield.R

class MoraShieldWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> updateAll(context)
            ACTION_VAULT_ON, ACTION_VAULT_OFF -> {
                val action = intent.action
                Thread {
                    val result = Ps.run(if (action == ACTION_VAULT_ON) "vault on" else "vault off")
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("vault_status", if (result.ok && action == ACTION_VAULT_ON) "Aktif" else if (result.ok) "Terbuka" else "Gagal")
                        .apply()
                    updateAll(context)
                }.start()
            }
        }
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
        val vault = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("vault_status", "status belum disinkronkan")
        val views = RemoteViews(context.packageName, R.layout.widget_mora_shield)
        views.setTextViewText(R.id.widget_rx, "↓ ${format(rx)}")
        views.setTextViewText(R.id.widget_tx, "↑ ${format(tx)}")
        views.setTextViewText(R.id.widget_events, "${events} event / 24 jam")
        views.setTextViewText(R.id.widget_vault_status, "Brankas: $vault")
        val open = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        views.setOnClickPendingIntent(R.id.widget_vault_on, actionPending(context, ACTION_VAULT_ON, 1))
        views.setOnClickPendingIntent(R.id.widget_vault_off, actionPending(context, ACTION_VAULT_OFF, 2))
        manager.updateAppWidget(id, views)
    }

    private fun actionPending(context: Context, action: String, request: Int): PendingIntent {
        val intent = Intent(context, MoraShieldWidget::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun format(v: Long): String {
        if (v < 0) return "—"
        if (v < 1024) return "$v B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var n = v.toDouble(); var i = -1
        do { n /= 1024.0; i++ } while (n >= 1024 && i < units.lastIndex)
        return String.format(java.util.Locale.getDefault(), "%.1f %s", n, units[i])
    }

    companion object {
        const val ACTION_REFRESH = "com.mora.shield.action.REFRESH_WIDGET"
        const val ACTION_VAULT_ON = "com.mora.shield.action.WIDGET_VAULT_ON"
        const val ACTION_VAULT_OFF = "com.mora.shield.action.WIDGET_VAULT_OFF"
        private const val PREFS = "mora_widget"
    }
}
