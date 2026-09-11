package com.example.ruraltransport.data.model

enum class RideStatus {
    PENDING,
    MATCHING,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED
}

data class RideRequest(
    val requestId: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val passengerPhone: String = "",
    val routeId: String = "",
    val routeName: String = "",
    val pickupStopId: String = "",
    val pickupStopName: String = "",
    val destinationStopId: String = "",
    val destinationStopName: String = "",
    val requestedSeats: Int = 1,
    val targetTime: Long = 0L,
    val status: String = RideStatus.PENDING.name,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val vehicleNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DriverLocationUpdate(
    val driverId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    val ageSeconds: Long
        get() = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)

    val isStale: Boolean
        get() = ageSeconds > 60
}

data class MatchedDriver(
    val driverId: String = "",
    val name: String = "",
    val phone: String = "",
    val vehicleNumber: String = "",
    val currentStop: String = "",
    val availableSeats: Int = 0,
    val status: String = "AVAILABLE",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastUpdated: Long = 0L
)
