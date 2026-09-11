package com.example.ruraltransport.data.model

data class TransportStop(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val sequence: Int = 0
)

data class RouteInfo(
    val id: String = "",
    val name: String = "",
    val stops: List<TransportStop> = emptyList()
)

enum class JourneyValidationResult {
    VALID,
    MISSING_PICKUP,
    MISSING_DESTINATION,
    SAME_STOP,
    INVALID_DIRECTION,
    INVALID_TARGET_TIME
}
