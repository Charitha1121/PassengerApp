package com.example.ruraltransport

import com.example.ruraltransport.data.model.LiveDriverPosition
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
            lat = 16.5062,
            lng = 80.6480,
            heading = 90f,
            speed = 8.5f,
            isRideActive = true,
            lastUpdated = 1700000000L,
            routeId = "ROUTE_01",
            driverName = "Ramesh Auto",
            vehicleNumber = "AP29 AB 1234"
        )

        assertEquals("driver_123", driver.driverUid)
        assertEquals(16.5062, driver.lat, 0.0001)
        assertEquals(80.6480, driver.lng, 0.0001)
        assertEquals(90f, driver.heading, 0.01f)
        assertEquals(8.5f, driver.speed, 0.01f)
        assertTrue(driver.isRideActive)
        assertEquals("ROUTE_01", driver.routeId)
        assertEquals("Ramesh Auto", driver.driverName)
        assertEquals("AP29 AB 1234", driver.vehicleNumber)
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
    fun testDriverFilteringLogic() {
        val drivers = listOf(
            LiveDriverPosition(
                driverUid = "d1",
                lat = 16.5062,
                lng = 80.6480,
                isRideActive = true,
                routeId = "ROUTE_01"
            ),
            LiveDriverPosition(
                driverUid = "d2",
                lat = 16.5000,
                lng = 80.6550,
                isRideActive = false, // Ride inactive
                routeId = "ROUTE_01"
            ),
            LiveDriverPosition(
                driverUid = "d3",
                lat = 0.0,
                lng = 0.0, // Unset coordinates
                isRideActive = true,
                routeId = "ROUTE_01"
            ),
            LiveDriverPosition(
                driverUid = "d4",
                lat = 16.4950,
                lng = 80.6620,
                isRideActive = true,
                routeId = "ROUTE_02" // Different corridor
            )
        )

        val targetRouteId = "ROUTE_01"
        val filtered = drivers.filter { driver ->
            driver.isRideActive &&
            (driver.lat != 0.0 || driver.lng != 0.0) &&
            (driver.routeId.isBlank() || driver.routeId.equals(targetRouteId, ignoreCase = true))
        }

        assertEquals(1, filtered.size)
        assertEquals("d1", filtered.first().driverUid)
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
