package com.mora.shield.identity

import android.content.Context
import org.json.JSONObject

data class IdentityOriginalDeviceSnapshot(
    val device: DeviceSnapshot,
    val capturedAtEpochMs: Long
)

/**
 * Keeps a local, read-only baseline of public Android Build labels.
 * It deliberately excludes IMEI, serial, Android ID, MAC, Build fingerprint and attestation.
 */
class IdentityOriginalDeviceSnapshotStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("mora_identity_original_snapshot", Context.MODE_PRIVATE)

    fun load(): IdentityOriginalDeviceSnapshot? =
        preferences.getString(KEY_SNAPSHOT, null)?.let(::decode)

    fun loadOrCapture(): IdentityOriginalDeviceSnapshot =
        load() ?: captureNow()

    /** Replaces the reference baseline only after an explicit user action. */
    fun captureNow(): IdentityOriginalDeviceSnapshot {
        val snapshot = IdentityOriginalDeviceSnapshot(
            device = DeviceSnapshot.capture(),
            capturedAtEpochMs = System.currentTimeMillis()
        )
        preferences.edit().putString(KEY_SNAPSHOT, encode(snapshot).toString()).apply()
        return snapshot
    }

    /** Included in backup files as reference metadata; importing it never rewrites this device. */
    fun exportJson(): JSONObject = encode(loadOrCapture())

    private fun encode(snapshot: IdentityOriginalDeviceSnapshot) = JSONObject().apply {
        put("capturedAtEpochMs", snapshot.capturedAtEpochMs)
        put("device", JSONObject().apply {
            put("manufacturer", snapshot.device.manufacturer)
            put("brand", snapshot.device.brand)
            put("model", snapshot.device.model)
            put("device", snapshot.device.device)
            put("product", snapshot.device.product)
            put("androidRelease", snapshot.device.androidRelease)
            put("sdkInt", snapshot.device.sdkInt)
            put("securityPatch", snapshot.device.securityPatch)
        })
    }

    private fun decode(raw: String): IdentityOriginalDeviceSnapshot? = runCatching {
        val root = JSONObject(raw)
        val device = root.getJSONObject("device")
        val snapshot = IdentityOriginalDeviceSnapshot(
            device = DeviceSnapshot(
                manufacturer = device.getString("manufacturer"),
                brand = device.getString("brand"),
                model = device.getString("model"),
                device = device.getString("device"),
                product = device.getString("product"),
                androidRelease = device.getString("androidRelease"),
                sdkInt = device.getInt("sdkInt"),
                securityPatch = device.getString("securityPatch")
            ),
            capturedAtEpochMs = root.getLong("capturedAtEpochMs")
        )
        require(validateJson(encode(snapshot)) == null)
        snapshot
    }.getOrNull()

    companion object {
        private const val KEY_SNAPSHOT = "original_device_snapshot"

        fun validateJson(root: JSONObject): String? {
            val capturedAt = root.optLong("capturedAtEpochMs", -1L)
            if (capturedAt <= 0L) return "Waktu snapshot perangkat tidak valid"
            val device = root.optJSONObject("device")
                ?: return "Data perangkat pada snapshot tidak ditemukan"
            listOf("manufacturer", "brand", "model", "device", "product", "androidRelease", "securityPatch")
                .forEach { field ->
                    val value = device.optString(field, "")
                    if (value.length > 180 || value.any(Char::isISOControl)) {
                        return "Field $field pada snapshot tidak valid"
                    }
                }
            val sdk = device.optInt("sdkInt", -1)
            if (sdk !in 26..99) return "Versi SDK pada snapshot tidak valid"
            return null
        }
    }
}
