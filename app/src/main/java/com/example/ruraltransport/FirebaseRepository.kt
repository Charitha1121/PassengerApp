package com.example.ruraltransport

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseRepository {

    private val database =
        FirebaseDatabase.getInstance("https://ruraltransport-54174-default-rtdb.asia-southeast1.firebasedatabase.app").reference

    // ============================================================
    // WRITE PASSENGER DEMAND
    // ============================================================

    fun submitPassengerDemand(
        stopId: String,
        stopName: String,
        passengerId: String = "",
        passengerName: String = "Passenger",
        requestedSeats: Int = 1,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val demandId = database
            .child("passenger_demand")
            .push()
            .key

        if (demandId == null) {
            onError("Could not create demand ID")
            return
        }

        val demandData = mapOf(
            "demandId" to demandId,
            "passengerId" to passengerId,
            "passengerName" to passengerName,
            "stopId" to stopId,
            "stopName" to stopName,
            "requestedSeats" to requestedSeats,
            "status" to "WAITING",
            "timestamp" to System.currentTimeMillis()
        )

        database
            .child("passenger_demand")
            .child(demandId)
            .setValue(demandData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to submit demand")
            }
    }

    // ============================================================
    // REAL-TIME PASSENGER DEMAND
    // ============================================================

    fun observePassengerDemand(
        stopId: String,
        onDemandChanged: (Int) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val demandReference = database.child("passenger_demand")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalPassengers = 0
                for (demandSnapshot in snapshot.children) {
                    val recordStopId = demandSnapshot.child("stopId").getValue(String::class.java)
                    val status = demandSnapshot.child("status").getValue(String::class.java)
                    val requestedSeats = demandSnapshot.child("requestedSeats").getValue(Int::class.java) ?: 0

                    if (recordStopId == stopId && status == "WAITING") {
                        totalPassengers += requestedSeats
                    }
                }
                onDemandChanged(totalPassengers)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        demandReference.addValueEventListener(listener)
        return listener
    }

    // ============================================================
    // REMOVE LISTENER
    // ============================================================

    fun removePassengerDemandListener(listener: ValueEventListener) {
        database.child("passenger_demand").removeEventListener(listener)
    }

    // ============================================================
    // UPDATE AUTO STATUS (Legacy Driver compatibility)
    // ============================================================

    fun updateAutoStatus(
        autoId: String,
        currentStop: String,
        availableSeats: Int,
        status: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val autoData = mapOf(
            "autoId" to autoId,
            "currentStop" to currentStop,
            "availableSeats" to availableSeats,
            "status" to status,
            "timestamp" to System.currentTimeMillis()
        )

        database
            .child("auto_status")
            .child(autoId)
            .setValue(autoData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to update auto status")
            }
    }
}