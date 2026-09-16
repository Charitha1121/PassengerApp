package com.example.ruraltransport.data.model

/**
 * Real-time GPS and ride state for an active driver emitting to `drivers/{uid}/liveTracking`.
 *
 * Designed for animated vehicle marker tracking on Google Maps Compose.
 */
data class LiveDriverPosition(
    val driverUid: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val heading: Float = 0f,
    val speed: Float = 0f,
    val isRideActive: Boolean = false,
    val isAvailable: Boolean = true,
    val availableSeats: Int = 3,
    val lastUpdated: Long = 0L,
    val routeId: String = "",
    val activeDirection: String = "",
    val currentStop: String = "",
    val driverName: String = "Auto Driver",
    val vehicleNumber: String = "",
    val phone: String = "",
    val distanceKmToPickup: Double? = null,
    val etaMinutesToPickup: Int? = null
)
