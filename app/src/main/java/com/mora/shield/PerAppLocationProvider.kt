package com.mora.shield

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/** Read-only profile bridge for the LSPosed hook running inside the target app UID. */
class PerAppLocationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return Bundle()
        val callerPackages = ctx.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (callerPackages.size != 1) return Bundle()
        val caller = callerPackages.single()
        if (caller == ctx.packageName || !AppControlManager.isSafeThirdPartyPackage(caller)) return Bundle()
        return when (method) {
            "backend_heartbeat" -> {
                PerAppLocationStore.recordBackendHeartbeat(ctx, caller)
                Bundle().apply { putBoolean("ok", true) }
            }
            "get_profile" -> {
                val profile = PerAppLocationStore.load(ctx, caller)
                Bundle().apply {
                    putBoolean("enabled", profile.enabled)
                    putString("mode", profile.mode.name)
                    putDouble("latitude", profile.latitude)
                    putDouble("longitude", profile.longitude)
                    putInt("radiusMeters", profile.radiusMeters)
                    putInt("intervalMinutes", profile.intervalMinutes)
                    putLong("updatedAtMs", System.currentTimeMillis())
                }
            }
            else -> Bundle()
        }
    }
}
