package com.mora.shield.locationhook

import android.content.Context
import android.os.Build
import android.net.Uri
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal object IdentityHook {
    fun install(context: Context, packageName: String) {
        val b = runCatching {
            context.contentResolver.call(Uri.parse("content://com.mora.shield.location.profile"), "get_identity_profile", null, null)
        }.getOrNull() ?: return
        if (!b.getBoolean("enabled", false)) return
        val values = mapOf(
            "MANUFACTURER" to b.getString("manufacturer"),
            "BRAND" to b.getString("brand"),
            "MODEL" to b.getString("model"),
            "DEVICE" to b.getString("device"),
            "PRODUCT" to b.getString("product"),
            "FINGERPRINT" to b.getString("fingerprint")
        )
        values.forEach { (field, value) ->
            if (!value.isNullOrBlank()) runCatching { XposedHelpers.setStaticObjectField(Build::class.java, field, value) }
                .onFailure { XposedBridge.log("Mora identity: $field unavailable for $packageName: $it") }
        }
        val version = mapOf(
            "RELEASE" to b.getString("release"),
            "SECURITY_PATCH" to b.getString("securityPatch")
        )
        version.forEach { (field, value) ->
            if (!value.isNullOrBlank()) runCatching { XposedHelpers.setStaticObjectField(Build.VERSION::class.java, field, value) }
                .onFailure { XposedBridge.log("Mora identity: VERSION.$field unavailable: $it") }
        }
        XposedBridge.log("Mora per-app identity profile applied for $packageName")
    }
}
