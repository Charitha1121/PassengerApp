package com.example.ruraltransport.data.model

data class TransitOption(
    val type: String, // "SHARED_AUTO", "RTC_BUS", "CORRIDOR_SHUTTLE"
    val title: String,
    val description: String,
    val availabilityRecommendation: String,
    val timetableNotice: String? = null,
    val iconName: String = "DirectionsBus"
)

object AlternativeTransportProvider {

    fun getOptions(availabilityProbability: Int): List<TransitOption> {
        val options = mutableListOf<TransitOption>()

        // 1. Shared Auto
        val autoRec = when {
            availabilityProbability >= 80 -> "Recommended: High likelihood of finding transport"
            availabilityProbability >= 60 -> "Moderate: Backup transit recommended"
            else -> "Low availability: Alternative transport recommended"
        }
        options.add(
            TransitOption(
                type = "SHARED_AUTO",
                title = "Rural Shared Auto",
                description = "Point-to-point corridor autos along Gurramguda — Nadergul route",
                availabilityRecommendation = autoRec,
                iconName = "DirectionsCar"
            )
        )

        // 2. RTC Bus
        options.add(
            TransitOption(
                type = "RTC_BUS",
                title = "RTC Rural Bus Service",
                description = "Regional state transport stopping at major junctions",
                availabilityRecommendation = "Alternative option if auto availability is low",
                timetableNotice = "RTC schedule information is currently unavailable.",
                iconName = "DirectionsBus"
            )
        )

        // 3. Shared Shuttle
        options.add(
            TransitOption(
                type = "CORRIDOR_SHUTTLE",
                title = "Corridor Shuttle Van",
                description = "Periodic feeder vans operating during college/market hours",
                availabilityRecommendation = "Runs subject to local driver availability",
                timetableNotice = "Fixed timetable not published; operational on demand",
                iconName = "AirportShuttle"
            )
        )

        return options
    }
}
