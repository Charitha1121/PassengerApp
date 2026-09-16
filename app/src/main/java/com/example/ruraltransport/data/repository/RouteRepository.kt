package com.example.ruraltransport.data.repository

import com.example.ruraltransport.data.model.JourneyValidationResult
import com.example.ruraltransport.data.model.RouteInfo
import com.example.ruraltransport.data.model.TransportStop
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class RouteRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://ruraltransport-54174-default-rtdb.asia-southeast1.firebasedatabase.app")
) {

    val canonicalStops = listOf(
        TransportStop(
            id = "STOP_01",
            name = "Gurramguda",
            latitude = 17.2942,
            longitude = 78.5675,
            sequence = 1
        ),
        TransportStop(
            id = "STOP_02",
            name = "Jay Suryapatnam",
            latitude = 17.2885,
            longitude = 78.5605,
            sequence = 2
        ),
        TransportStop(
            id = "STOP_03",
            name = "Sphoorthy College",
            latitude = 17.2820,
            longitude = 78.5538,
            sequence = 3
        ),
        TransportStop(
            id = "STOP_04",
            name = "Nadergul",
            latitude = 17.2746,
            longitude = 78.5400,
            sequence = 4
        )
    )

    val defaultRoute = RouteInfo(
        id = "ROUTE_01",
        name = "Gurramguda — Nadergul Corridor",
        stops = canonicalStops
    )

    suspend fun getRoutes(): List<RouteInfo> {
        return try {
            val snapshot = database.reference.child("routes").get().await()
            if (snapshot.exists()) {
                val routes = mutableListOf<RouteInfo>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java) ?: child.key ?: ""
                    val name = child.child("name").getValue(String::class.java) ?: "Rural Corridor"
                    val stopsList = mutableListOf<TransportStop>()
                    val stopsSnapshot = child.child("stops")
                    for (stopSnap in stopsSnapshot.children) {
                        val stop = stopSnap.getValue(TransportStop::class.java)
                        if (stop != null) stopsList.add(stop)
                    }
                    if (stopsList.isNotEmpty()) {
                        routes.add(RouteInfo(id = id, name = name, stops = stopsList.sortedBy { it.sequence }))
                    }
                }
                if (routes.isNotEmpty()) routes else listOf(defaultRoute)
            } else {
                listOf(defaultRoute)
            }
        } catch (e: Exception) {
            listOf(defaultRoute)
        }
    }

    fun validateJourney(
        pickup: TransportStop?,
        destination: TransportStop?
    ): JourneyValidationResult {
        if (pickup == null) return JourneyValidationResult.MISSING_PICKUP
        if (destination == null) return JourneyValidationResult.MISSING_DESTINATION
        if (pickup.id == destination.id) return JourneyValidationResult.SAME_STOP
        return JourneyValidationResult.VALID
    }
}
