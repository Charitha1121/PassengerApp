package com.example.ruraltransport.data.repository

import android.util.Log
import com.example.ruraltransport.data.model.DriverLocationUpdate
import com.example.ruraltransport.data.model.MatchedDriver
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RideRequest
import com.example.ruraltransport.data.model.RideStatus
import com.example.ruraltransport.data.model.GeoUtils
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.data.model.RouteInfo
import com.google.android.gms.maps.model.LatLng
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

    private val demandRef = database.reference.child("passenger_demand")
    private val locationsRef = database.reference.child("locations")

    suspend fun submitWaitingDemand(
        passengerId: String,
        passengerName: String,
        routeId: String,
        direction: RouteDirection,
        pickupStop: String,
        destinationStop: String
    ): Result<Unit> {
        return try {
            val demandNode = demandRef
                .child(routeId)
                .child(direction.name)
                .child(pickupStop)
                .child("waitingPassengers")
                .child(passengerId)

            val demandData = mapOf(
                "passengerName" to passengerName,
                "destinationStop" to destinationStop,
                "timestamp" to System.currentTimeMillis()
            )

            demandNode.setValue(demandData).await()
            demandNode.onDisconnect().removeValue()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting waiting demand: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun startWaitingRequest(
        routeId: String,
        direction: RouteDirection,
        pickupStop: String,
        destinationStop: String,
        passengerUid: String
    ): Result<Unit> {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val name = currentUser?.displayName ?: (currentUser?.email?.substringBefore("@") ?: "Passenger")
        return submitWaitingDemand(passengerUid, name, routeId, direction, pickupStop, destinationStop)
    }

    suspend fun stopWaitingRequest(
        routeId: String,
        direction: RouteDirection,
        pickupStop: String,
        passengerUid: String
    ): Result<Unit> {
        return try {
            demandRef
                .child(routeId)
                .child(direction.name)
                .child(pickupStop)
                .child("waitingPassengers")
                .child(passengerUid)
                .removeValue()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeWaitingPassengersCount(
        routeId: String,
        direction: RouteDirection,
        pickupStop: String
    ): Flow<Int> = callbackFlow {
        val stopRef = demandRef
            .child(routeId)
            .child(direction.name)
            .child(pickupStop)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
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
                        RouteDirection.valueOf(dirName)
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
                                waitPassNode.ref.removeValue()
                            } else {
                                activeSession = ActiveWaitingSession(routeId, direction, pickupStop, dest)
                                waitPassNode.ref.onDisconnect().removeValue()
                            }
                        }
                    }
                }
            }
            Result.success(activeSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class ActiveWaitingSession(
        val routeId: String,
        val direction: RouteDirection,
        val pickupStop: String,
        val destinationStop: String
    )

    fun observeDriversOnCorridor(
        routeId: String,
        direction: RouteDirection,
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
                            val liveLocSnap = driverSnap.child("liveLocation")

                            val isOnline = liveLocSnap.getBooleanSafe("isOnline", default = driverSnap.getBooleanSafe("isOnline", default = false))
                            val isAvailable = liveLocSnap.getBooleanSafe("isAvailable", default = driverSnap.getBooleanSafe("isAvailable", default = false))

                            if (!isOnline && !isAvailable) continue

                            val driverRouteId = liveLocSnap.getStringSafe("routeId", default = driverSnap.getStringSafe("routeId"))
                            val activeDirection = liveLocSnap.getStringSafe("activeDirection", default = driverSnap.getStringSafe("activeDirection"))
                            val currentStop = liveLocSnap.getStringSafe("currentStop", default = driverSnap.getStringSafe("currentStop"))
                            val name = liveLocSnap.getStringSafe("driverName", "name", default = driverSnap.getStringSafe("driverName", "name", default = "Auto Driver"))
                            val vehicleNumber = liveLocSnap.getStringSafe("vehicleNumber", default = driverSnap.getStringSafe("vehicleNumber", default = "Auto #${uid.takeLast(4).uppercase()}"))
                            val phone = liveLocSnap.getStringSafe("phone", default = driverSnap.getStringSafe("phone"))
                            val availableSeats = liveLocSnap.getIntSafe("availableSeats", default = driverSnap.getIntSafe("availableSeats", default = 3))

                            val lat = liveLocSnap.getDoubleSafe("latitude", "lat", default = driverSnap.getDoubleSafe("latitude", "lat"))
                            val lng = liveLocSnap.getDoubleSafe("longitude", "lng", default = driverSnap.getDoubleSafe("longitude", "lng"))
                            val heading = liveLocSnap.getFloatSafe("heading", "bearing", default = driverSnap.getFloatSafe("heading", "bearing"))
                            val speed = liveLocSnap.getFloatSafe("speed", default = driverSnap.getFloatSafe("speed"))
                            val timestamp = liveLocSnap.getLongSafe("timestamp", "lastUpdated", default = driverSnap.getLongSafe("timestamp", "lastUpdated", default = System.currentTimeMillis()))

                            val routeMatches = routeId.isBlank() || driverRouteId.isBlank() ||
                                    driverRouteId.equals(routeId, ignoreCase = true) ||
                                    driverRouteId.contains(routeId, ignoreCase = true) ||
                                    routeId.contains(driverRouteId, ignoreCase = true) ||
                                    (routeId.contains("ROUTE_01", ignoreCase = true) && driverRouteId.contains("Gurramguda", ignoreCase = true)) ||
                                    (driverRouteId.contains("ROUTE_01", ignoreCase = true) && routeId.contains("Gurramguda", ignoreCase = true))
                            if (!routeMatches) continue

                            if (activeDirection.isNotBlank() && !activeDirection.equals(direction.name, ignoreCase = true)) continue

                            if (!lat.isFinite() || !lng.isFinite() || (lat == 0.0 && lng == 0.0)) continue

                            val isAtOrBefore = com.example.ruraltransport.data.model.RouteData.isDriverAtOrBeforePickup(
                                driverCurrentStop = currentStop,
                                passengerPickupStop = pickupStop,
                                direction = direction
                            )
                            if (!isAtOrBefore) continue

                            val stopsAway = com.example.ruraltransport.data.model.RouteData.stopsAway(
                                driverCurrentStop = currentStop,
                                passengerPickupStop = pickupStop,
                                direction = direction
                            )

                            matchingList.add(
                                com.example.ruraltransport.data.model.LiveDriverUiModel(
                                    driverId = uid,
                                    name = name,
                                    phone = phone,
                                    vehicleNumber = vehicleNumber,
                                    currentStop = currentStop,
                                    position = LatLng(lat, lng),
                                    heading = heading,
                                    speed = speed,
                                    availableSeats = availableSeats,
                                    stopsAway = stopsAway,
                                    isAtPassengerStop = stopsAway == 0,
                                    lastUpdated = timestamp
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed driver snapshot: ${e.message}")
                        }
                    }
                    trySend(matchingList.sortedBy { it.stopsAway })
                } catch (e: Exception) {
                    Log.e(TAG, "Error in observeDriversOnCorridor: ${e.message}", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        driversRef.addValueEventListener(listener)
        awaitClose { driversRef.removeEventListener(listener) }
    }

    fun observeCorridorBrowseDrivers(
        route: RouteInfo? = null,
        routeId: String? = route?.id,
        direction: RouteDirection? = null,
        pickupLatLng: LatLng? = null
    ): Flow<List<LiveDriverPosition>> = callbackFlow {
        val driversRef = database.reference.child("drivers")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val browseList = mutableListOf<LiveDriverPosition>()

                    for (driverSnap in snapshot.children) {
                        try {
                            val uid = driverSnap.key ?: continue
                            val liveLocSnap = driverSnap.child("liveLocation")

                            val isOnline = liveLocSnap.getBooleanSafe("isOnline", default = driverSnap.getBooleanSafe("isOnline", default = false))
                            val isAvailable = liveLocSnap.getBooleanSafe("isAvailable", default = driverSnap.getBooleanSafe("isAvailable", default = false))
                            val isRideActive = liveLocSnap.getBooleanSafe("isRideActive", default = driverSnap.getBooleanSafe("isRideActive", default = false))
                            val availableSeats = liveLocSnap.getIntSafe("availableSeats", default = driverSnap.getIntSafe("availableSeats", default = 3))

                            if (!isOnline && !isAvailable) continue

                            val driverRouteId = liveLocSnap.getStringSafe("routeId", default = driverSnap.getStringSafe("routeId"))
                            val activeDirection = liveLocSnap.getStringSafe("activeDirection", default = driverSnap.getStringSafe("activeDirection"))
                            val currentStop = liveLocSnap.getStringSafe("currentStop", default = driverSnap.getStringSafe("currentStop"))
                            val name = liveLocSnap.getStringSafe("driverName", "name", default = driverSnap.getStringSafe("driverName", "name", default = "Auto Driver"))
                            val vehicleNumber = liveLocSnap.getStringSafe("vehicleNumber", default = driverSnap.getStringSafe("vehicleNumber", default = "Auto #${uid.takeLast(4).uppercase()}"))
                            val phone = liveLocSnap.getStringSafe("phone", default = driverSnap.getStringSafe("phone"))

                            val lat = liveLocSnap.getDoubleSafe("latitude", "lat", default = driverSnap.getDoubleSafe("latitude", "lat"))
                            val lng = liveLocSnap.getDoubleSafe("longitude", "lng", default = driverSnap.getDoubleSafe("longitude", "lng"))
                            val heading = liveLocSnap.getFloatSafe("heading", "bearing", default = driverSnap.getFloatSafe("heading", "bearing"))
                            val speed = liveLocSnap.getFloatSafe("speed", default = driverSnap.getFloatSafe("speed"))
                            val timestamp = liveLocSnap.getLongSafe("lastUpdated", "timestamp", default = driverSnap.getLongSafe("lastUpdated", "timestamp", default = System.currentTimeMillis()))

                            val tempDriver = LiveDriverPosition(
                                driverUid = uid,
                                lat = lat,
                                lng = lng,
                                heading = if (heading.isNaN()) 0f else heading,
                                speed = if (speed.isNaN()) 0f else speed,
                                isRideActive = isRideActive,
                                isAvailable = isAvailable,
                                availableSeats = availableSeats,
                                lastUpdated = timestamp,
                                routeId = driverRouteId,
                                activeDirection = activeDirection,
                                currentStop = currentStop,
                                driverName = name,
                                vehicleNumber = vehicleNumber,
                                phone = phone
                            )

                            val routeMatches = if (route != null) {
                                RouteData.isDriverMatchingRoute(tempDriver, route, direction)
                            } else if (!routeId.isNullOrBlank()) {
                                RouteData.isDriverMatchingRoute(tempDriver, routeId, direction)
                            } else {
                                true
                            }
                            if (!routeMatches) continue

                            if (!lat.isFinite() || !lng.isFinite() || (lat == 0.0 && lng == 0.0)) continue

                            var distanceKm: Double? = null
                            var etaMinutes: Int? = null
                            if (pickupLatLng != null) {
                                val dist = GeoUtils.calculateDistanceKm(lat, lng, pickupLatLng.latitude, pickupLatLng.longitude)
                                distanceKm = dist
                                etaMinutes = GeoUtils.calculateEtaMinutes(dist, 20.0)
                            }

                            browseList.add(
                                tempDriver.copy(
                                    distanceKmToPickup = distanceKm,
                                    etaMinutesToPickup = etaMinutes
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed driver snapshot: ${e.message}")
                        }
                    }

                    val sorted = if (pickupLatLng != null) {
                        browseList.sortedBy { it.distanceKmToPickup ?: Double.MAX_VALUE }
                    } else {
                        browseList.sortedByDescending { it.lastUpdated }
                    }
                    trySend(sorted)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in observeCorridorBrowseDrivers: ${e.message}", e)
                    trySend(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        driversRef.addValueEventListener(listener)
        awaitClose { driversRef.removeEventListener(listener) }
    }

    fun observeDriverLocation(driverId: String): Flow<DriverLocationUpdate?> = callbackFlow {
        val trackingRef = database.reference.child("drivers").child(driverId).child("liveTracking")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }

                val update = DriverLocationUpdate(
                    latitude = snapshot.getDoubleSafe("lat"),
                    longitude = snapshot.getDoubleSafe("lng"),
                    bearing = snapshot.getFloatSafe("heading"),
                    speed = snapshot.getFloatSafe("speed"),
                    timestamp = snapshot.getLongSafe("lastUpdated")
                )
                trySend(update)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        trackingRef.addValueEventListener(listener)
        awaitClose { trackingRef.removeEventListener(listener) }
    }

}
