package com.example.ruraltransport.data.model

data class ForecastPoint(
    val epochMillis: Long = 0L,
    val formattedTime: String = "",
    val probabilityPercent: Int = 0,
    val expectedAutoMin: Int = 0,
    val expectedAutoMax: Int = 0,
    val expectedWaitMin: Int = 0,
    val expectedWaitMax: Int = 0,
    val isTargetTime: Boolean = false
)

data class AvailabilityForecast(
    val queryEpochMillis: Long = 0L,
    val targetEpochMillis: Long = 0L,
    val routeId: String = "",
    val routeName: String = "",
    val pickupStopId: String = "",
    val pickupStopName: String = "",
    val destStopId: String = "",
    val destStopName: String = "",
    val probabilityPercent: Int = 0,
    val expectedAutoMin: Int = 0,
    val expectedAutoMax: Int = 0,
    val expectedSeatsMin: Int = 0,
    val expectedSeatsMax: Int = 0,
    val expectedWaitMin: Int = 0,
    val expectedWaitMax: Int = 0,
    val confidenceScorePercent: Int = 0,
    val confidenceLevel: String = "Medium", // High, Medium, Low
    val riskLevel: String = "Low",          // Low, Moderate, High
    val bestTimeWindow: String = "",       // e.g. "4:10 PM – 4:25 PM"
    val explanationReasons: List<String> = emptyList(),
    val isFallbackUsed: Boolean = false,
    val fallbackDescription: String? = null,
    val timeSeries: List<ForecastPoint> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis()
)
