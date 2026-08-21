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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.DisposableEffect
// ============================================================
// DATA MODELS
// ============================================================

data class TransportStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val sequence: Int
)

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
// SAMPLE ROUTE
// TEMPORARY DATA ONLY
// ============================================================

val ruralRoute = listOf(

    TransportStop(
        id = "STOP_01",
        name = "IBP",
        latitude = 16.5062,
        longitude = 80.6480,
        sequence = 1
    ),

    TransportStop(
        id = "STOP_02",
        name = "Gurramguda",
        latitude = 16.5000,
        longitude = 80.6550,
        sequence = 2
    ),

    TransportStop(
        id = "STOP_03",
        name = "Champapet",
        latitude = 16.4950,
        longitude = 80.6620,
        sequence = 3
    ),

    TransportStop(
        id = "STOP_04",
        name = "Issdan",
        latitude = 16.4900,
        longitude = 80.6700,
        sequence = 4
    )
)

// ============================================================
// SAMPLE AUTO DATA
// TEMPORARY DATA ONLY
// ============================================================

val sampleAutos = listOf(

    Auto(
        id = "A01",
        currentStop = "IBP",
        availableSeats = 4,
        status = "Available",
        lastUpdated = "1 min ago"
    ),

    Auto(
        id = "A02",
        currentStop = "Gurramguda",
        availableSeats = 2,
        status = "Moving",
        lastUpdated = "2 min ago"
    )
)

// ============================================================
// SAMPLE DEMAND
// TEMPORARY DATA ONLY
// ============================================================

val sampleDemand = listOf(

    PassengerDemand(
        stopName = "Gurramguda",
        waitingPassengers = 4,
        predictedDemand = 5,
        demandLevel = "HIGH"
    ),

    PassengerDemand(
        stopName = "Champapet",
        waitingPassengers = 2,
        predictedDemand = 3,
        demandLevel = "MEDIUM"
    ),

    PassengerDemand(
        stopName = "Issdan",
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

                RuralTransportApp()
            }
        }
    }
}

// ============================================================
// APP NAVIGATION
// ============================================================

enum class AppScreen {
    HOME,
    DEMAND,
    AVAILABILITY,
    MAP
}

// ============================================================
// MAIN APP
// ============================================================

@Composable
fun RuralTransportApp() {

    var currentScreen by remember {
        mutableStateOf(AppScreen.HOME)
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

        AppScreen.HOME -> {

            RuralTransportHome(

                onSelectStop = {

                    selectedStop = it
                    currentScreen = AppScreen.DEMAND
                },

                onMap = {

                    currentScreen = AppScreen.MAP
                }
            )
        }

        AppScreen.DEMAND -> {

            PassengerDemandScreen(

                selectedStop = selectedStop,

                hasReportedWaiting = hasReportedWaiting,

                onReportWaiting = {

                    selectedStop?.let { stop ->

                        firebaseRepository.submitPassengerDemand(

                            stopId = stop.id,

                            stopName = stop.name,

                            onSuccess = {

                                hasReportedWaiting = true
                                firebaseError = null
                            },

                            onError = { error ->

                                firebaseError = error
                            }
                        )
                    }
                },

                firebaseError = firebaseError,

                onViewAvailability = {

                    currentScreen = AppScreen.AVAILABILITY
                },

                onBack = {

                    currentScreen = AppScreen.HOME
                }
            )
        }
        AppScreen.AVAILABILITY -> {

            AvailabilityScreen(

                selectedStop = selectedStop,

                onBack = {

                    currentScreen = AppScreen.DEMAND
                },

                onMap = {

                    currentScreen = AppScreen.MAP
                }
            )
        }

        AppScreen.MAP -> {

            RuralTransportMap(

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
    onMap: () -> Unit
) {

    LazyColumn(

        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "RURAL TRANSPORT V2",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Demand-aware shared auto information",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Your Route",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "IBP → Gurramguda → Champapet → Issdan"
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = "Select the stop where you are waiting."
                    )
                }
            }
        }

        item {

            Text(
                text = "Select Your Stop",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(
            ruralRoute.drop(1)
        ) { stop ->

            StopCard(

                stop = stop,

                onClick = {

                    onSelectStop(stop)
                }
            )
        }

        item {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedButton(
                onClick = onMap,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "View Transport Map"
                )
            }

            Spacer(
                modifier = Modifier.height(30.dp)
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
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = stop.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Route stop ${stop.sequence}"
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.headlineMedium
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
        selectedStop?.name ?: "Unknown Stop"


    val firebaseRepository = remember {
        FirebaseRepository()
    }

    var realtimeWaitingPassengers by remember {
        mutableStateOf(0)
    }

    val demand =
        sampleDemand.find {
            it.stopName == stopName
        }

    val currentDemand =
        realtimeWaitingPassengers

    val predictedDemand =
        demand?.predictedDemand ?: 0

    val demandLevel =
        demand?.demandLevel ?: "LOW"
    DisposableEffect(selectedStop?.id) {

        val stopId = selectedStop?.id

        if (stopId == null) {
            onDispose { }
        } else {

            val listener =
                firebaseRepository.observePassengerDemand(

                    stopId = stopId,

                    onDemandChanged = { count ->

                        realtimeWaitingPassengers = count
                    },

                    onError = { error ->

                        // Error will be handled later
                    }
                )

            onDispose {

                firebaseRepository.removePassengerDemandListener(
                    listener
                )
            }
        }
    }
    Column(

        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Passenger Demand",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stopName,
            style = MaterialTheme.typography.titleLarge
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Current passengers waiting",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = currentDemand.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Predicted demand: $predictedDemand passengers"
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "Demand: $demandLevel"
                        )
                    }
                )
            }
        }
        firebaseError?.let { error ->

            Text(
                text = "Firebase error: $error",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
        if (!hasReportedWaiting) {

            Button(
                onClick = onReportWaiting,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "I'm Waiting Here"
                )
            }

        } else {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "✓ Waiting request recorded",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "Your demand information can now be used by the transport system."
                    )
                }
            }
        }

        Button(
            onClick = onViewAvailability,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = "Check Auto Availability"
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = "Back"
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
        selectedStop?.name ?: "Unknown Stop"

    // --------------------------------------------------------
    // TEMPORARY MOCK PREDICTION
    //
    // This will later come from the ML model through FastAPI.
    // --------------------------------------------------------

    val prediction = AvailabilityPrediction(

        availabilityProbability = 82,

        expectedWaitMinutes = 8,

        estimatedSeats = 2,

        confidence = "Medium"
    )

    Column(

        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Transport Availability",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stopName,
            style = MaterialTheme.typography.titleLarge
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Availability Probability",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "${prediction.availabilityProbability}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Expected Waiting Time"
                )

                Text(
                    text = "${prediction.expectedWaitMinutes} minutes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Estimated Available Seats"
                )

                Text(
                    text = prediction.estimatedSeats.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Prediction confidence: ${prediction.confidence}"
        )

        Button(
            onClick = onMap,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = "View Map"
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = "Back"
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

    val context = LocalContext.current

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

    val permissionLauncher =
        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts.RequestMultiplePermissions()

        ) { permissions ->

            hasLocationPermission =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true ||

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

            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),

            verticalArrangement = Arrangement.Center,

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Location permission is required",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(
                modifier = Modifier.height(16.dp)
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
                    text = "Allow Location"
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedButton(
                onClick = onBack
            ) {

                Text(
                    text = "Back"
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
                .getFusedLocationProviderClient(context)
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
        currentLocation ?: defaultLocation

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

    LaunchedEffect(currentLocation) {

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
        modifier = Modifier.fillMaxSize()
    ) {

        GoogleMap(

            modifier = Modifier.fillMaxSize(),

            cameraPositionState =
                cameraPositionState
        ) {

            // ------------------------------------------------
            // ROUTE LINE
            // ------------------------------------------------

            Polyline(

                points =
                    ruralRoute.map {

                        LatLng(
                            it.latitude,
                            it.longitude
                        )
                    },

                width = 8f
            )

            // ------------------------------------------------
            // STOPS
            // ------------------------------------------------

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

            // ------------------------------------------------
            // CURRENT USER
            // ------------------------------------------------

            Marker(

                state =
                    rememberMarkerState(
                        position = mapLocation
                    ),

                title =
                    if (currentLocation != null) {

                        "Your Location"

                    } else {

                        "IBP"
                    }
            )

            // ------------------------------------------------
            // AUTO LOCATIONS
            // TEMPORARY SIMULATION
            // ------------------------------------------------

            Marker(

                state =
                    rememberMarkerState(

                        position =
                            LatLng(
                                ruralRoute[0].latitude,
                                ruralRoute[0].longitude
                            )
                    ),

                title = "Auto A01",

                snippet =
                    "4 seats • At IBP"
            )

            Marker(

                state =
                    rememberMarkerState(

                        position =
                            LatLng(
                                ruralRoute[1].latitude,
                                ruralRoute[1].longitude
                            )
                    ),

                title = "Auto A02",

                snippet =
                    "2 seats • Gurramguda"
            )
        }

        // ====================================================
        // BACK BUTTON
        // ====================================================

        Button(

            onClick = onBack,

            modifier =
                Modifier
                    .padding(16.dp)
        ) {

            Text(
                text = "Back"
            )
        }
    }
}