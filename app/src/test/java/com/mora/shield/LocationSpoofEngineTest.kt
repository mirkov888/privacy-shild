package com.mora.shield

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LocationSpoofEngineTest {
    @Test fun validatesCoordinateAndIntervalBounds() {
        val valid = PerAppLocationProfile("org.example.target", LocationMode.RADIUS, -6.2, 106.8, 500, 15)
        assertNull(LocationSpoofEngine.validate(valid))
        assertNotNull(LocationSpoofEngine.validate(valid.copy(latitude = 91.0)))
        assertNotNull(LocationSpoofEngine.validate(valid.copy(longitude = 181.0)))
        assertNotNull(LocationSpoofEngine.validate(valid.copy(radiusMeters = 0)))
        assertNotNull(LocationSpoofEngine.validate(valid.copy(intervalMinutes = 1441)))
    }

    @Test fun radiusCoordinatesRemainWithinConfiguredDistance() {
        val centerLat = -7.8
        val centerLon = 110.4
        repeat(500) {
            val point = LocationSpoofEngine.randomCoordinateInRadius(centerLat, centerLon, 2500)
            val dLat = Math.toRadians(point.latitude - centerLat)
            val dLon = Math.toRadians(point.longitude - centerLon)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(centerLat)) *
                cos(Math.toRadians(point.latitude)) * sin(dLon / 2).pow(2)
            val distance = 2 * 6_371_000.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
            assertTrue("distance=$distance", distance <= 2500.01)
        }
    }

    @Test fun randomWorldCoordinatesStayOnValidGlobe() {
        repeat(500) {
            val point = LocationSpoofEngine.randomWorldCoordinate()
            assertTrue(point.latitude in -90.0..90.0)
            assertTrue(point.longitude in -180.0..180.0)
        }
    }

    @Test fun randomCoordinateIsStableInsideIntervalAndChangesAfterInterval() {
        val p = PerAppLocationProfile("org.example.target", LocationMode.RANDOM, intervalMinutes = 1, enabled = true)
        val a = LocationSpoofEngine.coordinateFor(p, 1_000L)
        val b = LocationSpoofEngine.coordinateFor(p, 30_000L)
        val c = LocationSpoofEngine.coordinateFor(p, 61_000L)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
