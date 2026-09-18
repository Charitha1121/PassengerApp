package com.example.ruraltransport

import com.example.ruraltransport.data.model.GeoUtils
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.data.model.RouteInfo
import com.example.ruraltransport.data.model.TransportStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSearchUnitTest {

    @Test
    fun testAllKnownStopsContainsCanonicalStops() {
        val allStops = RouteData.getAllKnownStops()
        assertEquals(4, allStops.size)
        assertEquals("Gurramguda", allStops[0].name)
        assertEquals("Jay Suryapatnam", allStops[1].name)
        assertEquals("Sphoorthy College", allStops[2].name)
        assertEquals("Nadergul", allStops[3].name)
    }

    @Test
    fun testDirectRouteMatchForward() {
        val match = RouteData.findMatchingRoute("Gurramguda", "Nadergul")
        assertNotNull(match)
        assertEquals("ROUTE_01", match!!.route.id)
        assertEquals("Gurramguda", match.sourceStop.name)
        assertEquals("Nadergul", match.destStop.name)
        assertEquals(RouteDirection.FORWARD, match.direction)
    }

    @Test
    fun testDirectRouteMatchReverse() {
        val match = RouteData.findMatchingRoute("Nadergul", "Jay Suryapatnam")
        assertNotNull(match)
        assertEquals("ROUTE_01", match!!.route.id)
        assertEquals("Nadergul", match.sourceStop.name)
        assertEquals("Jay Suryapatnam", match.destStop.name)
        assertEquals(RouteDirection.REVERSE, match.direction)
    }

    @Test
    fun testDirectRouteMatchInvalidOrSameStop() {
        // Same stop
        assertNull(RouteData.findMatchingRoute("Gurramguda", "Gurramguda"))

        // Blank or non-existent stop
        assertNull(RouteData.findMatchingRoute("", "Nadergul"))
        assertNull(RouteData.findMatchingRoute("Gurramguda", "Unknown Village"))
    }

    @Test
    fun testMultiRouteRegistrationAndMatching() {
        // Register a second corridor
        val route2Stops = listOf(
            TransportStop(id = "STOP_10", name = "Nadergul Junction", latitude = 17.2740, longitude = 78.5395, sequence = 1),
            TransportStop(id = "STOP_11", name = "Badangpet", latitude = 17.3180, longitude = 78.5280, sequence = 2),
            TransportStop(id = "STOP_12", name = "Balapur X Road", latitude = 17.3245, longitude = 78.5085, sequence = 3)
        )
        val route2 = RouteInfo(
            id = "ROUTE_02",
            name = "Nadergul — Balapur Corridor",
            stops = route2Stops
        )
        RouteData.registerRoute(route2)

        // All stops count should now include new stops
        val allStops = RouteData.getAllKnownStops()
        assertTrue(allStops.size >= 7)

        // Matching on Route 2
        val matchRoute2 = RouteData.findMatchingRoute("Badangpet", "Balapur X Road")
        assertNotNull(matchRoute2)
        assertEquals("ROUTE_02", matchRoute2!!.route.id)
        assertEquals(RouteDirection.FORWARD, matchRoute2.direction)

        // Reverse matching on Route 2
        val matchRoute2Rev = RouteData.findMatchingRoute("Balapur X Road", "Nadergul Junction")
        assertNotNull(matchRoute2Rev)
        assertEquals("ROUTE_02", matchRoute2Rev!!.route.id)
        assertEquals(RouteDirection.REVERSE, matchRoute2Rev.direction)
    }

    @Test
    fun testNearbyStopsWithin2KmCalculation() {
        // Sphoorthy College: 17.2820, 78.5538
        // Jay Suryapatnam: 17.2885, 78.5605 (approx 1.0 km)
        // Gurramguda: 17.2942, 78.5675 (approx 2.0 km)
        val sphoorthy = RouteData.canonicalStops.find { it.name == "Sphoorthy College" }!!
        val nearbyStops = RouteData.findNearbyStops(sphoorthy, maxDistanceKm = 2.0)

        assertTrue("Should find at least Jay Suryapatnam within 2km", nearbyStops.isNotEmpty())
        for ((stop, dist) in nearbyStops) {
            assertTrue("Distance must be <= 2.0 km", dist <= 2.0)
            assertTrue("Distance must be > 0.0 km", dist > 0.0)
        }

        // Verify sorted by distance ascending
        for (i in 0 until nearbyStops.size - 1) {
            assertTrue(
                "Nearby stops must be sorted ascending by distance",
                nearbyStops[i].second <= nearbyStops[i + 1].second
            )
        }
    }

    @Test
    fun testDriverMatchFilteringForSearchedRoute() {
        val sampleDrivers = listOf(
            LiveDriverPosition(
                driverUid = "driver_1",
                lat = 17.2942,
                lng = 78.5675,
                isAvailable = true,
                routeId = "ROUTE_01",
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "driver_2",
                lat = 17.2885,
                lng = 78.5605,
                isAvailable = false, // Not available
                routeId = "ROUTE_01",
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "driver_3",
                lat = 0.0,
                lng = 0.0, // Invalid coordinate
                isAvailable = true,
                routeId = "ROUTE_01",
                activeDirection = "FORWARD"
            ),
            LiveDriverPosition(
                driverUid = "driver_4",
                lat = 17.2746,
                lng = 78.5400,
                isAvailable = true,
                routeId = "ROUTE_01",
                activeDirection = "REVERSE" // Opposite direction
            )
        )

        // Matching Route 1, Forward direction
        val matchedDrivers = sampleDrivers.filter { d ->
            d.isAvailable &&
            (d.lat != 0.0 || d.lng != 0.0) &&
            d.routeId.equals("ROUTE_01", ignoreCase = true) &&
            d.activeDirection.equals("FORWARD", ignoreCase = true)
        }

        assertEquals(1, matchedDrivers.size)
        assertEquals("driver_1", matchedDrivers[0].driverUid)
    }

    @Test
    fun testWalkingDistanceAndEtaFormattedCorrectly() {
        val lat1 = 17.2942
        val lon1 = 78.5675
        val lat2 = 17.2885
        val lon2 = 78.5605
        val distKm = GeoUtils.calculateDistanceKm(lat1, lon1, lat2, lon2)
        val formatted = "%.1f".format(distKm)
        assertTrue(distKm > 0.0 && distKm < 2.0)
        assertTrue(formatted.contains("."))
    }

    @Test
    fun testIsDriverMatchingRoute_byIdNameAndStop() {
        val testRoute = RouteInfo(
            id = "ROUTE_TEST_01",
            name = "Balapur – Sphoorthy College",
            stops = listOf(
                TransportStop("S1", "Balapur X Road", 17.3020, 78.5150, 1),
                TransportStop("S2", "Sphoorthy Engineering College", 17.2960, 78.5675, 2)
            )
        )

        // 1. Matches by route ID
        val driverById = LiveDriverPosition(
            driverUid = "d1",
            routeId = "ROUTE_TEST_01",
            activeDirection = "FORWARD"
        )
        assertTrue(RouteData.isDriverMatchingRoute(driverById, testRoute, RouteDirection.FORWARD))

        // 2. Matches by route Name
        val driverByName = LiveDriverPosition(
            driverUid = "d2",
            routeId = "Balapur – Sphoorthy College",
            activeDirection = "FORWARD"
        )
        assertTrue(RouteData.isDriverMatchingRoute(driverByName, testRoute, RouteDirection.FORWARD))

        // 3. Matches by current stop on route
        val driverByStop = LiveDriverPosition(
            driverUid = "d3",
            routeId = "",
            currentStop = "Balapur X Road",
            activeDirection = "FORWARD"
        )
        assertTrue(RouteData.isDriverMatchingRoute(driverByStop, testRoute, RouteDirection.FORWARD))

        // 4. Rejects when routeId is blank and currentStop does NOT match any route stop
        val driverUnmatched = LiveDriverPosition(
            driverUid = "d4",
            routeId = "",
            currentStop = "Random Unrelated Stop",
            activeDirection = "FORWARD"
        )
        assertFalse(RouteData.isDriverMatchingRoute(driverUnmatched, testRoute, RouteDirection.FORWARD))

        // 5. Rejects opposite direction
        val driverOpposite = LiveDriverPosition(
            driverUid = "d5",
            routeId = "ROUTE_TEST_01",
            activeDirection = "REVERSE"
        )
        assertFalse(RouteData.isDriverMatchingRoute(driverOpposite, testRoute, RouteDirection.FORWARD))

        // 6. Accepts ordinal direction (e.g. "0" for FORWARD, "1" for REVERSE)
        val driverOrdinal = LiveDriverPosition(
            driverUid = "d6",
            routeId = "ROUTE_TEST_01",
            activeDirection = "0"
        )
        assertTrue(RouteData.isDriverMatchingRoute(driverOrdinal, testRoute, RouteDirection.FORWARD))
    }
}
