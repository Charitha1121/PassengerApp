package com.example.ruraltransport.data.model

enum class RouteDirection {
    FORWARD,
    REVERSE
}

data class RouteMatchResult(
    val route: RouteInfo,
    val sourceStop: TransportStop,
    val destStop: TransportStop,
    val direction: RouteDirection
)

/**
 * Shared Corridor Route & Direction Helper.
 * Matches the RuralTransportDriver implementation exactly.
 */
object RouteData {

    const val DEFAULT_ROUTE_ID = "ROUTE_01"
    const val DEFAULT_ROUTE_NAME = "Rural Corridor"

    /**
     * Ordered corridor stops from start to end (FORWARD direction sequence).
     */
    val stops: List<String> = listOf(
        "Gurramguda",
        "Jay Suryapatnam",
        "Sphoorthy College",
        "Nadergul"
    )

    /**
     * Canonical stops with geographic coordinates for map plotting.
     */
    val canonicalStops: List<TransportStop> = listOf(
        TransportStop(
            id = "STOP_01",
            name = "Gurramguda",
            latitude = 17.2942,
            longitude = 78.5675,
            sequence = 1
        ),
        TransportStop(
            id = "STOP_02",
            name = "Jay Suryapatnam",
            latitude = 17.2885,
            longitude = 78.5605,
            sequence = 2
        ),
        TransportStop(
            id = "STOP_03",
            name = "Sphoorthy College",
            latitude = 17.2820,
            longitude = 78.5538,
            sequence = 3
        ),
        TransportStop(
            id = "STOP_04",
            name = "Nadergul",
            latitude = 17.2746,
            longitude = 78.5400,
            sequence = 4
        )
    )

    val defaultRouteInfo = RouteInfo(
        id = DEFAULT_ROUTE_ID,
        name = DEFAULT_ROUTE_NAME,
        stops = canonicalStops
    )

    // New Route Constants matching Driver App
    const val ROUTE_IBP_GURRAMGUDA = "ROUTE_IBP_GURRAMGUDA"
    const val ROUTE_GURRAMGUDA_RINGROAD = "ROUTE_GURRAMGUDA_RINGROAD"
    const val ROUTE_RINGROAD_SANTOSHNAGAR = "ROUTE_RINGROAD_SANTOSHNAGAR"
    const val ROUTE_BALAPUR_SPHOORTHY = "ROUTE_BALAPUR_SPHOORTHY"

    private val ibpGurramgudaStops = listOf(
        TransportStop("IBP_01", "IBP Petrol Pump, Nagarjuna Sagar Road", 17.2945, 78.5650, 1),
        TransportStop("IBP_02", "Gurramguda Village", 17.2940, 78.5660, 2)
    )

    private val gurramgudaRingRoadStops = listOf(
        TransportStop("GRR_01", "Gurramguda Village", 17.2940, 78.5660, 1),
        TransportStop("GRR_02", "Gurramguda Cross Road", 17.3079, 78.5674, 2),
        TransportStop("GRR_03", "B.N. Reddy Nagar Bus Stop", 17.3235, 78.5630, 3),
        TransportStop("GRR_04", "Vanasthalipuram", 17.3350, 78.5510, 4),
        TransportStop("GRR_05", "Bairamalguda Cross Road", 17.3440, 78.5512, 5),
        TransportStop("GRR_06", "Sagar Ring Road (LB Nagar)", 17.3484, 78.5510, 6)
    )

    private val ringRoadSantoshnagarStops = listOf(
        TransportStop("RRS_01", "Sagar Ring Road (LB Nagar)", 17.3484, 78.5510, 1),
        TransportStop("RRS_02", "L.B. Nagar Metro Station", 17.3502, 78.5475, 2),
        TransportStop("RRS_03", "Kothapet Fruit Market", 17.3565, 78.5450, 3),
        TransportStop("RRS_04", "Chaitanyapuri Metro Station", 17.3620, 78.5430, 4),
        TransportStop("RRS_05", "Dilsukhnagar Bus Station", 17.3687, 78.5247, 5),
        TransportStop("RRS_06", "Moosarambagh X Road", 17.3712, 78.5135, 6),
        TransportStop("RRS_07", "Saidabad Colony", 17.3615, 78.5100, 7),
        TransportStop("RRS_08", "Santoshnagar Cross Roads", 17.3544, 78.5076, 8)
    )

    private val balapurSphoorthyStops = listOf(
        TransportStop("BSP_01", "Balapur X Road", 17.3020, 78.5150, 1),
        TransportStop("BSP_02", "Udyog Nagar", 17.2965, 78.5210, 2),
        TransportStop("BSP_03", "Badangpet Cheruvu Bus Stop", 17.2885, 78.5320, 3),
        TransportStop("BSP_04", "MVSR Engineering College, Nadergul", 17.2831, 78.5492, 4),
        TransportStop("BSP_05", "Kammaguda Bus Stop", 17.2840, 78.5570, 5),
        TransportStop("BSP_06", "Nadergul Village", 17.2985, 78.5670, 6),
        TransportStop("BSP_07", "Sphoorthy Engineering College", 17.2960, 78.5675, 7)
    )

    /**
     * All registered routes known to the system.
     * Preserves the primary corridor and allows addition of further corridors.
     */
    val allRoutes: MutableList<RouteInfo> = mutableListOf(
        defaultRouteInfo,
        RouteInfo(ROUTE_IBP_GURRAMGUDA, "IBP – Gurramguda Local", ibpGurramgudaStops),
        RouteInfo(ROUTE_GURRAMGUDA_RINGROAD, "Gurramguda – Sagar Ring Road", gurramgudaRingRoadStops),
        RouteInfo(ROUTE_RINGROAD_SANTOSHNAGAR, "Sagar Ring Road – Santoshnagar", ringRoadSantoshnagarStops),
        RouteInfo(ROUTE_BALAPUR_SPHOORTHY, "Balapur – Sphoorthy College", balapurSphoorthyStops)
    )

    /**
     * Registers an additional route to the system.
     */
    fun registerRoute(route: RouteInfo) {
        if (allRoutes.none { it.id.equals(route.id, ignoreCase = true) }) {
            allRoutes.add(route)
        }
    }

    /**
     * Deduplicated list of all canonical stops across all registered routes.
     * Acts as the single source of truth for Source & Destination dropdown selectors.
     */
    fun getAllKnownStops(): List<TransportStop> {
        val stopMap = linkedMapOf<String, TransportStop>()
        allRoutes.forEach { route ->
            route.stops.forEach { stop ->
                val key = stop.name.trim().lowercase()
                if (!stopMap.containsKey(key)) {
                    stopMap[key] = stop
                }
            }
        }
        return stopMap.values.toList()
    }

    /**
     * Single source of truth for matching a live driver to a route and direction.
     * Used identically by RouteSearchViewModel, LiveTrackingViewModel, and RideRepository.
     */
    fun isDriverMatchingRoute(
        driver: LiveDriverPosition,
        route: RouteInfo,
        direction: RouteDirection? = null
    ): Boolean {
        val driverRouteId = driver.routeId
        val driverCurrentStop = driver.currentStop

        val idMatch = driverRouteId.isNotBlank() && (
            driverRouteId.equals(route.id, ignoreCase = true) ||
            driverRouteId.contains(route.id, ignoreCase = true) ||
            route.id.contains(driverRouteId, ignoreCase = true)
        )

        val nameMatch = driverRouteId.isNotBlank() && (
            driverRouteId.contains(route.name, ignoreCase = true) ||
            route.name.contains(driverRouteId, ignoreCase = true) ||
            (route.name.contains("Santoshnagar", ignoreCase = true) && driverRouteId.contains("Santosh", ignoreCase = true))
        )

        val stopMatch = driverCurrentStop.isNotBlank() && route.stops.any {
            it.name.contains(driverCurrentStop, ignoreCase = true) ||
            driverCurrentStop.contains(it.name, ignoreCase = true)
        }

        val directionMatch = direction == null ||
            driver.activeDirection.isBlank() ||
            driver.activeDirection.equals(direction.name, ignoreCase = true) ||
            (driver.activeDirection.length == 1 && driver.activeDirection == direction.ordinal.toString())

        return (idMatch || nameMatch || stopMatch) && directionMatch
    }

    /**
     * Overload to match by routeId String by resolving from allRoutes,
     * or fallback to strict ID match if not found.
     */
    fun isDriverMatchingRoute(
        driver: LiveDriverPosition,
        routeId: String?,
        direction: RouteDirection? = null
    ): Boolean {
        if (routeId.isNullOrBlank()) return true
        val matchedRoute = allRoutes.find {
            it.id.equals(routeId, ignoreCase = true) ||
            it.name.equals(routeId, ignoreCase = true)
        }
        return if (matchedRoute != null) {
            isDriverMatchingRoute(driver, matchedRoute, direction)
        } else {
            val idMatch = driver.routeId.isNotBlank() && (
                driver.routeId.equals(routeId, ignoreCase = true) ||
                driver.routeId.contains(routeId, ignoreCase = true) ||
                routeId.contains(driver.routeId, ignoreCase = true)
            )
            val directionMatch = direction == null ||
                driver.activeDirection.isBlank() ||
                driver.activeDirection.equals(direction.name, ignoreCase = true) ||
                (driver.activeDirection.length == 1 && driver.activeDirection == direction.ordinal.toString())
            idMatch && directionMatch
        }
    }

    /**
     * Checks if Source and Destination both exist as stops on any known route.
     * Returns a list of all matching routes, resolved TransportStops, and determined RouteDirections.
     */
    fun findAllMatchingRoutes(sourceStopName: String, destStopName: String): List<RouteMatchResult> {
        val sClean = sourceStopName.trim()
        val dClean = destStopName.trim()
        if (sClean.isBlank() || dClean.isBlank() || sClean.equals(dClean, ignoreCase = true)) {
            return emptyList()
        }

        val results = mutableListOf<RouteMatchResult>()

        for (route in allRoutes) {
            val sourceIdx = route.stops.indexOfFirst {
                it.name.equals(sClean, ignoreCase = true) || it.id.equals(sClean, ignoreCase = true)
            }
            val destIdx = route.stops.indexOfFirst {
                it.name.equals(dClean, ignoreCase = true) || it.id.equals(dClean, ignoreCase = true)
            }

            if (sourceIdx >= 0 && destIdx >= 0 && sourceIdx != destIdx) {
                val sourceStop = route.stops[sourceIdx]
                val destStop = route.stops[destIdx]
                val direction = if (sourceIdx < destIdx) RouteDirection.FORWARD else RouteDirection.REVERSE
                results.add(RouteMatchResult(
                    route = route,
                    sourceStop = sourceStop,
                    destStop = destStop,
                    direction = direction
                ))
            }
        }
        return results
    }

    /**
     * Legacy helper - returns the first match.
     */
    fun findMatchingRoute(sourceStopName: String, destStopName: String): RouteMatchResult? {
        return findAllMatchingRoutes(sourceStopName, destStopName).firstOrNull()
    }

    /**
     * Finds stops within [maxDistanceKm] (default 2.0 km) of the given stop coordinates
     * across all known routes, calculated using Haversine formula (GeoUtils).
     */
    fun findNearbyStops(
        fromStop: TransportStop,
        maxDistanceKm: Double = 2.0
    ): List<Pair<TransportStop, Double>> {
        val allStops = getAllKnownStops().filterNot {
            it.name.equals(fromStop.name, ignoreCase = true) || it.id.equals(fromStop.id, ignoreCase = true)
        }

        return allStops.mapNotNull { otherStop ->
            val dist = GeoUtils.calculateDistanceKm(
                fromStop.latitude, fromStop.longitude,
                otherStop.latitude, otherStop.longitude
            )
            if (dist > 0.0 && dist <= maxDistanceKm) {
                Pair(otherStop, dist)
            } else null
        }.sortedBy { it.second }
    }

    /**
     * Returns all routes that pass through a given stop.
     */
    fun getRoutesForStop(stopNameOrId: String): List<RouteInfo> {
        val clean = stopNameOrId.trim()
        return allRoutes.filter { route ->
            route.stops.any { it.name.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true) }
        }
    }

    /**
     * Returns the 0-based index of a stop along the corridor.
     * Matches either by exact name, case-insensitive name, or stop ID.
     */
    fun indexOf(stopNameOrId: String?): Int {
        if (stopNameOrId.isNullOrBlank()) return -1
        val clean = stopNameOrId.trim()
        
        // Check canonical stops first
        val direct = canonicalStops.indexOfFirst { it.name.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true) }
        if (direct >= 0) return direct

        // Check all stops across all routes
        val allStops = getAllKnownStops()
        val idx = allStops.indexOfFirst { it.name.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true) }
        return idx
    }

    /**
     * Overload for [TransportStop].
     */
    fun indexOf(stop: TransportStop?): Int {
        if (stop == null) return -1
        return indexOf(stop.name)
    }

    /**
     * Determine passenger's direction from pickup and destination stops.
     * FORWARD: pickupIndex < destinationIndex
     * REVERSE: pickupIndex >= destinationIndex
     */
    fun getDirection(pickupStop: String, destinationStop: String): RouteDirection {
        val match = findMatchingRoute(pickupStop, destinationStop)
        return match?.direction ?: RouteDirection.FORWARD
    }

    /**
     * Overload for [TransportStop].
     */
    fun getDirection(pickupStop: TransportStop, destinationStop: TransportStop): RouteDirection {
        return getDirection(pickupStop.name, destinationStop.name)
    }

    /**
     * Core Rule Check:
     * A driver is at or before the passenger's pickup stop if:
     * - FORWARD: driverIndex <= passengerPickupIndex
     * - REVERSE: driverIndex >= passengerPickupIndex
     */
    fun isDriverAtOrBeforePickup(
        driverCurrentStop: String,
        passengerPickupStop: String,
        direction: RouteDirection
    ): Boolean {
        // This logic is mostly for the primary corridor. 
        // For other routes, we'd need to know which route the driver is on.
        // For now, let's keep it simple and match by name index in the default stops if possible.
        val driverIdx = indexOf(driverCurrentStop)
        val pickupIdx = indexOf(passengerPickupStop)

        if (driverIdx < 0 || pickupIdx < 0) return true

        return when (direction) {
            RouteDirection.FORWARD -> driverIdx <= pickupIdx
            RouteDirection.REVERSE -> driverIdx >= pickupIdx
        }
    }

    /**
     * Computes the number of stops away the driver is from the passenger's pickup stop.
     */
    fun stopsAway(
        driverCurrentStop: String,
        passengerPickupStop: String,
        direction: RouteDirection
    ): Int {
        val driverIdx = indexOf(driverCurrentStop)
        val pickupIdx = indexOf(passengerPickupStop)
        if (driverIdx < 0 || pickupIdx < 0) return 0

        return when (direction) {
            RouteDirection.FORWARD -> (pickupIdx - driverIdx).coerceAtLeast(0)
            RouteDirection.REVERSE -> (driverIdx - pickupIdx).coerceAtLeast(0)
        }
    }

    /**
     * Formats stop description for UI.
     */
    fun getStopName(stopNameOrId: String?): String {
        if (stopNameOrId.isNullOrBlank()) return "Unknown Stop"
        val allStops = getAllKnownStops()
        val match = allStops.find { it.name.equals(stopNameOrId, ignoreCase = true) || it.id.equals(stopNameOrId, ignoreCase = true) }
        return match?.name ?: stopNameOrId
    }
}
