package com.example.ruraltransport.data.repository

import com.example.ruraltransport.data.model.AvailabilityForecast
import com.example.ruraltransport.data.model.ForecastPoint
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ForecastRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://ruraltransport-54174-default-rtdb.asia-southeast1.firebasedatabase.app")
) {

    suspend fun generateForecast(
        routeId: String,
        routeName: String,
        pickupStopId: String,
        pickupStopName: String,
        destStopId: String,
        destStopName: String,
        targetEpochMillis: Long,
        queryEpochMillis: Long = System.currentTimeMillis()
    ): AvailabilityForecast {
        // 1. Fetch real-time corridor driver availability & waiting demand
        val activeDriverCount = fetchCorridorActiveDrivers()
        val activeAvailableSeats = fetchCorridorAvailableSeats()
        val direction = com.example.ruraltransport.data.model.RouteData.getDirection(pickupStopName, destStopName)
        val waitingCount = fetchPickupStopWaitingCount(routeId, direction.name, pickupStopName)

        // 2. Target Time Analysis
        val targetCal = Calendar.getInstance().apply { timeInMillis = targetEpochMillis }
        val targetHour = targetCal.get(Calendar.HOUR_OF_DAY)
        val targetMinute = targetCal.get(Calendar.MINUTE)
        val targetDayOfWeek = targetCal.get(Calendar.DAY_OF_WEEK)

        // Time horizon in minutes from query time to target time
        val horizonMinutes = max(0L, (targetEpochMillis - queryEpochMillis) / (1000 * 60))

        // 3. Compute base probability from rural network time-of-day model
        val baseMetrics = computeTimeOfDayPrior(targetHour, targetMinute, targetDayOfWeek)

        // 4. Adjust with real-time driver density and passenger waiting queue
        val realTimeAdjustment = when {
            activeDriverCount >= 3 -> 10
            activeDriverCount in 1..2 -> 5
            else -> 0
        }

        // 5. Time-horizon uncertainty adjustment
        // Predictions 6 hours out have wider uncertainty than predictions 15 minutes out
        val horizonConfidenceFactor = when {
            horizonMinutes <= 30 -> 0.95
            horizonMinutes <= 120 -> 0.88
            horizonMinutes <= 360 -> 0.82
            else -> 0.74
        }

        val rawProbability = (baseMetrics.probability + realTimeAdjustment).coerceIn(25, 96)
        val finalProbability = rawProbability.coerceIn(10, 98)

        // Expected Autos calculation (additional autos attracted if high passenger queue)
        val autoMin = max(1, (baseMetrics.expectedAutos * 0.8).roundToInt())
        val autoMax = max(autoMin + 1, (baseMetrics.expectedAutos * 1.25).roundToInt() + if (activeDriverCount > 2) 1 else 0 + if (waitingCount >= 3) 1 else 0)

        // Expected Seats calculation (adjusting for passengers already in line)
        val baseSeatsMin = max(2, autoMin * 2)
        val baseSeatsMax = max(baseSeatsMin + 2, autoMax * 3)
        val seatsMin = max(1, baseSeatsMin - (waitingCount / 2))
        val seatsMax = max(seatsMin + 1, baseSeatsMax - waitingCount)

        // Waiting Time calculation (board queue adjustment if people already waiting)
        val queueDelay = if (waitingCount >= 3) 2 else 0
        val waitMin = (baseMetrics.expectedWaitMin + queueDelay).coerceAtLeast(3)
        val waitMax = (baseMetrics.expectedWaitMax + queueDelay + if (horizonMinutes > 180) 2 else 0).coerceAtLeast(waitMin + 2)

        // Confidence calculation
        val confidencePercent = (baseMetrics.baseConfidence * horizonConfidenceFactor).roundToInt().coerceIn(40, 95)
        val confidenceLevel = when {
            confidencePercent >= 75 -> "High"
            confidencePercent >= 55 -> "Medium"
            else -> "Low"
        }

        val riskLevel = when {
            finalProbability >= 80 -> "Low"
            finalProbability >= 60 -> "Moderate"
            else -> "High"
        }

        // Explanations
        val explanations = mutableListOf<String>()
        if (targetHour in 15..17) {
            explanations.add("✓ High frequency rural peak window (college / office departure time)")
        } else if (targetHour in 8..10) {
            explanations.add("✓ High morning commute activity along the corridor")
        } else if (targetHour in 11..14) {
            explanations.add("ℹ Moderate afternoon rural transit schedule")
        } else {
            explanations.add("ℹ Evening / off-peak transit frequency applies")
        }

        if (waitingCount > 0) {
            explanations.add("ℹ Real-time demand: $waitingCount passenger(s) currently waiting at $pickupStopName for ${direction.name} corridor")
        }

        if (activeDriverCount > 0) {
            explanations.add("✓ Real-time active drivers currently operating on the route ($activeDriverCount active)")
        } else {
            explanations.add("ℹ Based on historical corridor demand and arrival distribution patterns")
        }

        if (horizonMinutes > 180) {
            explanations.add("ℹ Advance forecast (+${horizonMinutes / 60}h ahead). Forecast accuracy increases closer to departure time.")
        } else {
            explanations.add("✓ Short horizon (<3h); high alignment with current driver availability")
        }

        // 6. Generate Time-Series Window around Target Time (T-30m to T+30m)
        val timeSeries = generateTimeSeries(
            targetEpochMillis = targetEpochMillis,
            baseMetrics = baseMetrics,
            realTimeAdjustment = realTimeAdjustment
        )

        // 7. Determine Best Time Window
        val bestPoint = timeSeries.maxByOrNull { it.probabilityPercent } ?: timeSeries[timeSeries.size / 2]
        val bestCal = Calendar.getInstance().apply { timeInMillis = bestPoint.epochMillis }
        val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val windowStart = timeSdf.format(Date(bestCal.timeInMillis - TimeUnit.MINUTES.toMillis(5)))
        val windowEnd = timeSdf.format(Date(bestCal.timeInMillis + TimeUnit.MINUTES.toMillis(10)))
        val bestWindowStr = "$windowStart – $windowEnd"

        return AvailabilityForecast(
            queryEpochMillis = queryEpochMillis,
            targetEpochMillis = targetEpochMillis,
            routeId = routeId,
            routeName = routeName,
            pickupStopId = pickupStopId,
            pickupStopName = pickupStopName,
            destStopId = destStopId,
            destStopName = destStopName,
            probabilityPercent = finalProbability,
            expectedAutoMin = autoMin,
            expectedAutoMax = autoMax,
            expectedSeatsMin = seatsMin,
            expectedSeatsMax = seatsMax,
            expectedWaitMin = waitMin,
            expectedWaitMax = waitMax,
            confidenceScorePercent = confidencePercent,
            confidenceLevel = confidenceLevel,
            riskLevel = riskLevel,
            bestTimeWindow = bestWindowStr,
            explanationReasons = explanations,
            isFallbackUsed = false,
            fallbackDescription = null,
            timeSeries = timeSeries,
            generatedAt = System.currentTimeMillis()
        )
    }

    private fun generateTimeSeries(
        targetEpochMillis: Long,
        baseMetrics: PriorMetrics,
        realTimeAdjustment: Int
    ): List<ForecastPoint> {
        val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val offsetsMinutes = listOf(-30, -15, 0, 15, 30)

        return offsetsMinutes.map { offset ->
            val pointEpoch = targetEpochMillis + TimeUnit.MINUTES.toMillis(offset.toLong())
            val cal = Calendar.getInstance().apply { timeInMillis = pointEpoch }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val prior = computeTimeOfDayPrior(hour, min, cal.get(Calendar.DAY_OF_WEEK))

            val prob = (prior.probability + realTimeAdjustment).coerceIn(30, 95)
            val autoMin = max(1, (prior.expectedAutos * 0.8).roundToInt())
            val autoMax = max(autoMin + 1, (prior.expectedAutos * 1.25).roundToInt())
            val waitMin = prior.expectedWaitMin.coerceAtLeast(3)
            val waitMax = prior.expectedWaitMax.coerceAtLeast(waitMin + 2)

            ForecastPoint(
                epochMillis = pointEpoch,
                formattedTime = timeSdf.format(Date(pointEpoch)),
                probabilityPercent = prob,
                expectedAutoMin = autoMin,
                expectedAutoMax = autoMax,
                expectedWaitMin = waitMin,
                expectedWaitMax = waitMax,
                isTargetTime = offset == 0
            )
        }
    }

    private suspend fun fetchCorridorActiveDrivers(): Int {
        return try {
            val snapshot = database.reference.child("auto_status").get().await()
            var count = 0
            for (child in snapshot.children) {
                val status = child.child("status").getValue(String::class.java)
                if (status == "AVAILABLE" || status == "Available" || status == "MOVING") {
                    count++
                }
            }
            count
        } catch (e: Exception) {
            1 // Fallback minimum baseline
        }
    }

    private suspend fun fetchCorridorAvailableSeats(): Int {
        return try {
            val snapshot = database.reference.child("auto_status").get().await()
            var totalSeats = 0
            for (child in snapshot.children) {
                val seats = child.child("availableSeats").getValue(Int::class.java) ?: 0
                totalSeats += seats
            }
            totalSeats
        } catch (e: Exception) {
            4
        }
    }

    private suspend fun fetchPickupStopWaitingCount(
        routeId: String,
        direction: String,
        pickupStop: String
    ): Int {
        return try {
            val snapshot = database.reference
                .child("passenger_demand")
                .child(routeId)
                .child(direction)
                .child(pickupStop)
                .child("waitingPassengers")
                .get()
                .await()
            snapshot.childrenCount.toInt()
        } catch (e: Exception) {
            0
        }
    }

    private data class PriorMetrics(
        val probability: Int,
        val expectedAutos: Double,
        val expectedWaitMin: Int,
        val expectedWaitMax: Int,
        val baseConfidence: Int
    )

    private fun computeTimeOfDayPrior(hour: Int, minute: Int, dayOfWeek: Int): PriorMetrics {
        val isWeekend = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY

        return when {
            // Peak afternoon college / school departure: 3:30 PM to 5:15 PM
            hour == 15 && minute >= 30 || hour == 16 || (hour == 17 && minute <= 15) -> {
                if (!isWeekend) {
                    PriorMetrics(
                        probability = 87,
                        expectedAutos = 5.0,
                        expectedWaitMin = 5,
                        expectedWaitMax = 10,
                        baseConfidence = 85
                    )
                } else {
                    PriorMetrics(
                        probability = 68,
                        expectedAutos = 3.0,
                        expectedWaitMin = 8,
                        expectedWaitMax = 14,
                        baseConfidence = 70
                    )
                }
            }
            // Morning peak commute: 8:00 AM to 10:00 AM
            hour in 8..9 || (hour == 10 && minute <= 15) -> {
                PriorMetrics(
                    probability = 84,
                    expectedAutos = 4.5,
                    expectedWaitMin = 6,
                    expectedWaitMax = 11,
                    baseConfidence = 82
                )
            }
            // Midday: 11:00 AM to 2:30 PM
            hour in 11..13 || (hour == 14 && minute <= 30) -> {
                PriorMetrics(
                    probability = 65,
                    expectedAutos = 2.5,
                    expectedWaitMin = 10,
                    expectedWaitMax = 16,
                    baseConfidence = 72
                )
            }
            // Evening market rush: 6:00 PM to 8:00 PM
            hour in 18..19 -> {
                PriorMetrics(
                    probability = 76,
                    expectedAutos = 3.8,
                    expectedWaitMin = 7,
                    expectedWaitMax = 12,
                    baseConfidence = 78
                )
            }
            // Late night: 9:00 PM to 11:00 PM
            hour in 21..23 -> {
                PriorMetrics(
                    probability = 42,
                    expectedAutos = 1.5,
                    expectedWaitMin = 15,
                    expectedWaitMax = 25,
                    baseConfidence = 60
                )
            }
            // Early morning: before 7:00 AM
            hour < 7 -> {
                PriorMetrics(
                    probability = 35,
                    expectedAutos = 1.0,
                    expectedWaitMin = 18,
                    expectedWaitMax = 30,
                    baseConfidence = 55
                )
            }
            // Default daytime shoulder
            else -> {
                PriorMetrics(
                    probability = 72,
                    expectedAutos = 3.2,
                    expectedWaitMin = 8,
                    expectedWaitMax = 13,
                    baseConfidence = 75
                )
            }
        }
    }
}
