package com.example.ruraltransport

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseRepository {

    private val database =
        FirebaseDatabase.getInstance().reference

    // ============================================================
    // WRITE PASSENGER DEMAND
    // ============================================================

    fun submitPassengerDemand(
        stopId: String,
        stopName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val demandId =
            database
                .child("passenger_demand")
                .push()
                .key

        if (demandId == null) {
            onError("Could not create demand ID")
            return
        }

        val demandData = mapOf(
            "stopId" to stopId,
            "stopName" to stopName,
            "requestedSeats" to 1,
            "status" to "WAITING",
            "timestamp" to System.currentTimeMillis()
        )

        database
            .child("passenger_demand")
            .child(demandId)
            .setValue(demandData)
            .addOnSuccessListener {

                // Firebase write succeeded
                onSuccess()
            }
            .addOnFailureListener { exception ->

                onError(
                    exception.message
                        ?: "Failed to submit demand"
                )
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

        val demandReference =
            database.child("passenger_demand")

        val listener =
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    var totalPassengers = 0

                    for (demandSnapshot in snapshot.children) {

                        val recordStopId =
                            demandSnapshot
                                .child("stopId")
                                .getValue(String::class.java)

                        val status =
                            demandSnapshot
                                .child("status")
                                .getValue(String::class.java)

                        val requestedSeats =
                            demandSnapshot
                                .child("requestedSeats")
                                .getValue(Int::class.java)
                                ?: 0

                        if (
                            recordStopId == stopId &&
                            status == "WAITING"
                        ) {

                            totalPassengers += requestedSeats
                        }
                    }

                    // IMPORTANT:
                    // UI receives the value from Firebase.
                    onDemandChanged(totalPassengers)
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    onError(error.message)
                }
            }

        demandReference.addValueEventListener(listener)

        return listener
    }

    // ============================================================
    // REMOVE LISTENER
    // ============================================================

    fun removePassengerDemandListener(
        listener: ValueEventListener
    ) {

        database
            .child("passenger_demand")
            .removeEventListener(listener)
    }
}