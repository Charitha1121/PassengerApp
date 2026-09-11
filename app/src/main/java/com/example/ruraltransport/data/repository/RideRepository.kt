package com.example.ruraltransport.data.repository

import com.example.ruraltransport.data.model.DriverLocationUpdate
import com.example.ruraltransport.data.model.MatchedDriver
import com.example.ruraltransport.data.model.RideRequest
import com.example.ruraltransport.data.model.RideStatus
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RideRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://ruraltransport-54174-default-rtdb.asia-southeast1.firebasedatabase.app")
) {

    private val requestsRef = database.reference.child("rideRequests")
    private val demandRef = database.reference.child("passenger_demand")
    private val locationsRef = database.reference.child("locations")
    private val autoStatusRef = database.reference.child("auto_status")

    suspend fun submitWaitingDemand(
        passengerId: String,
        passengerName: String,
        routeId: String,
        pickupStopId: String,
        pickupStopName: String,
        destStopId: String,
        destStopName: String,
        requestedSeats: Int,
        targetTime: Long
    ): Result<String> {
        return try {
            val demandId = demandRef.push().key ?: throw Exception("Could not generate demand ID")
            val record = mapOf(
                "demandId" to demandId,
                "passengerId" to passengerId,
                "passengerName" to passengerName,
                "routeId" to routeId,
                "stopId" to pickupStopId,
                "stopName" to pickupStopName,
                "destinationStopId" to destStopId,
                "destinationStopName" to destStopName,
                "requestedSeats" to requestedSeats,
                "targetTime" to targetTime,
                "status" to "WAITING",
                "timestamp" to System.currentTimeMillis()
            )
            demandRef.child(demandId).setValue(record).await()
            Result.success(demandId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRideRequest(request: RideRequest): Result<RideRequest> {
        return try {
            val requestId = requestsRef.push().key ?: throw Exception("Could not generate request ID")
            val newRequest = request.copy(
                requestId = requestId,
                status = RideStatus.PENDING.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            requestsRef.child(requestId).setValue(newRequest).await()
            Result.success(newRequest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRideRequest(requestId: String): Flow<RideRequest?> = callbackFlow {
        val ref = requestsRef.child(requestId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ride = snapshot.getValue(RideRequest::class.java)
                trySend(ride)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeDriverLocation(driverId: String): Flow<DriverLocationUpdate?> = callbackFlow {
        val ref = locationsRef.child(driverId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loc = snapshot.getValue(DriverLocationUpdate::class.java)
                if (loc != null) {
                    trySend(loc)
                } else {
                    // Fallback to reading lat/lng children directly
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val speed = snapshot.child("speed").getValue(Float::class.java) ?: 0f
                    val bearing = snapshot.child("bearing").getValue(Float::class.java) ?: 0f
                    val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    trySend(
                        DriverLocationUpdate(
                            driverId = driverId,
                            latitude = lat,
                            longitude = lng,
                            speed = speed,
                            bearing = bearing,
                            timestamp = ts
                        )
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun findAvailableCorridorDrivers(minSeats: Int = 1): List<MatchedDriver> {
        return try {
            val snapshot = autoStatusRef.get().await()
            val list = mutableListOf<MatchedDriver>()
            for (child in snapshot.children) {
                val autoId = child.child("autoId").getValue(String::class.java) ?: child.key ?: ""
                val currentStop = child.child("currentStop").getValue(String::class.java) ?: ""
                val availableSeats = child.child("availableSeats").getValue(Int::class.java) ?: 0
                val status = child.child("status").getValue(String::class.java) ?: "OFFLINE"
                val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L

                if ((status.equals("AVAILABLE", ignoreCase = true) || status.equals("MOVING", ignoreCase = true)) && availableSeats >= minSeats) {
                    list.add(
                        MatchedDriver(
                            driverId = autoId,
                            name = "Driver ($autoId)",
                            phone = "9876543210",
                            vehicleNumber = autoId,
                            currentStop = currentStop,
                            availableSeats = availableSeats,
                            status = status,
                            lastUpdated = ts
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun cancelRideRequest(requestId: String): Result<Unit> {
        return try {
            val snapshot = requestsRef.child(requestId).get().await()
            val status = snapshot.child("status").getValue(String::class.java)
            if (status == RideStatus.COMPLETED.name) {
                throw Exception("Cannot cancel a ride that has already been completed.")
            }
            requestsRef.child(requestId).child("status").setValue(RideStatus.CANCELLED.name).await()
            requestsRef.child(requestId).child("updatedAt").setValue(System.currentTimeMillis()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPassengerRideHistory(passengerId: String): Flow<List<RideRequest>> = callbackFlow {
        val query = requestsRef.orderByChild("passengerId").equalTo(passengerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RideRequest>()
                for (child in snapshot.children) {
                    val request = child.getValue(RideRequest::class.java)
                    if (request != null) {
                        list.add(request)
                    }
                }
                trySend(list.sortedByDescending { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}
