package com.example.ruraltransport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.core.content.ContextCompat

import com.example.ruraltransport.ui.theme.RuralTransportTheme

import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.example.ruraltransport.ui.navigation.AppNavigation
import com.google.firebase.auth.FirebaseAuth


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.ruraltransport.data.model.LiveDriverUiModel
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.ui.home.PassengerViewModel
import com.example.ruraltransport.data.model.TransportStop
import com.example.ruraltransport.data.repository.RouteRepository
import com.example.ruraltransport.ui.home.HomeScreen
import com.example.ruraltransport.ui.home.JourneyViewModel
import com.example.ruraltransport.ui.forecast.ForecastScreen
import com.example.ruraltransport.ui.forecast.ForecastViewModel
import com.example.ruraltransport.ui.ride.ActiveRideScreen
import com.example.ruraltransport.ui.ride.ActiveRideViewModel
import com.example.ruraltransport.ui.map.LiveTrackingMapScreen
import androidx.lifecycle.viewmodel.compose.viewModel

// ============================================================
// DATA MODELS
// ============================================================

data class Auto(
    val id: String,
    val currentStop: String,
    val availableSeats: Int,
    val status: String,
    val lastUpdated: String
)

data class PassengerDemand(
    val stopName: String,
    val waitingPassengers: Int,
    val predictedDemand: Int,
    val demandLevel: String
)

data class AvailabilityPrediction(
    val availabilityProbability: Int,
    val expectedWaitMinutes: Int,
    val estimatedSeats: Int,
    val confidence: String
)


// ============================================================
// RURAL ROUTE
// ============================================================

val ruralRoute = listOf(

    TransportStop(
        id = "STOP_01",
        name = "Gurramguda",
        latitude = 16.5062,
        longitude = 80.6480,
        sequence = 1
    ),

    TransportStop(
        id = "STOP_02",
        name = "Jay Suryapatnam",
        latitude = 16.5000,
        longitude = 80.6550,
        sequence = 2
    ),

    TransportStop(
        id = "STOP_03",
        name = "Sphoorthy College",
        latitude = 16.4950,
        longitude = 80.6620,
        sequence = 3
    ),

    TransportStop(
        id = "STOP_04",
        name = "Nadergul",
        latitude = 16.4900,
        longitude = 80.6700,
        sequence = 4
    )
)


// ============================================================
// SAMPLE AUTO DATA
// TEMPORARY
// ============================================================

val sampleAutos = listOf(

    Auto(
        id = "A01",
        currentStop = "Gurramguda",
        availableSeats = 4,
        status = "Available",
        lastUpdated = "1 min ago"
    ),

    Auto(
        id = "A02",
        currentStop = "Jay Suryapatnam",
        availableSeats = 2,
        status = "Moving",
        lastUpdated = "2 min ago"
    )
)


// ============================================================
// SAMPLE DEMAND
// TEMPORARY
// ============================================================

val sampleDemand = listOf(

    PassengerDemand(
        stopName = "Jay Suryapatnam",
        waitingPassengers = 4,
        predictedDemand = 5,
        demandLevel = "HIGH"
    ),

    PassengerDemand(
        stopName = "Sphoorthy College",
        waitingPassengers = 2,
        predictedDemand = 3,
        demandLevel = "MEDIUM"
    ),

    PassengerDemand(
        stopName = "Nadergul",
        waitingPassengers = 0,
        predictedDemand = 1,
        demandLevel = "LOW"
    )
)



// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            RuralTransportTheme {

                val journeyViewModel: JourneyViewModel = viewModel()
                val passengerViewModel: PassengerViewModel = viewModel()

                AppNavigation(
                    homeContent = { onNavigateToProfile, onNavigateToMap ->
                        RuralTransportApp(
                            onNavigateToProfile = onNavigateToProfile,
                            onOpenMap = onNavigateToMap,
                            journeyViewModel = journeyViewModel,
                            passengerViewModel = passengerViewModel
                        )
                    },
                    mapContent = { onBack ->
                        LiveTrackingMapScreen(
                            journeyViewModel = journeyViewModel,
                            passengerViewModel = passengerViewModel,
                            onBack = onBack
                        )
                    }
                )
            }
        }
    }
}


// ============================================================
// APP SCREENS
// ============================================================

enum class AppScreen {

    HOME,
    DEMAND,
    AVAILABILITY,
    ACTIVE_RIDE,
    DRIVER,
    MAP
}


// ============================================================
// MAIN APP
// ============================================================

@Composable
fun RuralTransportApp(
    onNavigateToProfile: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    journeyViewModel: JourneyViewModel = viewModel(),
    forecastViewModel: ForecastViewModel = viewModel(),
    activeRideViewModel: ActiveRideViewModel = viewModel(),
    passengerViewModel: com.example.ruraltransport.ui.home.PassengerViewModel = viewModel()
) {

    var currentScreen by remember {

        mutableStateOf(
            AppScreen.HOME
        )
    }

    var selectedStop by remember {

        mutableStateOf<TransportStop?>(null)
    }

    var hasReportedWaiting by remember {

        mutableStateOf(false)
    }

    val firebaseRepository = remember {

        FirebaseRepository()
    }

    var firebaseError by remember {

        mutableStateOf<String?>(null)
    }


    when (currentScreen) {


        // ====================================================
        // HOME
        // ====================================================

        AppScreen.HOME -> {

            HomeScreen(
                journeyViewModel = journeyViewModel,
                passengerViewModel = passengerViewModel,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToForecast = { routeId, pickupId, destId, targetEpochMillis, queryEpochMillis ->
                    val foundPickup = ruralRoute.find { it.id == pickupId } ?: ruralRoute.first()
                    val foundDest = ruralRoute.find { it.id == destId } ?: ruralRoute.last()
                    selectedStop = foundPickup
                    forecastViewModel.loadForecast(
                        routeId = routeId,
                        routeName = "Gurramguda — Nadergul Corridor",
                        pickupId = pickupId,
                        pickupName = foundPickup.name,
                        destId = destId,
                        destName = foundDest.name,
                        targetEpochMillis = targetEpochMillis,
                        queryEpochMillis = queryEpochMillis
                    )
                    currentScreen = AppScreen.AVAILABILITY
                },
                onReportWaiting = { stop ->
                    val dest = journeyViewModel.uiState.value.destinationStop?.name ?: com.example.ruraltransport.data.model.RouteData.stops.last()
                    passengerViewModel.startWaiting(stop.name, dest)
                },
                onNavigateToMap = onOpenMap
            )
        }


        // ====================================================
        // PASSENGER DEMAND
        // ====================================================

        AppScreen.DEMAND -> {

            PassengerDemandScreen(

                selectedStop =
                    selectedStop,

                hasReportedWaiting =
                    hasReportedWaiting,

                onReportWaiting = {

                    selectedStop?.let { stop ->
                        val currentUser = FirebaseAuth.getInstance().currentUser

                        firebaseRepository.submitPassengerDemand(

                            stopId =
                                stop.id,

                            stopName =
                                stop.name,

                            passengerId =
                                currentUser?.uid ?: "",

                            passengerName =
                                currentUser?.displayName ?: (currentUser?.email?.substringBefore("@") ?: "Passenger"),

                            requestedSeats = 1,

                            onSuccess = {

                                hasReportedWaiting =
                                    true

                                firebaseError =
                                    null
                            },

                            onError = { error ->

                                firebaseError =
                                    error
                            }
                        )
                    }
                },

                firebaseError =
                    firebaseError,

                onViewAvailability = {
                    val stop = selectedStop ?: ruralRoute.first()
                    forecastViewModel.loadForecast(
                        routeId = "ROUTE_01",
                        routeName = "Gurramguda — Nadergul Corridor",
                        pickupId = stop.id,
                        pickupName = stop.name,
                        destId = "STOP_03",
                        destName = "Sphoorthy College",
                        targetEpochMillis = System.currentTimeMillis(),
                        queryEpochMillis = System.currentTimeMillis()
                    )
                    currentScreen = AppScreen.AVAILABILITY
                },

                onBack = {

                    currentScreen =
                        AppScreen.HOME
                }
            )
        }


        // ====================================================
        // DRIVER
        // ====================================================

        AppScreen.DRIVER -> {

            DriverStatusScreen(

                onBack = {

                    currentScreen =
                        AppScreen.HOME
                }
            )
        }


        // ====================================================
        // AVAILABILITY
        // ====================================================

        AppScreen.AVAILABILITY -> {

            ForecastScreen(
                forecastViewModel = forecastViewModel,
                passengerViewModel = passengerViewModel,
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                onWaitingHere = { stopId, stopName ->
                    val dest = journeyViewModel.uiState.value.destinationStop?.name ?: com.example.ruraltransport.data.model.RouteData.stops.last()
                    passengerViewModel.startWaiting(stopName, dest)
                },
                onRequestRide = { forecast ->
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    activeRideViewModel.requestRide(
                        passengerId = currentUser?.uid ?: "",
                        passengerName = currentUser?.displayName ?: (currentUser?.email?.substringBefore("@") ?: "Passenger"),
                        passengerPhone = "9876543210",
                        routeId = forecast.routeId,
                        routeName = forecast.routeName,
                        pickupStopId = forecast.pickupStopId,
                        pickupStopName = forecast.pickupStopName,
                        destStopId = forecast.destStopId,
                        destStopName = forecast.destStopName,
                        requestedSeats = 1,
                        targetTime = forecast.targetEpochMillis
                    )
                    currentScreen = AppScreen.ACTIVE_RIDE
                }
            )
        }


        // ====================================================
        // ACTIVE RIDE
        // ====================================================

        AppScreen.ACTIVE_RIDE -> {

            ActiveRideScreen(
                activeRideViewModel = activeRideViewModel,
                routeStops = ruralRoute,
                onBackToHome = {
                    currentScreen = AppScreen.HOME
                }
            )
        }


        // ====================================================
        // MAP
        // ====================================================

        AppScreen.MAP -> {

            RuralTransportMap(
                journeyViewModel = journeyViewModel,
                passengerViewModel = passengerViewModel,
                onBack = {
                    currentScreen = AppScreen.HOME
                }
            )
        }
    }
}


// ============================================================
// HOME SCREEN
// ============================================================

@Composable
fun RuralTransportHome(

    onSelectStop: (TransportStop) -> Unit,

    onMap: () -> Unit,

    onDriver: () -> Unit,

    onProfile: () -> Unit = {}

) {

    LazyColumn(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {


        // ====================================================
        // HEADER
        // ====================================================

        item {

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RURAL TRANSPORT V2",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val userEmail = FirebaseAuth.getInstance().currentUser?.email
                    Text(
                        text = if (userEmail != null) "Logged in as $userEmail" else "Demand-aware shared auto information",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onProfile) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "My Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )
        }


        // ====================================================
        // DRIVER BUTTON
        // ====================================================

        item {

            Button(

                onClick =
                    onDriver,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    text =
                        "Driver / Auto Update"
                )
            }
        }


        // ====================================================
        // ROUTE CARD
        // ====================================================

        item {

            Card(

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Column(

                    modifier =
                        Modifier.padding(16.dp)

                ) {

                    Text(

                        text =
                            "Your Route",

                        style =
                            MaterialTheme.typography.titleLarge,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(

                        text =
                            "Gurramguda → Jay Suryapatnam → Sphoorthy College → Nadergul"
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Text(

                        text =
                            "Select the stop where you are waiting."
                    )
                }
            }
        }


        // ====================================================
        // STOP TITLE
        // ====================================================

        item {

            Text(

                text =
                    "Select Your Stop",

                style =
                    MaterialTheme.typography.titleLarge,

                fontWeight =
                    FontWeight.Bold
            )
        }


        // ====================================================
        // STOPS
        // ====================================================

        items(

            ruralRoute.drop(1)

        ) { stop ->

            StopCard(

                stop =
                    stop,

                onClick = {

                    onSelectStop(
                        stop
                    )
                }
            )
        }


        // ====================================================
        // MAP
        // ====================================================

        item {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            OutlinedButton(

                onClick =
                    onMap,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    text =
                        "View Transport Map"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(30.dp)
            )
        }
    }
}


// ============================================================
// STOP CARD
// ============================================================

@Composable
fun StopCard(

    stop: TransportStop,

    onClick: () -> Unit

) {

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        onClick =
            onClick

    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            verticalAlignment =
                Alignment.CenterVertically

        ) {

            Column(

                modifier =
                    Modifier.weight(1f)

            ) {

                Text(

                    text =
                        stop.name,

                    style =
                        MaterialTheme.typography.titleLarge,

                    fontWeight =
                        FontWeight.Bold
                )

                Text(

                    text =
                        "Route stop ${stop.sequence}"
                )
            }

            Text(

                text =
                    "→",

                style =
                    MaterialTheme.typography.headlineMedium
            )
        }
    }
}


// ============================================================
// DRIVER STATUS SCREEN
// ============================================================

@Composable
fun DriverStatusScreen(

    onBack: () -> Unit

) {

    val firebaseRepository =
        remember {

            FirebaseRepository()
        }


    var selectedAuto by remember {

        mutableStateOf("A01")
    }


    var selectedStop by remember {

        mutableStateOf(
            ruralRoute[1]
        )
    }


    var availableSeats by remember {

        mutableStateOf("2")
    }


    var selectedStatus by remember {

        mutableStateOf(
            "AVAILABLE"
        )
    }


    var selectedDirection by remember {
        mutableStateOf("FORWARD")
    }


    var message by remember {

        mutableStateOf<String?>(null)
    }


    var errorMessage by remember {

        mutableStateOf<String?>(null)
    }


    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(
                    rememberScrollState()
                ),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)

    ) {


        // ====================================================
        // TITLE
        // ====================================================

        Text(

            text =
                "Driver / Auto Status",

            style =
                MaterialTheme.typography.headlineMedium,

            fontWeight =
                FontWeight.Bold
        )


        Text(

            text =
                "Update your auto's current availability",

            style =
                MaterialTheme.typography.bodyLarge
        )


        // ====================================================
        // AUTO SELECTION
        // ====================================================

        Text(

            text =
                "Select Auto",

            style =
                MaterialTheme.typography.titleMedium,

            fontWeight =
                FontWeight.Bold
        )


        Row(

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            listOf(
                "A01",
                "A02"
            ).forEach { autoId ->

                if (
                    selectedAuto == autoId
                ) {

                    Button(

                        onClick = {

                            selectedAuto =
                                autoId
                        }

                    ) {

                        Text(autoId)
                    }

                } else {

                    OutlinedButton(

                        onClick = {

                            selectedAuto =
                                autoId
                        }

                    ) {

                        Text(autoId)
                    }
                }
            }
        }


        // ====================================================
        // CURRENT STOP
        // ====================================================

        Text(

            text =
                "Current Stop",

            style =
                MaterialTheme.typography.titleMedium,

            fontWeight =
                FontWeight.Bold
        )


        ruralRoute.forEach { stop ->

            if (
                selectedStop.id == stop.id
            ) {

                Button(

                    onClick = {

                        selectedStop =
                            stop
                    },

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(
                        text =
                            stop.name
                    )
                }

            } else {

                OutlinedButton(

                    onClick = {

                        selectedStop =
                            stop
                    },

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text(
                        text =
                            stop.name
                    )
                }
            }
        }


        // ====================================================
        // AVAILABLE SEATS
        // ====================================================

        Text(

            text =
                "Available Seats",

            style =
                MaterialTheme.typography.titleMedium,

            fontWeight =
                FontWeight.Bold
        )


        Row(

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            listOf(
                "0",
                "1",
                "2",
                "3",
                "4"
            ).forEach { seats ->

                if (
                    availableSeats == seats
                ) {

                    Button(

                        onClick = {

                            availableSeats =
                                seats
                        }

                    ) {

                        Text(seats)
                    }

                } else {

                    OutlinedButton(

                        onClick = {

                            availableSeats =
                                seats
                        }

                    ) {

                        Text(seats)
                    }
                }
            }
        }


        // ====================================================
        // STATUS
        // ====================================================

        Text(

            text =
                "Auto Status",

            style =
                MaterialTheme.typography.titleMedium,

            fontWeight =
                FontWeight.Bold
        )


        Row(

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            listOf(

                "AVAILABLE",
                "MOVING",
                "FULL",
                "OFFLINE"

            ).forEach { status ->

                if (
                    selectedStatus == status
                ) {

                    Button(

                        onClick = {

                            selectedStatus =
                                status
                        }

                    ) {

                        Text(status)
                    }

                } else {

                    OutlinedButton(

                        onClick = {

                            selectedStatus =
                                status
                        }

                    ) {

                        Text(status)
                    }
                }
            }
        }


        // ====================================================
        // DIRECTION
        // ====================================================

        Text(
            text = "Corridor Direction",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("FORWARD", "REVERSE").forEach { direction ->
                if (selectedDirection == direction) {
                    Button(onClick = { selectedDirection = direction }) {
                        Text(direction)
                    }
                } else {
                    OutlinedButton(onClick = { selectedDirection = direction }) {
                        Text(direction)
                    }
                }
            }
        }


        // ====================================================
        // FIREBASE UPDATE
        // ====================================================

        Button(

            onClick = {

                val seats =
                    availableSeats.toIntOrNull()

                if (seats == null) {

                    errorMessage =
                        "Please select available seats."

                    return@Button
                }


                firebaseRepository.updateAutoStatus(

                    autoId =
                        selectedAuto,

                    currentStop =
                        selectedStop.name,

                    availableSeats =
                        seats,

                    status =
                        selectedStatus,

                    direction =
                        selectedDirection,

                    onSuccess = {

                        message =
                            "Auto status updated successfully."

                        errorMessage =
                            null
                    },

                    onError = { error ->

                        errorMessage =
                            error

                        message =
                            null
                    }
                )
            },

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "Update Auto Status"
            )
        }


        // ====================================================
        // SUCCESS
        // ====================================================

        message?.let { text ->

            Text(

                text =
                    "✓ $text",

                fontWeight =
                    FontWeight.Bold
            )
        }


        // ====================================================
        // ERROR
        // ====================================================

        errorMessage?.let { error ->

            Text(

                text =
                    "Firebase error: $error",

                color =
                    MaterialTheme.colorScheme.error,

                fontWeight =
                    FontWeight.Bold
            )
        }


        // ====================================================
        // BACK
        // ====================================================

        OutlinedButton(

            onClick =
                onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "Back"
            )
        }
    }
}


// ============================================================
// PASSENGER DEMAND SCREEN
// ============================================================

@Composable
fun PassengerDemandScreen(

    selectedStop: TransportStop?,

    hasReportedWaiting: Boolean,

    onReportWaiting: () -> Unit,

    firebaseError: String?,

    onViewAvailability: () -> Unit,

    onBack: () -> Unit

) {

    val stopName =
        selectedStop?.name
            ?: "Unknown Stop"


    val firebaseRepository =
        remember {

            FirebaseRepository()
        }


    var realtimeWaitingPassengers by remember {

        mutableStateOf(0)
    }


    val demand =
        sampleDemand.find {

            it.stopName ==
                    stopName
        }


    val predictedDemand =
        demand?.predictedDemand
            ?: 0


    val demandLevel =
        demand?.demandLevel
            ?: "LOW"


    // ========================================================
    // REALTIME FIREBASE LISTENER
    // ========================================================

    DisposableEffect(
        selectedStop?.id
    ) {

        val stopId =
            selectedStop?.id


        if (stopId == null) {

            onDispose { }

        } else {

            val listener =
                firebaseRepository.observePassengerDemand(

                    stopId =
                        stopId,

                    onDemandChanged = { count ->

                        realtimeWaitingPassengers =
                            count
                    },

                    onError = {

                        // Error handled separately
                    }
                )


            onDispose {

                firebaseRepository
                    .removePassengerDemandListener(
                        listener
                    )
            }
        }
    }


    // ========================================================
    // SCREEN
    // ========================================================

    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(
                    rememberScrollState()
                ),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)

    ) {


        Text(

            text =
                "Passenger Demand",

            style =
                MaterialTheme.typography.headlineMedium,

            fontWeight =
                FontWeight.Bold
        )


        Text(

            text =
                stopName,

            style =
                MaterialTheme.typography.titleLarge
        )


        // ====================================================
        // DEMAND CARD
        // ====================================================

        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Column(

                modifier =
                    Modifier.padding(20.dp)

            ) {

                Text(

                    text =
                        "Current passengers waiting",

                    style =
                        MaterialTheme.typography.titleMedium
                )


                Text(

                    text =
                        realtimeWaitingPassengers.toString(),

                    style =
                        MaterialTheme.typography.displaySmall,

                    fontWeight =
                        FontWeight.Bold
                )


                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                Text(

                    text =
                        "Predicted demand: $predictedDemand passengers"
                )


                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                AssistChip(

                    onClick = {},

                    label = {

                        Text(

                            text =
                                "Demand: $demandLevel"
                        )
                    }
                )
            }
        }


        // ====================================================
        // FIREBASE ERROR
        // ====================================================

        firebaseError?.let { error ->

            Text(

                text =
                    "Firebase error: $error",

                color =
                    MaterialTheme.colorScheme.error,

                fontWeight =
                    FontWeight.Bold
            )
        }


        // ====================================================
        // WAITING BUTTON
        // ====================================================

        if (!hasReportedWaiting) {

            Button(

                onClick =
                    onReportWaiting,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    text =
                        "I'm Waiting Here"
                )
            }

        } else {

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors()

            ) {

                Column(

                    modifier =
                        Modifier.padding(16.dp)

                ) {

                    Text(

                        text =
                            "✓ Waiting request recorded",

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(

                        text =
                            "Your demand information can now be used by the transport system."
                    )
                }
            }
        }


        // ====================================================
        // AVAILABILITY
        // ====================================================

        Button(

            onClick =
                onViewAvailability,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "Check Auto Availability"
            )
        }


        // ====================================================
        // BACK
        // ====================================================

        OutlinedButton(

            onClick =
                onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "Back"
            )
        }
    }
}


// ============================================================
// AVAILABILITY SCREEN
// TEMPORARY PREDICTION
// ============================================================

@Composable
fun AvailabilityScreen(

    selectedStop: TransportStop?,

    onBack: () -> Unit,

    onMap: () -> Unit

) {

    val stopName =
        selectedStop?.name
            ?: "Unknown Stop"


    // ========================================================
    // TEMPORARY MOCK PREDICTION
    // ========================================================

    val prediction =
        AvailabilityPrediction(

            availabilityProbability =
                82,

            expectedWaitMinutes =
                8,

            estimatedSeats =
                2,

            confidence =
                "Medium"
        )


    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)

    ) {


        Text(

            text =
                "Transport Availability",

            style =
                MaterialTheme.typography.headlineMedium,

            fontWeight =
                FontWeight.Bold
        )


        Text(

            text =
                stopName,

            style =
                MaterialTheme.typography.titleLarge
        )


        // ====================================================
        // PROBABILITY
        // ====================================================

        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Column(

                modifier =
                    Modifier.padding(20.dp)

            ) {

                Text(

                    text =
                        "Availability Probability",

                    style =
                        MaterialTheme.typography.titleMedium
                )


                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )


                Text(

                    text =
                        "${prediction.availabilityProbability}%",

                    style =
                        MaterialTheme.typography.displaySmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }


        // ====================================================
        // WAIT TIME
        // ====================================================

        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Column(

                modifier =
                    Modifier.padding(20.dp)

            ) {

                Text(
                    text =
                        "Expected Waiting Time"
                )


                Text(

                    text =
                        "${prediction.expectedWaitMinutes} minutes",

                    style =
                        MaterialTheme.typography.headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }


        // ====================================================
        // SEATS
        // ====================================================

        Card(

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Column(

                modifier =
                    Modifier.padding(20.dp)

            ) {

                Text(
                    text =
                        "Estimated Available Seats"
                )


                Text(

                    text =
                        prediction.estimatedSeats.toString(),

                    style =
                        MaterialTheme.typography.headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }


        Text(

            text =
                "Prediction confidence: ${prediction.confidence}"
        )


        // ====================================================
        // MAP
        // ====================================================

        Button(

            onClick =
                onMap,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "View Map"
            )
        }


        // ====================================================
        // BACK
        // ====================================================

        OutlinedButton(

            onClick =
                onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "Back"
            )
        }
    }
}


// ============================================================
// MAP SCREEN
// ============================================================

@Composable
fun AnimatedDriverMarker(
    driver: LiveDriverUiModel,
    passengerPickupStop: String,
    direction: RouteDirection,
    onMarkerClick: (LiveDriverUiModel) -> Unit
) {
    val animLat by animateFloatAsState(
        targetValue = driver.position.latitude.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "driverLat"
    )
    val animLng by animateFloatAsState(
        targetValue = driver.position.longitude.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "driverLng"
    )
    val animRotation by animateFloatAsState(
        targetValue = driver.heading,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "driverHeading"
    )

    val currentPosition = LatLng(animLat.toDouble(), animLng.toDouble())
    val markerState = rememberMarkerState(position = currentPosition)
    LaunchedEffect(animLat, animLng) {
        markerState.position = currentPosition
    }

    val stopsText = if (driver.isAtPassengerStop) {
        "At your pickup stop (${driver.currentStop})"
    } else {
        "${driver.stopsAway} stop${if (driver.stopsAway > 1) "s" else ""} away • At ${driver.currentStop}"
    }

    Marker(
        state = markerState,
        title = driver.vehicleNumber.ifBlank { "Auto #${driver.driverId.takeLast(4).uppercase()}" },
        snippet = "$stopsText • ${driver.availableSeats} seats",
        rotation = animRotation,
        onClick = {
            onMarkerClick(driver)
            false
        }
    )
}

@Composable
fun RuralTransportMap(
    journeyViewModel: JourneyViewModel = viewModel(),
    passengerViewModel: PassengerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val journeyState by journeyViewModel.uiState.collectAsState()
    val waitingState by passengerViewModel.waitingState.collectAsState()
    val liveDrivers by passengerViewModel.liveDrivers.collectAsState()

    val route = journeyState.selectedRoute ?: RouteRepository().defaultRoute
    val pickupStop = journeyState.pickupStop?.name ?: RouteData.stops.first()
    val destStop = journeyState.destinationStop?.name ?: RouteData.stops.last()
    val direction = RouteData.getDirection(pickupStop, destStop)

    var selectedDriver by remember { mutableStateOf<LiveDriverUiModel?>(null) }

    // Start live listener for corridor drivers matching route, direction, and pickup stop
    LaunchedEffect(route.id, direction, pickupStop) {
        passengerViewModel.observeDriversOnCorridor(
            routeId = route.id,
            direction = direction,
            pickupStop = pickupStop
        )
    }

    // ========================================================
    // LOCATION PERMISSION
    // ========================================================
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var currentLocation by remember {
        mutableStateOf<LatLng?>(null)
    }

    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                }
            }
        } catch (_: SecurityException) {}
    }

    val pickupTransportStop = RouteData.canonicalStops.find { it.name.equals(pickupStop, true) }
        ?: RouteData.canonicalStops.first()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(pickupTransportStop.latitude, pickupTransportStop.longitude),
            13.5f
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // CORRIDOR ROUTE POLYLINE
            Polyline(
                points = RouteData.canonicalStops.map { LatLng(it.latitude, it.longitude) },
                color = Color(0xFF1976D2),
                width = 10f
            )

            // CORRIDOR STOPS MARKERS
            RouteData.canonicalStops.forEach { stop ->
                val isPickup = stop.name.equals(pickupStop, true)
                val isDest = stop.name.equals(destStop, true)

                val markerTitle = when {
                    isPickup -> "Pickup: ${stop.name}"
                    isDest -> "Destination: ${stop.name}"
                    else -> stop.name
                }
                val markerSnippet = when {
                    isPickup -> "Your boarding stop (${stop.sequence}/4)"
                    isDest -> "Your destination (${stop.sequence}/4)"
                    else -> "Corridor Stop ${stop.sequence}"
                }

                Marker(
                    state = rememberMarkerState(position = LatLng(stop.latitude, stop.longitude)),
                    title = markerTitle,
                    snippet = markerSnippet
                )
            }

            // USER LOCATION MARKER
            currentLocation?.let { loc ->
                Marker(
                    state = rememberMarkerState(position = loc),
                    title = "Your Location",
                    snippet = "GPS Position"
                )
            }

            // LIVE ANIMATED DRIVER VEHICLE MARKERS
            liveDrivers.forEach { driver ->
                key(driver.driverId) {
                    AnimatedDriverMarker(
                        driver = driver,
                        passengerPickupStop = pickupStop,
                        direction = direction,
                        onMarkerClick = { selectedDriver = it }
                    )
                }
            }
        }

        // ====================================================
        // TOP OVERLAY: NAVIGATION & CORRIDOR DIRECTION BANNER
        // ====================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.size(width = 10.dp, height = 0.dp))

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = route.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$pickupStop → $destStop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = direction.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // DRIVER COUNT STATUS BANNER
            if (liveDrivers.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.size(width = 10.dp, height = 0.dp))
                        Column {
                            Text(
                                text = "0 autos currently en route",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "No online autos travelling in your direction ($direction) at or before $pickupStop. Vehicles will appear live once active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.size(width = 10.dp, height = 0.dp))
                        Column {
                            Text(
                                text = "${liveDrivers.size} auto${if (liveDrivers.size > 1) "s" else ""} en route to $pickupStop",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val nearest = liveDrivers.first()
                            val distText = if (nearest.isAtPassengerStop) {
                                "Nearest auto is at your stop (${nearest.currentStop})"
                            } else {
                                "Nearest is ${nearest.stopsAway} stop(s) away at ${nearest.currentStop}"
                            }
                            Text(
                                text = distText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // ====================================================
        // BOTTOM OVERLAY: DRIVER DETAILS & WAITING ACTIONS
        // ====================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selected Driver Card
            selectedDriver?.let { driver ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = driver.vehicleNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedDriver = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Text(
                            text = "Driver: ${driver.name} • ${driver.phone.ifBlank { "Corridor Operator" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Current stop: ${driver.currentStop}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (driver.isAtPassengerStop) "At your stop" else "${driver.stopsAway} stops away",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${driver.availableSeats} seats",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Waiting State Bar
            if (waitingState.isWaiting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Waiting at ${waitingState.pickupStop}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${waitingState.waitingPassengersCount} passenger(s) waiting in queue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { passengerViewModel.stopWaiting() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel Wait", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        passengerViewModel.startWaiting(pickupStop, destStop, route.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.size(width = 8.dp, height = 0.dp))
                    Text("I'm Waiting at $pickupStop", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}