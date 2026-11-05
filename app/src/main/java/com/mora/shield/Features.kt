package com.mora.shield

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class TestRow(val name: String, val status: String, val detail: String)
class SusRow(val sev: String, val kind: String, val id: String, val detail: String)

object Known {
    private const val KEY = "sus_known"
    fun set(ctx: Context): Set<String> =
        ctx.getSharedPreferences(Linker.PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun add(ctx: Context, row: SusRow) {
        val p = ctx.getSharedPreferences(Linker.PREFS, Context.MODE_PRIVATE)
        val n = HashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        n.add(row.kind + ":" + row.id)
        p.edit().putStringSet(KEY, n).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(Linker.PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

fun openSystemFor(ctx: Context, r: SusRow) {
    val action = when (r.kind) {
        "ACC" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        "NOTIF" -> "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        "VPN" -> Settings.ACTION_VPN_SETTINGS
        "ADB" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        "ADMIN", "CA" -> Settings.ACTION_SECURITY_SETTINGS
        else -> ""
    }
    try {
        val i = if (action.isNotEmpty()) Intent(action)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.id))
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Store.message = "Tidak bisa membuka pengaturan"
    }
}

fun kindName(k: String): String = when (k) {
    "ACC" -> "Aksesibilitas"
    "NOTIF" -> "Akses notifikasi"
    "ADMIN" -> "Device admin"
    "CA" -> "Sertifikat CA"
    "VPN" -> "VPN"
    "ADB" -> "ADB"
    "IOC" -> "Indikator milikmu"
    "WORD" -> "Kata kunci"
    else -> k
}
