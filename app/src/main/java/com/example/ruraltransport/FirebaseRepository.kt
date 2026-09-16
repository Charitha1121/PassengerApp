package com.example.ruraltransport

import com.example.ruraltransport.data.model.RouteData
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
                // Bug 1 Fix: Also maintain passenger_demand/{routeId}/{direction}/{stopName} hierarchy
                val routeId = RouteData.DEFAULT_ROUTE_ID
                val direction = "FORWARD"
                val stopRef = database.child("passenger_demand").child(routeId).child(direction).child(stopName)
                if (passengerId.isNotBlank()) {
                    stopRef.child("waitingPassengers").child(passengerId).setValue(
                        mapOf(
                            "destinationStop" to RouteData.stops.last(),
                            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
                        )
                    )
                }
                stopRef.child("waitingPassengers").get().addOnSuccessListener { snap ->
                    val count = snap.childrenCount.toInt().coerceAtLeast(1)
                    stopRef.child("waitingCount").setValue(count)
                }
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
    // UPDATE AUTO STATUS & LIVE DRIVER DATA
    // ============================================================

    fun updateAutoStatus(
        autoId: String,
        currentStop: String,
        availableSeats: Int,
        status: String,
        direction: String = "FORWARD",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val isOnline = status.uppercase() != "OFFLINE"
        val timestamp = System.currentTimeMillis()

        // 1. Update legacy auto_status node
        val autoData = mapOf(
            "autoId" to autoId,
            "currentStop" to currentStop,
            "availableSeats" to availableSeats,
            "status" to status,
            "timestamp" to timestamp
        )

        // 2. Update new drivers node (for map visibility)
        val stopIdx = RouteData.indexOf(currentStop)
        val stop = if (stopIdx >= 0) RouteData.canonicalStops[stopIdx] else null

        val driverData = mapOf(
            "isOnline" to isOnline,
            "routeId" to RouteData.DEFAULT_ROUTE_ID,
            "activeDirection" to direction,
            "currentStop" to currentStop,
            "name" to "Auto Driver $autoId",
            "phone" to "9876543210",
            "vehicleNumber" to autoId,
            "availableSeats" to availableSeats,
            "liveLocation" to mapOf(
                "latitude" to (stop?.latitude ?: 0.0),
                "longitude" to (stop?.longitude ?: 0.0),
                "heading" to 0.0,
                "speed" to 0.0,
                "timestamp" to timestamp
            )
        )

        database.child("auto_status").child(autoId).setValue(autoData)
            .addOnSuccessListener {
                database.child("drivers").child(autoId).setValue(driverData)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.message ?: "Failed to update drivers node") }
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to update auto status")
            }
    }
}