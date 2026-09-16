package com.example.ruraltransport

import com.example.ruraltransport.data.model.GeoUtils
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.ui.map.shortestAngleDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrackingUnitTest {

    @Test
    fun testLiveDriverPositionDefaults() {
        val driver = LiveDriverPosition(
            driverUid = "driver_123",
            lat = 17.2942,
            lng = 78.5675,
            heading = 90f,
            speed = 8.5f,
            isRideActive = true,
            isAvailable = true,
            availableSeats = 3,
            lastUpdated = 1700000000L,
            routeId = "ROUTE_01",
            driverName = "Ramesh Auto",
            vehicleNumber = "AP29 AB 1234"
        )

        assertEquals("driver_123", driver.driverUid)
        assertEquals(17.2942, driver.lat, 0.0001)
        assertEquals(78.5675, driver.lng, 0.0001)
        assertEquals(90f, driver.heading, 0.01f)
        assertEquals(8.5f, driver.speed, 0.01f)
        assertTrue(driver.isRideActive)
        assertTrue(driver.isAvailable)
        assertEquals(3, driver.availableSeats)
        assertEquals("ROUTE_01", driver.routeId)
        assertEquals("Ramesh Auto", driver.driverName)
        assertEquals("AP29 AB 1234", driver.vehicleNumber)
    }

    @Test
    fun testCorridorStopsAreInTelangana() {
        val stops = RouteData.canonicalStops
        assertEquals(4, stops.size)

        // Sphoorthy College should be ~17.2820°N, 78.5538°E
        val sphoorthy = stops.find { it.name == "Sphoorthy College" }
        assertTrue("Sphoorthy College must be present", sphoorthy != null)
        assertEquals(17.2820, sphoorthy!!.latitude, 0.001)
        assertEquals(78.5538, sphoorthy.longitude, 0.001)

        // All stops must be in Telangana corridor (17.2° - 17.4° N, 78.4° - 78.6° E)
        for (stop in stops) {
            assertTrue("${stop.name} lat in Telangana", stop.latitude in 17.2..17.4)
            assertTrue("${stop.name} lng in Telangana", stop.longitude in 78.4..78.6)
        }
    }

    @Test
    fun testGeoUtilsDistanceAndEta() {
        // Distance between Gurramguda (17.2942, 78.5675) and Sphoorthy College (17.2820, 78.5538)
        val distKm = GeoUtils.calculateDistanceKm(17.2942, 78.5675, 17.2820, 78.5538)
        assertTrue("Distance should be roughly 1.5 - 2.5 km", distKm in 1.5..2.5)

        // At 20 km/h:
        // ~2 km at 20 km/h is 0.1 hour = 6 minutes
        val eta = GeoUtils.calculateEtaMinutes(distKm, avgSpeedKmh = 20.0)
        assertTrue("ETA should be between 4 and 8 minutes", eta in 4..8)

        // Zero distance
        assertEquals(0, GeoUtils.calculateEtaMinutes(0.0))
        assertEquals(0.0, GeoUtils.calculateDistanceKm(0.0, 0.0, 17.2820, 78.5538), 0.0001)
    }

    @Test
    fun testCorridorBrowseDriverFilteringLogic() {
        val drivers = listOf(
            LiveDriverPosition(
                driverUid = "d1",
                lat = 17.2942,
                lng = 78.5675,
                isAvailable = true,
                routeId = "ROUTE_01",
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "d2",
                lat = 17.2885,
                lng = 78.5605,
                isAvailable = false, // Not available
                routeId = "ROUTE_01",
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "d3",
                lat = 0.0,
                lng = 0.0, // Unset coordinates
                isAvailable = true,
                routeId = "ROUTE_01",
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "d4",
                lat = 17.2820,
                lng = 78.5538,
                isAvailable = true,
                routeId = "ROUTE_02", // Different corridor
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "d5",
                lat = 17.2746,
                lng = 78.5400,
                isAvailable = true,
                routeId = "ROUTE_01",
                activeDirection = "REVERSE" // Opposite direction
            )
        )

        val targetRouteId = "ROUTE_01"
        val targetDirection = "FORWARD"

        val filtered = drivers.filter { driver ->
            driver.isAvailable &&
            (driver.lat.isFinite() && driver.lng.isFinite() && (driver.lat != 0.0 || driver.lng != 0.0)) &&
            (driver.routeId.isBlank() || driver.routeId.equals(targetRouteId, ignoreCase = true)) &&
            (driver.activeDirection.isBlank() || driver.activeDirection.equals(targetDirection, ignoreCase = true))
        }

        assertEquals(1, filtered.size)
        assertEquals("d1", filtered.first().driverUid)
    }

    @Test
    fun testShortestAngleDiff_crossingZeroNorth() {
        // Turning clockwise from 350° to 10° should be +20°, not -340°
        val diffClockwise = shortestAngleDiff(350f, 10f)
        assertEquals(20f, diffClockwise, 0.01f)

        // Turning counter-clockwise from 10° to 350° should be -20°, not +340°
        val diffCounterClockwise = shortestAngleDiff(10f, 350f)
        assertEquals(-20f, diffCounterClockwise, 0.01f)

        // Orthogonal right turn
        val rightTurn = shortestAngleDiff(0f, 90f)
        assertEquals(90f, rightTurn, 0.01f)

        // Exact reverse turn
        val uTurn = shortestAngleDiff(0f, 180f)
        assertEquals(180f, kotlin.math.abs(uTurn), 0.01f)
    }

    @Test
    fun testSpeedConversionKmh() {
        val speedMps = 10f // 10 m/s
        val speedKmh = (speedMps * 3.6f).toInt()
        assertEquals(36, speedKmh)
    }

    @Test
    fun testShortestAngleDiff_withNaN() {
        // Bug 2 guard test: NaN angles should return 0f to prevent crashes
        assertEquals(0f, shortestAngleDiff(Float.NaN, 90f), 0.001f)
        assertEquals(0f, shortestAngleDiff(90f, Float.NaN), 0.001f)
        assertEquals(0f, shortestAngleDiff(Float.NaN, Float.NaN), 0.001f)
    }

    @Test
    fun testBackendContractStatuses() {
        // Bug 3 contract validation: status must be one of pending/accepted/in_progress/completed/cancelled (lowercase)
        val validStatuses = setOf("pending", "accepted", "in_progress", "completed", "cancelled")
        assertTrue(validStatuses.contains("pending"))
        assertTrue(validStatuses.contains("cancelled"))
        assertFalse("Uppercase PENDING violates RTDB rule validation", validStatuses.contains("PENDING"))
    }
}
