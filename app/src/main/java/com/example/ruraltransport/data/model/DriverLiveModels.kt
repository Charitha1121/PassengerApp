package com.example.ruraltransport.data.model

import com.google.android.gms.maps.model.LatLng

/**
 * Parsed record from Realtime Database `drivers/{uid}` node.
 * Handles both nested `liveLocation` and flat location properties.
 */
data class DriverLiveRecord(
    val uid: String = "",
    val name: String = "Driver",
    val phone: String = "",
    val vehicleNumber: String = "",
    val isOnline: Boolean = false,
    val routeId: String = "",
    val activeDirection: String = "", // "FORWARD" or "REVERSE"
    val currentStop: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Float = 0f,
    val speed: Float = 0f,
    val availableSeats: Int = 0,
    val lastUpdated: Long = 0L
)

/**
 * UI-friendly model for plotting on Google Maps Compose.
 */
data class LiveDriverUiModel(
    val driverId: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val currentStop: String,
    val position: LatLng,
    val heading: Float,
    val speed: Float,
    val availableSeats: Int,
    val stopsAway: Int,
    val isAtPassengerStop: Boolean,
    val lastUpdated: Long
)
