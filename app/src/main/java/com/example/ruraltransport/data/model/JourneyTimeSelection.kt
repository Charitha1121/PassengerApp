package com.example.ruraltransport.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class TimeSelectionMode {
    NOW,
    FUTURE
}

data class JourneyTimeSelection(
    val mode: TimeSelectionMode = TimeSelectionMode.NOW,
    val targetEpochMillis: Long = System.currentTimeMillis(),
    val queryEpochMillis: Long = System.currentTimeMillis()
) {

    val isPastTime: Boolean
        get() {
            if (mode == TimeSelectionMode.NOW) return false
            // Allow 5 minute grace margin for clock drift
            return targetEpochMillis < (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5))
        }

    val timeHorizonMinutes: Long
        get() {
            val delta = targetEpochMillis - queryEpochMillis
            return (delta / (1000 * 60)).coerceAtLeast(0)
        }

    val formattedTargetTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(targetEpochMillis))
        }

    val formattedQueryTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(queryEpochMillis))
        }

    val formattedTargetDate: String
        get() {
            val nowCal = Calendar.getInstance()
            val targetCal = Calendar.getInstance().apply { timeInMillis = targetEpochMillis }

            return when {
                nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR) -> {
                    "Today"
                }
                nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) + 1 == targetCal.get(Calendar.DAY_OF_YEAR) -> {
                    "Tomorrow"
                }
                else -> {
                    val sdf = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
                    sdf.format(Date(targetEpochMillis))
                }
            }
        }

    val horizonDescription: String
        get() {
            if (mode == TimeSelectionMode.NOW) return "Immediate departure"
            val totalMinutes = timeHorizonMinutes
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 && minutes > 0 -> "in $hours hrs $minutes mins"
                hours > 0 -> "in $hours hrs"
                else -> "in $minutes mins"
            }
        }
}
