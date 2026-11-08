package com.mora.shield.security

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mora.shield.MainActivity
import com.mora.shield.Notif
import com.mora.shield.R
import com.mora.shield.core.MoraEvent

/**
 * Local, opt-in event rules. The only automatic action is a notification:
 * no shell commands, app blocking, permission changes, or remote transmission.
 */
object LocalRulesEngine {
    const val MIN_HIGH = "HIGH"
    const val MIN_MEDIUM = "MEDIUM"
    private const val PREFS = "mora_local_rules"
    private const val COOLDOWN_MS = 5L * 60L * 1000L

    data class Config(
        val enabled: Boolean = false,
        val minimumSeverity: String = MIN_HIGH
    )

    fun config(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val minimum = prefs.getString("minimum_severity", MIN_HIGH)
            ?.takeIf { it == MIN_HIGH || it == MIN_MEDIUM } ?: MIN_HIGH
        return Config(prefs.getBoolean("enabled", false), minimum)
    }

    fun save(context: Context, config: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putString("minimum_severity", if (config.minimumSeverity == MIN_MEDIUM) MIN_MEDIUM else MIN_HIGH)
            .apply()
    }

    fun evaluate(context: Context, event: MoraEvent) {
        val cfg = config(context)
        if (!cfg.enabled) return
        val rank = severityRank(event.severity.wireValue)
        val threshold = if (cfg.minimumSeverity == MIN_MEDIUM) 2 else 3
        if (rank < threshold) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val key = "last_" + listOf(event.sourcePackage, event.eventType, event.affectedPackage).joinToString("_").hashCode()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(key, 0L) < COOLDOWN_MS) return

        val open = PendingIntent.getActivity(
            context,
            7301,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val affected = event.affectedPackage.takeIf { it.isNotBlank() } ?: event.sourcePackage
        val title = event.title.ifBlank { event.eventType }.take(100)
        val body = "$affected · ${event.severity.wireValue.uppercase()} · ${event.eventType}"
        runCatching {
            Notif.ensureChannels(context)
            NotificationManagerCompat.from(context).notify(
                key.hashCode(),
                NotificationCompat.Builder(context, Notif.CH_ALERT)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
            )
            prefs.edit().putLong(key, now).apply()
        }
    }

    private fun severityRank(value: String): Int = when (value.lowercase()) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 0
    }
}
