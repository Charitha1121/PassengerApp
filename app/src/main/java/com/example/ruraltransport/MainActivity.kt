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


import com.example.ruraltransport.data.model.TransportStop
import com.example.ruraltransport.ui.home.HomeScreen
import com.example.ruraltransport.ui.home.JourneyViewModel
import com.example.ruraltransport.ui.forecast.ForecastScreen
import com.example.ruraltransport.ui.forecast.ForecastViewModel
import com.example.ruraltransport.ui.ride.ActiveRideScreen
import com.example.ruraltransport.ui.ride.ActiveRideViewModel
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

                AppNavigation(
                    homeContent = { onNavigateToProfile, onNavigateToMap ->
                        RuralTransportApp(
                            onNavigateToProfile = onNavigateToProfile,
                            onOpenMap = onNavigateToMap
                        )
                    },
                    mapContent = { onBack ->
                        RuralTransportMap(onBack = onBack)
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
    activeRideViewModel: ActiveRideViewModel = viewModel()
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
                    selectedStop = stop
                    hasReportedWaiting = false
                    firebaseError = null
                    currentScreen = AppScreen.DEMAND
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
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                onWaitingHere = { stopId, stopName ->
                    val stop = ruralRoute.find { it.id == stopId } ?: ruralRoute.first()
                    selectedStop = stop
                    hasReportedWaiting = false
                    firebaseError = null
                    currentScreen = AppScreen.DEMAND
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

                onBack = {

                    currentScreen =
                        AppScreen.HOME
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
fun RuralTransportMap(

    onBack: () -> Unit

) {

    val context =
        LocalContext.current


    // ========================================================
    // LOCATION PERMISSION
    // ========================================================

    var hasLocationPermission by remember {

        mutableStateOf(

            ContextCompat.checkSelfPermission(

                context,

                Manifest.permission.ACCESS_FINE_LOCATION

            ) == PackageManager.PERMISSION_GRANTED

                    ||

                    ContextCompat.checkSelfPermission(

                        context,

                        Manifest.permission.ACCESS_COARSE_LOCATION

                    ) == PackageManager.PERMISSION_GRANTED
        )
    }


    val permissionLauncher =
        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()

        ) { permissions ->

            hasLocationPermission =

                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

                        ||

                        permissions[
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ] == true
        }


    // ========================================================
    // REQUEST LOCATION
    // ========================================================

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


    // ========================================================
    // PERMISSION SCREEN
    // ========================================================

    if (!hasLocationPermission) {

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),

            verticalArrangement =
                Arrangement.Center,

            horizontalAlignment =
                Alignment.CenterHorizontally

        ) {

            Text(

                text =
                    "Location permission is required",

                style =
                    MaterialTheme.typography.titleLarge
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            Button(

                onClick = {

                    permissionLauncher.launch(

                        arrayOf(

                            Manifest.permission.ACCESS_FINE_LOCATION,

                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

            ) {

                Text(
                    text =
                        "Allow Location"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            OutlinedButton(

                onClick =
                    onBack

            ) {

                Text(
                    text =
                        "Back"
                )
            }
        }

        return
    }


    // ========================================================
    // LOCATION CLIENT
    // ========================================================

    val fusedLocationClient =
        remember {

            LocationServices
                .getFusedLocationProviderClient(
                    context
                )
        }


    var currentLocation by remember {

        mutableStateOf<LatLng?>(null)
    }


    // ========================================================
    // GET LOCATION
    // ========================================================

    LaunchedEffect(Unit) {

        fusedLocationClient.lastLocation

            .addOnSuccessListener { location ->

                if (location != null) {

                    currentLocation =

                        LatLng(

                            location.latitude,

                            location.longitude
                        )
                }
            }
    }


    // ========================================================
    // MAP LOCATION
    // ========================================================

    val defaultLocation =
        LatLng(

            ruralRoute.first().latitude,

            ruralRoute.first().longitude
        )


    val mapLocation =
        currentLocation
            ?: defaultLocation


    // ========================================================
    // CAMERA
    // ========================================================

    val cameraPositionState =
        rememberCameraPositionState {

            position =

                CameraPosition.fromLatLngZoom(

                    mapLocation,

                    14f
                )
        }


    LaunchedEffect(
        currentLocation
    ) {

        currentLocation?.let { location ->

            cameraPositionState.position =

                CameraPosition.fromLatLngZoom(

                    location,

                    14f
                )
        }
    }


    // ========================================================
    // MAP
    // ========================================================

    Box(

        modifier =
            Modifier.fillMaxSize()

    ) {

        GoogleMap(

            modifier =
                Modifier.fillMaxSize(),

            cameraPositionState =
                cameraPositionState

        ) {


            // =================================================
            // ROUTE
            // =================================================

            Polyline(

                points =
                    ruralRoute.map {

                        LatLng(

                            it.latitude,

                            it.longitude
                        )
                    },

                width =
                    8f
            )


            // =================================================
            // STOPS
            // =================================================

            ruralRoute.forEach { stop ->

                Marker(

                    state =

                        rememberMarkerState(

                            position =

                                LatLng(

                                    stop.latitude,

                                    stop.longitude
                                )
                        ),

                    title =
                        stop.name,

                    snippet =
                        "Route stop ${stop.sequence}"
                )
            }


            // =================================================
            // USER LOCATION
            // =================================================

            Marker(

                state =

                    rememberMarkerState(

                        position =
                            mapLocation
                    ),

                title =

                    if (
                        currentLocation != null
                    ) {

                        "Your Location"

                    } else {

                        "Gurramguda"
                    }
            )


            // =================================================
            // TEMPORARY AUTO A01
            // =================================================

            Marker(

                state =

                    rememberMarkerState(

                        position =

                            LatLng(

                                ruralRoute[0].latitude,

                                ruralRoute[0].longitude
                            )
                    ),

                title =
                    "Auto A01",

                snippet =
                    "4 seats • At Gurramguda"
            )


            // =================================================
            // TEMPORARY AUTO A02
            // =================================================

            Marker(

                state =

                    rememberMarkerState(

                        position =

                            LatLng(

                                ruralRoute[1].latitude,

                                ruralRoute[1].longitude
                            )
                    ),

                title =
                    "Auto A02",

                snippet =
                    "2 seats • Jay Suryapatnam"
            )
        }


        // ====================================================
        // BACK BUTTON
        // ====================================================

        Button(

            onClick =
                onBack,

            modifier =

                Modifier
                    .padding(16.dp)

        ) {

            Text(
                text =
                    "Back"
            )
        }
    }
}