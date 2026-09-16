package com.example.ruraltransport

import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDirectionAndDemandTest {

    @Test
    fun testCorridorStopsOrder() {
        assertEquals(4, RouteData.stops.size)
        assertEquals("Gurramguda", RouteData.stops[0])
        assertEquals("Jay Suryapatnam", RouteData.stops[1])
        assertEquals("Sphoorthy College", RouteData.stops[2])
        assertEquals("Nadergul", RouteData.stops[3])
    }

    @Test
    fun testStopIndexResolution() {
        assertEquals(0, RouteData.indexOf("Gurramguda"))
        assertEquals(1, RouteData.indexOf("Jay Suryapatnam"))
        assertEquals(2, RouteData.indexOf("Sphoorthy College"))
        assertEquals(3, RouteData.indexOf("Nadergul"))

        // By Stop ID
        assertEquals(0, RouteData.indexOf("STOP_01"))
        assertEquals(1, RouteData.indexOf("STOP_02"))
        assertEquals(2, RouteData.indexOf("STOP_03"))
        assertEquals(3, RouteData.indexOf("STOP_04"))

        // Case insensitivity
        assertEquals(0, RouteData.indexOf("gurramguda"))
        assertEquals(3, RouteData.indexOf("nadergul"))
    }

    @Test
    fun testDirectionComputation() {
        // Forward journeys
        assertEquals(
            RouteDirection.FORWARD,
            RouteData.getDirection("Gurramguda", "Nadergul")
        )
        assertEquals(
            RouteDirection.FORWARD,
            RouteData.getDirection("Jay Suryapatnam", "Sphoorthy College")
        )

        // Reverse journeys
        assertEquals(
            RouteDirection.REVERSE,
            RouteData.getDirection("Nadergul", "Gurramguda")
        )
        assertEquals(
            RouteDirection.REVERSE,
            RouteData.getDirection("Sphoorthy College", "Jay Suryapatnam")
        )
    }

    @Test
    fun testDriverAtOrBeforePickupInForwardDirection() {
        val pickup = "Jay Suryapatnam" // index 1
        val direction = RouteDirection.FORWARD

        // Driver at Gurramguda (index 0) is BEFORE pickup -> visible
        assertTrue(RouteData.isDriverAtOrBeforePickup("Gurramguda", pickup, direction))

        // Driver at Jay Suryapatnam (index 1) is AT pickup -> visible
        assertTrue(RouteData.isDriverAtOrBeforePickup("Jay Suryapatnam", pickup, direction))

        // Driver at Sphoorthy College (index 2) is PAST pickup in forward -> NOT visible
        assertFalse(RouteData.isDriverAtOrBeforePickup("Sphoorthy College", pickup, direction))

        // Driver at Nadergul (index 3) is PAST pickup in forward -> NOT visible
        assertFalse(RouteData.isDriverAtOrBeforePickup("Nadergul", pickup, direction))
    }

    @Test
    fun testDriverAtOrBeforePickupInReverseDirection() {
        val pickup = "Sphoorthy College" // index 2
        val direction = RouteDirection.REVERSE

        // In reverse (Nadergul -> Gurramguda), driver starts at 3 and moves towards 0
        // Driver at Nadergul (index 3) is BEFORE pickup -> visible
        assertTrue(RouteData.isDriverAtOrBeforePickup("Nadergul", pickup, direction))

        // Driver at Sphoorthy College (index 2) is AT pickup -> visible
        assertTrue(RouteData.isDriverAtOrBeforePickup("Sphoorthy College", pickup, direction))

        // Driver at Jay Suryapatnam (index 1) has already PASSED Sphoorthy in reverse -> NOT visible
        assertFalse(RouteData.isDriverAtOrBeforePickup("Jay Suryapatnam", pickup, direction))

        // Driver at Gurramguda (index 0) has already PASSED Sphoorthy in reverse -> NOT visible
        assertFalse(RouteData.isDriverAtOrBeforePickup("Gurramguda", pickup, direction))
    }

    @Test
    fun testStopsAwayCalculation() {
        // Forward: Gurramguda (0) to Sphoorthy College (2) = 2 stops away
        assertEquals(
            2,
            RouteData.stopsAway("Gurramguda", "Sphoorthy College", RouteDirection.FORWARD)
        )

        // Forward: At the stop = 0 stops away
        assertEquals(
            0,
            RouteData.stopsAway("Sphoorthy College", "Sphoorthy College", RouteDirection.FORWARD)
        )

        // Reverse: Nadergul (3) to Jay Suryapatnam (1) = 2 stops away
        assertEquals(
            2,
            RouteData.stopsAway("Nadergul", "Jay Suryapatnam", RouteDirection.REVERSE)
        )

        // Reverse: At the stop = 0 stops away
        assertEquals(
            0,
            RouteData.stopsAway("Jay Suryapatnam", "Jay Suryapatnam", RouteDirection.REVERSE)
        )
    }
}
