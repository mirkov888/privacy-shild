package com.mora.shield.locationhook

import android.content.Context
import android.net.Uri
import android.os.Build
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Applies only public, non-security Build labels to an explicitly selected app process.
 * Build.FINGERPRINT, SDK/release, security patch, hardware IDs and attestation are never changed.
 */
internal object IdentityHook {
    private val supportedFields = listOf(
        "MANUFACTURER",
        "BRAND",
        "MODEL",
        "DEVICE",
        "PRODUCT"
    )

    fun install(context: Context, packageName: String) {
        val profile = runCatching {
            context.contentResolver.call(
                Uri.parse("content://com.mora.shield.location.profile"),
                "get_identity_profile",
                null,
                null
            )
        }.getOrNull() ?: return

        if (!profile.getBoolean("enabled", false)) return

        val values = supportedFields.associateWith { field ->
            when (field) {
                "MANUFACTURER" -> profile.getString("manufacturer")
                "BRAND" -> profile.getString("brand")
                "MODEL" -> profile.getString("model")
                "DEVICE" -> profile.getString("device")
                "PRODUCT" -> profile.getString("product")
                else -> null
            }
        }

        values.forEach { (field, value) ->
            if (!value.isNullOrBlank() && value.length <= 180 &&
                value.none { it == '\n' || it == '\r' || it == '\u0000' }
            ) {
                runCatching {
                    XposedHelpers.setStaticObjectField(Build::class.java, field, value)
                }.onFailure {
                    XposedBridge.log("Mora identity: $field unavailable for $packageName: $it")
                }
            }
        }

        XposedBridge.log(
            "Mora compatibility identity applied to $packageName; security/build identity and device identifiers left unchanged"
        )
    }
}
