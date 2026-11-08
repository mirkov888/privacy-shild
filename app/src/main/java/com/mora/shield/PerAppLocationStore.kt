package com.mora.shield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PerAppLocationStore {
    const val PREFS = "per_app_location_profiles"
    private fun key(pkg: String, field: String) = "$pkg:$field"

    fun load(context: Context, pkg: String): PerAppLocationProfile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching { LocationMode.valueOf(p.getString(key(pkg, "mode"), "REAL") ?: "REAL") }.getOrDefault(LocationMode.REAL)
        return PerAppLocationProfile(
            packageName = pkg,
            mode = mode,
            latitude = java.lang.Double.longBitsToDouble(p.getLong(key(pkg, "lat"), java.lang.Double.doubleToRawLongBits(0.0))),
            longitude = java.lang.Double.longBitsToDouble(p.getLong(key(pkg, "lon"), java.lang.Double.doubleToRawLongBits(0.0))),
            radiusMeters = p.getInt(key(pkg, "radius"), 1000),
            intervalMinutes = p.getInt(key(pkg, "interval"), 15),
            enabled = p.getBoolean(key(pkg, "enabled"), false)
        )
    }

    fun save(context: Context, profile: PerAppLocationProfile) {
        require(LocationSpoofEngine.validate(profile) == null)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(profile.packageName, "mode"), profile.mode.name)
            .putLong(key(profile.packageName, "lat"), java.lang.Double.doubleToRawLongBits(profile.latitude))
            .putLong(key(profile.packageName, "lon"), java.lang.Double.doubleToRawLongBits(profile.longitude))
            .putInt(key(profile.packageName, "radius"), profile.radiusMeters)
            .putInt(key(profile.packageName, "interval"), profile.intervalMinutes)
            .putBoolean(key(profile.packageName, "enabled"), profile.enabled)
            .apply()
        LocationSpoofEngine.clear(profile.packageName)
    }

    fun presets(context: Context, pkg: String): List<LocationPreset> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(pkg, "presets"), "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LocationPreset(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    mode = LocationMode.valueOf(item.getString("mode")),
                    latitude = item.optDouble("latitude", 0.0),
                    longitude = item.optDouble("longitude", 0.0),
                    radiusMeters = item.optInt("radiusMeters", 1000),
                    intervalMinutes = item.optInt("intervalMinutes", 15)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePreset(context: Context, pkg: String, preset: LocationPreset) {
        require(preset.name.isNotBlank() && preset.name.length <= 48)
        val profile = PerAppLocationProfile(pkg, preset.mode, preset.latitude, preset.longitude, preset.radiusMeters, preset.intervalMinutes)
        require(LocationSpoofEngine.validate(profile) == null)
        val current = presets(context, pkg).filterNot { it.id == preset.id }.toMutableList()
        current.add(preset)
        writePresets(context, pkg, current)
    }

    fun deletePreset(context: Context, pkg: String, presetId: String) {
        writePresets(context, pkg, presets(context, pkg).filterNot { it.id == presetId })
    }

    private fun writePresets(context: Context, pkg: String, items: List<LocationPreset>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("mode", item.mode.name)
                put("latitude", item.latitude); put("longitude", item.longitude)
                put("radiusMeters", item.radiusMeters); put("intervalMinutes", item.intervalMinutes)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(pkg, "presets"), array.toString()).apply()
    }

    fun packageNames(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .mapNotNull { entry -> if (':' in entry) entry.substringBeforeLast(':') else null }.toSet()

    fun disableAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        packageNames(context).forEach { pkg -> editor.putBoolean(key(pkg, "enabled"), false) }
        editor.apply()
        LocationSpoofEngine.clearAll()
    }

    fun recordBackendHeartbeat(context: Context, pkg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(pkg, "backend_heartbeat"), System.currentTimeMillis()).apply()
    }

    fun backendHeartbeat(context: Context, pkg: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key(pkg, "backend_heartbeat"), 0L)
}
