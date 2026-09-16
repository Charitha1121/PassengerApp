package com.example.ruraltransport.data.repository

import android.util.Log
import com.example.ruraltransport.data.model.DriverLocationUpdate
import com.example.ruraltransport.data.model.MatchedDriver
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RideRequest
import com.example.ruraltransport.data.model.RideStatus
import com.google.firebase.auth.FirebaseAuth
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

    companion object {
        private const val TAG = "RideRepository"

        // Type-safe Firebase RTDB helpers to avoid DatabaseException crashes on type conversion
        private fun DataSnapshot.getDoubleSafe(vararg keys: String, default: Double = 0.0): Double {
            for (k in keys) {
                val childSnap = if (k.isNotEmpty()) child(k) else this
                val v = childSnap.value ?: continue
                when (v) {
                    is Number -> return v.toDouble()
                    is String -> v.toDoubleOrNull()?.let { return it }
                }
            }
            return default
        }

        private fun DataSnapshot.getFloatSafe(vararg keys: String, default: Float = 0f): Float {
            for (k in keys) {
                val childSnap = if (k.isNotEmpty()) child(k) else this
                val v = childSnap.value ?: continue
                when (v) {
                    is Number -> return v.toFloat()
                    is String -> v.toFloatOrNull()?.let { return it }
                }
            }
            return default
        }

        private fun DataSnapshot.getLongSafe(vararg keys: String, default: Long = 0L): Long {
            for (k in keys) {
                val childSnap = if (k.isNotEmpty()) child(k) else this
                val v = childSnap.value ?: continue
                when (v) {
                    is Number -> return v.toLong()
                    is String -> v.toLongOrNull()?.let { return it }
                }
            }
            return default
        }

        private fun DataSnapshot.getIntSafe(vararg keys: String, default: Int = 0): Int {
            for (k in keys) {
                val childSnap = if (k.isNotEmpty()) child(k) else this
                val v = childSnap.value ?: continue
                when (v) {
                    is Number -> return v.toInt()
                    is String -> v.toIntOrNull()?.let { return it }
                }
            }
            return default
        }

        private fun DataSnapshot.getBooleanSafe(vararg keys: String, default: Boolean = false): Boolean {
            for (k in keys) {
                val childSnap = if (k.isNotEmpty()) child(k) else this
                val v = childSnap.value ?: continue
                when (v) {
                    is Boolean -> return v
                    is Number -> return v.toInt() != 0
                    is String -> return v.equals("true", ignoreCase = true) || v == "1"
                }
            }
            return default
        }

        private fun DataSnapshot.getStringSafe(vararg keys: String, default: String = ""): String {
            for (k in keys) {
                val childSnap = if (k.isNotEmpty()) child(k) else this
                val v = childSnap.value ?: continue
                return v.toString()
            }
            return default
        }
    }

    // Bug 3 Fix: Contract requires node "ride_requests" (snake_case)
    private val requestsRef = database.reference.child("ride_requests")
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
            Log.e(TAG, "submitWaitingDemand error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createRideRequest(request: RideRequest): Result<RideRequest> {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
                ?: throw IllegalStateException("Firebase Auth session is not active. User must be signed in to request a ride.")
            val currentUid = currentUser.uid

            val requestId = requestsRef.push().key ?: throw Exception("Could not generate request ID")
            // Bug 3 Fix: passengerId set to auth.currentUser.uid before write, and status set to lowercase "pending"
            val newRequest = request.copy(
                requestId = requestId,
                passengerId = currentUid,
                status = "pending",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            requestsRef.child(requestId).setValue(newRequest).await()
            Log.i(TAG, "Created ride request successfully: ride_requests/$requestId for passengerId=$currentUid with status=pending")
            Result.success(newRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ride request at ride_requests: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeRideRequest(requestId: String): Flow<RideRequest?> = callbackFlow {
        val ref = requestsRef.child(requestId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val ride = snapshot.getValue(RideRequest::class.java)
                    trySend(ride)
                } catch (e: Exception) {
                    Log.w(TAG, "Error deserializing ride request $requestId: ${e.message}", e)
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeRideRequest onCancelled for $requestId: ${error.message} (code: ${error.code})")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeDriverLocation(driverId: String): Flow<DriverLocationUpdate?> = callbackFlow {
        val ref = database.reference.child("drivers").child(driverId).child("liveTracking")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                try {
                    val lat = snapshot.getDoubleSafe("lat", "latitude", default = 0.0)
                    val lng = snapshot.getDoubleSafe("lng", "longitude", default = 0.0)
                    val speed = snapshot.getFloatSafe("speed", default = 0f)
                    val heading = snapshot.getFloatSafe("heading", "bearing", default = 0f)
                    val ts = snapshot.getLongSafe("lastUpdated", "timestamp", default = System.currentTimeMillis())

                    trySend(
                        DriverLocationUpdate(
                            driverId = driverId,
                            latitude = lat,
                            longitude = lng,
                            speed = speed,
                            bearing = heading,
                            timestamp = ts
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "observeDriverLocation error for driver $driverId: ${e.message}", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeDriverLocation onCancelled: ${error.message}")
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
            if (status != null && status.equals("completed", ignoreCase = true)) {
                throw Exception("Cannot cancel a ride that has already been completed.")
            }
            // Bug 3 Fix: Exact lowercase string "cancelled" matching contract
            requestsRef.child(requestId).child("status").setValue("cancelled").await()
            requestsRef.child(requestId).child("updatedAt").setValue(System.currentTimeMillis()).await()
            Log.i(TAG, "Ride request $requestId successfully cancelled.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel ride request $requestId: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getPassengerRideHistory(passengerId: String): Flow<List<RideRequest>> = callbackFlow {
        val query = requestsRef.orderByChild("passengerId").equalTo(passengerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RideRequest>()
                for (child in snapshot.children) {
                    try {
                        val request = child.getValue(RideRequest::class.java)
                        if (request != null) {
                            list.add(request)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error deserializing ride history item ${child.key}: ${e.message}")
                    }
                }
                trySend(list.sortedByDescending { it.createdAt })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "getPassengerRideHistory onCancelled: ${error.message} (code: ${error.code})")
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    // ============================================================
    // DIRECTION-AWARE WAITING DEMAND
    // ============================================================

    data class ActiveWaitingSession(
        val routeId: String,
        val direction: com.example.ruraltransport.data.model.RouteDirection,
        val pickupStop: String,
        val destinationStop: String,
        val timestamp: Long
    )

    suspend fun startWaitingRequest(
        routeId: String,
        direction: com.example.ruraltransport.data.model.RouteDirection,
        pickupStop: String,
        destinationStop: String,
        passengerUid: String
    ): Result<Unit> {
        return try {
            // Bug 1 Fix: Stop node at passenger_demand/{routeId}/{direction.name}/{pickupStop}
            val stopRef = demandRef
                .child(routeId)
                .child(direction.name)
                .child(pickupStop)

            val waitRef = stopRef
                .child("waitingPassengers")
                .child(passengerUid)

            val payload = mapOf(
                "destinationStop" to destinationStop,
                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            // Flat write for legacy compatibility
            val legacyRef = demandRef.child(passengerUid)
            val legacyPayload = mapOf(
                "demandId" to passengerUid,
                "passengerId" to passengerUid,
                "stopId" to pickupStop,
                "stopName" to pickupStop,
                "requestedSeats" to 1,
                "status" to "WAITING",
                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
            )

            // Register automatic cleanup on disconnect
            waitRef.onDisconnect().removeValue()
            legacyRef.onDisconnect().removeValue()

            // Write passenger entry
            waitRef.setValue(payload).await()
            legacyRef.setValue(legacyPayload).await()

            // Bug 1 Fix: Update waitingCount (int) at the stop node for observeDirectionDemand()
            val currentPassengersSnap = stopRef.child("waitingPassengers").get().await()
            val count = currentPassengersSnap.childrenCount.toInt().coerceAtLeast(1)
            stopRef.child("waitingCount").setValue(count).await()
            Log.i(TAG, "Waiting demand registered at passenger_demand/$routeId/${direction.name}/$pickupStop with waitingCount=$count")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start waiting request at passenger_demand/$routeId/${direction.name}/$pickupStop: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun stopWaitingRequest(
        routeId: String,
        direction: com.example.ruraltransport.data.model.RouteDirection,
        pickupStop: String,
        passengerUid: String
    ): Result<Unit> {
        return try {
            val stopRef = demandRef
                .child(routeId)
                .child(direction.name)
                .child(pickupStop)

            val waitRef = stopRef
                .child("waitingPassengers")
                .child(passengerUid)

            val legacyRef = demandRef.child(passengerUid)

            // Cancel onDisconnect listeners and remove the nodes
            try { waitRef.onDisconnect().cancel().await() } catch (_: Exception) {}
            try { legacyRef.onDisconnect().cancel().await() } catch (_: Exception) {}

            waitRef.removeValue().await()
            legacyRef.removeValue().await()

            // Bug 1 Fix: Recalculate remaining waitingCount at the stop node
            val remainingSnap = stopRef.child("waitingPassengers").get().await()
            val remainingCount = remainingSnap.childrenCount.toInt()
            stopRef.child("waitingCount").setValue(remainingCount).await()
            Log.i(TAG, "Waiting demand removed for $passengerUid at passenger_demand/$routeId/${direction.name}/$pickupStop, remainingCount=$remainingCount")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop waiting request: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeWaitingPassengersCount(
        routeId: String,
        direction: com.example.ruraltransport.data.model.RouteDirection,
        pickupStop: String
    ): Flow<Int> = callbackFlow {
        val stopRef = demandRef
            .child(routeId)
            .child(direction.name)
            .child(pickupStop)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Bug 1 Fix: Read child("waitingCount") first, fall back to waitingPassengers.childrenCount
                val count = snapshot.child("waitingCount").getValue(Int::class.java)
                    ?: snapshot.child("waitingPassengers").childrenCount.toInt()
                trySend(count)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeWaitingPassengersCount onCancelled: ${error.message}")
                close(error.toException())
            }
        }

        stopRef.addValueEventListener(listener)
        awaitClose { stopRef.removeEventListener(listener) }
    }

    suspend fun findAndHandleExistingWaitingSession(passengerUid: String): Result<ActiveWaitingSession?> {
        return try {
            val snapshot = demandRef.get().await()
            var activeSession: ActiveWaitingSession? = null
            val now = System.currentTimeMillis()
            val staleThreshold = 30 * 60 * 1000L // 30 minutes

            for (routeChild in snapshot.children) {
                val routeId = routeChild.key ?: continue
                for (dirChild in routeChild.children) {
                    val dirName = dirChild.key ?: continue
                    val direction = try {
                        com.example.ruraltransport.data.model.RouteDirection.valueOf(dirName)
                    } catch (e: Exception) {
                        continue
                    }
                    for (stopChild in dirChild.children) {
                        val pickupStop = stopChild.key ?: continue
                        val waitPassNode = stopChild.child("waitingPassengers").child(passengerUid)
                        if (waitPassNode.exists()) {
                            val dest = waitPassNode.child("destinationStop").getValue(String::class.java) ?: ""
                            val ts = waitPassNode.child("timestamp").getValue(Long::class.java) ?: 0L

                            if (now - ts > staleThreshold) {
                                // Clean up stale entry
                                waitPassNode.ref.removeValue().await()
                                val remaining = stopChild.child("waitingPassengers").childrenCount.toInt()
                                stopChild.ref.child("waitingCount").setValue(remaining).await()
                            } else {
                                // Active entry found: re-arm onDisconnect and return
                                waitPassNode.ref.onDisconnect().removeValue()
                                activeSession = ActiveWaitingSession(
                                    routeId = routeId,
                                    direction = direction,
                                    pickupStop = pickupStop,
                                    destinationStop = dest,
                                    timestamp = ts
                                )
                                break
                            }
                        }
                    }
                    if (activeSession != null) break
                }
                if (activeSession != null) break
            }

            Result.success(activeSession)
        } catch (e: Exception) {
            Log.e(TAG, "findAndHandleExistingWaitingSession error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ============================================================
    // CORRIDOR DRIVERS LIVE OBSERVATION & FILTERING
    // ============================================================

    fun observeDriversOnCorridor(
        routeId: String,
        direction: com.example.ruraltransport.data.model.RouteDirection,
        pickupStop: String
    ): Flow<List<com.example.ruraltransport.data.model.LiveDriverUiModel>> = callbackFlow {
        val driversRef = database.reference.child("drivers")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val matchingList = mutableListOf<com.example.ruraltransport.data.model.LiveDriverUiModel>()

                    for (driverSnap in snapshot.children) {
                        try {
                            val uid = driverSnap.key ?: continue
                            val isOnline = driverSnap.getBooleanSafe("isOnline", default = false)
                            val driverRouteId = driverSnap.getStringSafe("routeId")
                            val activeDirection = driverSnap.getStringSafe("activeDirection")
                            val currentStop = driverSnap.getStringSafe("currentStop")
                            val name = driverSnap.getStringSafe("name", default = "Auto Driver")
                            val phone = driverSnap.getStringSafe("phone")
                            val vehicleNumber = driverSnap.getStringSafe("vehicleNumber", default = "Auto #${uid.takeLast(4).uppercase()}")
                            val availableSeats = driverSnap.getIntSafe("availableSeats", default = 3)

                            // Parse lat, lng, heading safely to avoid DatabaseException on Long/Float type conversion
                            val liveLocSnap = driverSnap.child("liveLocation")
                            val lat = if (liveLocSnap.exists()) {
                                liveLocSnap.getDoubleSafe("latitude", "lat")
                            } else {
                                driverSnap.getDoubleSafe("latitude", "lat")
                            }
                            val lng = if (liveLocSnap.exists()) {
                                liveLocSnap.getDoubleSafe("longitude", "lng")
                            } else {
                                driverSnap.getDoubleSafe("longitude", "lng")
                            }

                            val heading = if (liveLocSnap.exists()) {
                                liveLocSnap.getFloatSafe("heading", "bearing")
                            } else {
                                driverSnap.getFloatSafe("heading", "bearing")
                            }

                            val speed = if (liveLocSnap.exists()) {
                                liveLocSnap.getFloatSafe("speed")
                            } else {
                                driverSnap.getFloatSafe("speed")
                            }

                            val timestamp = if (liveLocSnap.exists()) {
                                liveLocSnap.getLongSafe("timestamp", default = System.currentTimeMillis())
                            } else {
                                driverSnap.getLongSafe("timestamp", default = System.currentTimeMillis())
                            }

                            // Filtering Rules:
                            // 1. Must be online
                            if (!isOnline) continue

                            // 2. Must match the route corridor (if corridor is specified)
                            if (routeId.isNotBlank() && driverRouteId.isNotBlank() && !driverRouteId.equals(routeId, ignoreCase = true)) {
                                continue
                            }

                            // 3. Must be travelling the same direction
                            if (!activeDirection.equals(direction.name, ignoreCase = true)) {
                                continue
                            }

                            // 4. Validate coordinates are finite and non-zero
                            if (lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0)) {
                                continue
                            }

                            // 5. Must be AT OR BEFORE the passenger's pickup stop in that direction
                            val isAtOrBefore = com.example.ruraltransport.data.model.RouteData.isDriverAtOrBeforePickup(
                                driverCurrentStop = currentStop,
                                passengerPickupStop = pickupStop,
                                direction = direction
                            )
                            if (!isAtOrBefore) {
                                continue
                            }

                            val stopsAway = com.example.ruraltransport.data.model.RouteData.stopsAway(
                                driverCurrentStop = currentStop,
                                passengerPickupStop = pickupStop,
                                direction = direction
                            )
                            val isAtPassengerStop = stopsAway == 0 &&
                                    com.example.ruraltransport.data.model.RouteData.indexOf(currentStop) ==
                                    com.example.ruraltransport.data.model.RouteData.indexOf(pickupStop)

                            matchingList.add(
                                com.example.ruraltransport.data.model.LiveDriverUiModel(
                                    driverId = uid,
                                    name = name,
                                    phone = phone,
                                    vehicleNumber = vehicleNumber,
                                    currentStop = com.example.ruraltransport.data.model.RouteData.getStopName(currentStop),
                                    position = com.google.android.gms.maps.model.LatLng(lat, lng),
                                    heading = if (heading.isNaN()) 0f else heading,
                                    speed = if (speed.isNaN()) 0f else speed,
                                    availableSeats = availableSeats,
                                    stopsAway = stopsAway,
                                    isAtPassengerStop = isAtPassengerStop,
                                    lastUpdated = timestamp
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed driver snapshot in observeDriversOnCorridor: ${e.message}")
                        }
                    }

                    // Sort: nearest drivers first (by stopsAway)
                    trySend(matchingList.sortedBy { it.stopsAway })
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing drivers snapshot: ${e.message}", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeDriversOnCorridor onCancelled: ${error.message} (code: ${error.code})")
                close(error.toException())
            }
        }

        driversRef.addValueEventListener(listener)
        awaitClose { driversRef.removeEventListener(listener) }
    }

    /**
     * Listens to live driver GPS tracking data emitted to `drivers/{uid}/liveTracking`.
     * Filters for active rides (`isRideActive == true`) with valid coordinates.
     * Optionally filters by corridor routeId if provided.
     * Bug 2 Fix: Fully safe type coercion preventing DatabaseException crashes.
     */
    fun observeLiveTrackingDrivers(routeId: String? = null): Flow<List<LiveDriverPosition>> = callbackFlow {
        val driversRef = database.reference.child("drivers")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val liveList = mutableListOf<LiveDriverPosition>()

                    for (driverSnap in snapshot.children) {
                        try {
                            val uid = driverSnap.key ?: continue
                            val liveTrackingSnap = driverSnap.child("liveTracking")

                            // Only process drivers with live tracking data
                            if (!liveTrackingSnap.exists()) continue

                            val isRideActive = liveTrackingSnap.getBooleanSafe("isRideActive", default = false)
                            if (!isRideActive) continue

                            val lat = liveTrackingSnap.getDoubleSafe("lat", "latitude", default = 0.0)
                            val lng = liveTrackingSnap.getDoubleSafe("lng", "longitude", default = 0.0)

                            // Ignore invalid, NaN, or unset GPS coordinates
                            if (lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0)) continue

                            val heading = liveTrackingSnap.getFloatSafe("heading", "bearing", default = 0f)
                            val speed = liveTrackingSnap.getFloatSafe("speed", default = 0f)
                            val lastUpdated = liveTrackingSnap.getLongSafe("lastUpdated", "timestamp", default = System.currentTimeMillis())

                            val driverRouteId = liveTrackingSnap.getStringSafe("routeId").ifBlank {
                                driverSnap.getStringSafe("routeId")
                            }

                            if (!routeId.isNullOrBlank() && driverRouteId.isNotBlank() && !driverRouteId.equals(routeId, ignoreCase = true)) {
                                continue
                            }

                            val name = driverSnap.getStringSafe("name", default = "Auto Driver")
                            val vehicleNumber = driverSnap.getStringSafe("vehicleNumber", default = "Auto #${uid.takeLast(4).uppercase()}")

                            liveList.add(
                                LiveDriverPosition(
                                    driverUid = uid,
                                    lat = lat,
                                    lng = lng,
                                    heading = if (heading.isNaN()) 0f else heading,
                                    speed = if (speed.isNaN()) 0f else speed,
                                    isRideActive = isRideActive,
                                    lastUpdated = lastUpdated,
                                    routeId = driverRouteId,
                                    driverName = name,
                                    vehicleNumber = vehicleNumber
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed driver record in observeLiveTrackingDrivers: ${e.message}")
                        }
                    }

                    trySend(liveList)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in observeLiveTrackingDrivers onDataChange: ${e.message}", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeLiveTrackingDrivers onCancelled: ${error.message} (code: ${error.code})")
                close(error.toException())
            }
        }

        driversRef.addValueEventListener(listener)
        awaitClose { driversRef.removeEventListener(listener) }
    }
}
