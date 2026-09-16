package com.example.ruraltransport.data.model

enum class RouteDirection {
    FORWARD,
    REVERSE
}

/**
 * Shared Corridor Route & Direction Helper.
 * Matches the RuralTransportDriver implementation exactly.
 */
object RouteData {

    const val DEFAULT_ROUTE_ID = "ROUTE_01"
    const val DEFAULT_ROUTE_NAME = "Gurramguda — Nadergul Corridor"

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
            latitude = 16.5062,
            longitude = 80.6480,
            sequence = 1
        ),
        TransportStop(
            id = "STOP_02",
            name = "Jay Suryapatnam",
            latitude = 16.5000,
            longitude = 80.6550,
            sequence = 2
        ),
        TransportStop(
            id = "STOP_03",
            name = "Sphoorthy College",
            latitude = 16.4950,
            longitude = 80.6620,
            sequence = 3
        ),
        TransportStop(
            id = "STOP_04",
            name = "Nadergul",
            latitude = 16.4900,
            longitude = 80.6700,
            sequence = 4
        )
    )

    /**
     * Returns the 0-based index of a stop along the corridor.
     * Matches either by exact name, case-insensitive name, or stop ID.
     */
    fun indexOf(stopNameOrId: String?): Int {
        if (stopNameOrId.isNullOrBlank()) return -1
        val clean = stopNameOrId.trim()
        val direct = stops.indexOfFirst { it.equals(clean, ignoreCase = true) }
        if (direct >= 0) return direct

        val byId = canonicalStops.indexOfFirst { it.id.equals(clean, ignoreCase = true) }
        if (byId >= 0) return byId

        return -1
    }

    /**
     * Overload for [TransportStop].
     */
    fun indexOf(stop: TransportStop?): Int {
        if (stop == null) return -1
        val byId = canonicalStops.indexOfFirst { it.id == stop.id }
        if (byId >= 0) return byId
        return indexOf(stop.name)
    }

    /**
     * Determine passenger's direction from pickup and destination stops.
     * FORWARD: pickupIndex < destinationIndex
     * REVERSE: pickupIndex >= destinationIndex
     */
    fun getDirection(pickupStop: String, destinationStop: String): RouteDirection {
        val pIdx = indexOf(pickupStop)
        val dIdx = indexOf(destinationStop)
        return if (pIdx < dIdx) RouteDirection.FORWARD else RouteDirection.REVERSE
    }

    /**
     * Overload for [TransportStop].
     */
    fun getDirection(pickupStop: TransportStop, destinationStop: TransportStop): RouteDirection {
        val pIdx = indexOf(pickupStop)
        val dIdx = indexOf(destinationStop)
        return if (pIdx < dIdx) RouteDirection.FORWARD else RouteDirection.REVERSE
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
        val driverIdx = indexOf(driverCurrentStop)
        val pickupIdx = indexOf(passengerPickupStop)

        // If either stop is unrecognized, default to allowing visibility or handling gracefully
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
        val idx = indexOf(stopNameOrId)
        return if (idx in stops.indices) stops[idx] else stopNameOrId
    }
}
