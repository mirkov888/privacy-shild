package com.mora.shield

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

enum class LocationMode(val title: String) {
    REAL("Lokasi asli"), STATIC("Lokasi statis"), RANDOM("Lokasi random"), RADIUS("Random dalam radius")
}

data class PerAppLocationProfile(
    val packageName: String,
    val mode: LocationMode = LocationMode.REAL,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 1000,
    val intervalMinutes: Int = 15,
    val enabled: Boolean = false
)

data class LocationPreset(
    val id: String,
    val name: String,
    val mode: LocationMode,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val intervalMinutes: Int
)

data class LocationCoordinate(val latitude: Double, val longitude: Double)

object LocationSpoofEngine {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private val random = SecureRandom()
    private data class CacheEntry(val bucket: Long, val coordinate: LocationCoordinate)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun validate(profile: PerAppLocationProfile): String? {
        if (!profile.packageName.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) return "Package aplikasi tidak valid."
        if (profile.mode == LocationMode.STATIC || profile.mode == LocationMode.RADIUS) {
            if (!profile.latitude.isFinite() || profile.latitude !in -90.0..90.0) return "Latitude harus -90 sampai 90."
            if (!profile.longitude.isFinite() || profile.longitude !in -180.0..180.0) return "Longitude harus -180 sampai 180."
        }
        if (profile.mode == LocationMode.RADIUS && profile.radiusMeters !in 1..500_000) return "Radius harus 1–500000 meter."
        if (profile.mode == LocationMode.RANDOM || profile.mode == LocationMode.RADIUS) {
            if (profile.intervalMinutes !in 1..1440) return "Interval harus 1–1440 menit."
        }
        return null
    }

    /** Uniform random point over the whole sphere, not a biased latitude/longitude rectangle. */
    fun randomWorldCoordinate(): LocationCoordinate {
        val z = random.nextDouble() * 2.0 - 1.0
        val latitude = Math.toDegrees(asin(z))
        val longitude = random.nextDouble() * 360.0 - 180.0
        return LocationCoordinate(latitude, longitude)
    }

    /** Uniform-by-area point inside a spherical radius around the configured center. */
    fun randomCoordinateInRadius(
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Int
    ): LocationCoordinate {
        require(centerLatitude.isFinite() && centerLatitude in -90.0..90.0)
        require(centerLongitude.isFinite() && centerLongitude in -180.0..180.0)
        require(radiusMeters in 1..500_000)
        val distance = sqrt(random.nextDouble()) * radiusMeters.toDouble()
        val bearing = random.nextDouble() * 2.0 * PI
        val angularDistance = distance / EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(centerLatitude)
        val lon1 = Math.toRadians(centerLongitude)
        val lat2 = asin(sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )
        val normalizedLon = ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0
        return LocationCoordinate(Math.toDegrees(lat2).coerceIn(-90.0, 90.0), normalizedLon)
    }

    /** Returns one stable coordinate per package/interval bucket. REAL returns null. */
    fun coordinateFor(profile: PerAppLocationProfile, elapsedRealtimeMs: Long): LocationCoordinate? {
        if (!profile.enabled || profile.mode == LocationMode.REAL) return null
        if (profile.mode == LocationMode.STATIC) return LocationCoordinate(profile.latitude, profile.longitude)
        val intervalMs = profile.intervalMinutes.coerceIn(1, 1440) * 60_000L
        val bucket = elapsedRealtimeMs.coerceAtLeast(0L) / intervalMs
        val previous = cache[profile.packageName]
        if (previous != null && previous.bucket == bucket) return previous.coordinate
        val next = if (profile.mode == LocationMode.RADIUS) {
            randomCoordinateInRadius(profile.latitude, profile.longitude, profile.radiusMeters)
        } else {
            randomWorldCoordinate()
        }
        cache[profile.packageName] = CacheEntry(bucket, next)
        return next
    }

    fun clear(packageName: String) { cache.remove(packageName) }
    fun clearAll() { cache.clear() }
}
