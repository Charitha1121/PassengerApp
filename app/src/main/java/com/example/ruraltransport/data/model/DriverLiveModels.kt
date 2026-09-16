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
    val lastUpdated: Long,
    val distanceKm: Double = 0.0,
    val etaMinutes: Int = 0
)

object GeoUtils {
    /**
     * Calculates the great-circle distance between two points on the Earth (in km) using Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0) return 0.0
        if (lat2 == 0.0 && lon2 == 0.0) return 0.0
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusKm * c
    }

    /**
     * Estimates travel time in minutes based on distance and average speed (default 20 km/h for rural auto corridor).
     */
    fun calculateEtaMinutes(distanceKm: Double, avgSpeedKmh: Double = 20.0): Int {
        if (distanceKm <= 0.0 || avgSpeedKmh <= 0.0) return 0
        val hours = distanceKm / avgSpeedKmh
        return kotlin.math.round((hours * 60)).toInt().coerceAtLeast(1)
    }
}
