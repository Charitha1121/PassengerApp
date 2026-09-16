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
    val lastUpdated: Long = 0L,
    val routeId: String = "",
    val driverName: String = "Auto Driver",
    val vehicleNumber: String = ""
)
