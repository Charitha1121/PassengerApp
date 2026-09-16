package com.example.ruraltransport.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ruraltransport.data.model.DriverLocationUpdate
import com.example.ruraltransport.data.model.RideRequest
import com.example.ruraltransport.data.model.TransportStop
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import com.example.ruraltransport.ui.notifications.NotificationHelper
import com.example.ruraltransport.ui.map.LiveTrackingViewModel
import com.example.ruraltransport.ui.map.AnimatedAutoMarker
import com.example.ruraltransport.ui.map.bitmapDescriptorFromVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRideScreen(
    activeRideViewModel: ActiveRideViewModel,
    liveTrackingViewModel: LiveTrackingViewModel = viewModel(),
    routeStops: List<TransportStop>,
    onBackToHome: () -> Unit
) {
    val rideState by activeRideViewModel.rideState.collectAsState()
    val activeDrivers by liveTrackingViewModel.activeDrivers.collectAsState()
    val context = LocalContext.current
    val notificationHelper = remember { NotificationHelper(context) }

    // Ensure Google Maps is initialized
    LaunchedEffect(Unit) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context)
        } catch (e: Exception) {
            android.util.Log.e("ActiveRideMap", "MapsInitializer failed: ${e.message}")
        }
    }

    // Observe all corridor drivers continuously
    LaunchedEffect(Unit) {
        liveTrackingViewModel.observeCorridorDrivers()
    }

    LaunchedEffect(rideState) {
        when (val s = rideState) {
            is RideUiState.DriverAssigned -> {
                notificationHelper.showRideNotification(
                    title = "Driver Assigned!",
                    message = "${s.ride.driverName ?: "Your auto"} is heading to ${s.ride.pickupStopName}."
                )
            }
            is RideUiState.Completed -> {
                notificationHelper.showRideNotification(
                    title = "Ride Completed",
                    message = "You arrived at ${s.ride.destinationStopName}."
                )
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Ride Tracking", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = rideState) {
                is RideUiState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No active ride.", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackToHome) {
                            Text("Back to Home")
                        }
                    }
                }

                is RideUiState.Requesting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sending ride request to rural corridor...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is RideUiState.Searching -> {
                    SearchingView(
                        requestId = state.requestId,
                        onCancel = { activeRideViewModel.cancelActiveRide() }
                    )
                }

                is RideUiState.DriverAssigned -> {
                    ActiveRideLiveView(
                        ride = state.ride,
                        assignedDriverLocation = state.driverLocation,
                        activeDrivers = activeDrivers,
                        statusTitle = "Driver Assigned • On The Way",
                        statusColor = Color(0xFF2E7D32),
                        routeStops = routeStops,
                        onCancel = { activeRideViewModel.cancelActiveRide() }
                    )
                }

                is RideUiState.InProgress -> {
                    ActiveRideLiveView(
                        ride = state.ride,
                        assignedDriverLocation = state.driverLocation,
                        activeDrivers = activeDrivers,
                        statusTitle = "Ride In Progress • En Route",
                        statusColor = MaterialTheme.colorScheme.primary,
                        routeStops = routeStops,
                        onCancel = null
                    )
                }

                is RideUiState.Completed -> {
                    CompletedView(
                        ride = state.ride,
                        onDismiss = {
                            activeRideViewModel.resetState()
                            onBackToHome()
                        }
                    )
                }

                is RideUiState.Cancelled -> {
                    CancelledView(
                        reason = state.message,
                        onDismiss = {
                            activeRideViewModel.resetState()
                            onBackToHome()
                        }
                    )
                }

                is RideUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Request Notice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            activeRideViewModel.resetState()
                            onBackToHome()
                        }) {
                            Text("Back to Home")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchingView(
    requestId: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Searching for Nearby Autos...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Broadcasting to active approved drivers along the Gurramguda — Nadergul corridor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Request")
        }
    }
}

@Composable
private fun ActiveRideLiveView(
    ride: RideRequest,
    assignedDriverLocation: DriverLocationUpdate?,
    activeDrivers: List<LiveDriverPosition>,
    statusTitle: String,
    statusColor: Color,
    routeStops: List<TransportStop>,
    onCancel: (() -> Unit)?
) {
    val context = LocalContext.current
    val autoIconDescriptor = remember(context) {
        bitmapDescriptorFromVector(context, R.drawable.ic_auto_rickshaw, 44)
    }

    val firstStop = routeStops.firstOrNull()
    val initialPos = if (firstStop != null && firstStop.latitude.isFinite() && firstStop.longitude.isFinite()) {
        LatLng(firstStop.latitude, firstStop.longitude)
    } else {
        LatLng(17.2820, 78.5538)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 14f)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor)
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    Icons.Default.NearMe,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Stale Location Notice if needed
        if (assignedDriverLocation != null && assignedDriverLocation.isStale) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3E0))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Driver GPS signal paused (${assignedDriverLocation.ageSeconds}s ago)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }

        // Google Map with Live Driver
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // Route Polyline
                Polyline(
                    points = routeStops
                        .filter { it.latitude.isFinite() && it.longitude.isFinite() }
                        .map { LatLng(it.latitude, it.longitude) },
                    width = 8f,
                    color = MaterialTheme.colorScheme.primary
                )

                // Stops Markers
                routeStops.forEach { stop ->
                    if (stop.latitude.isFinite() && stop.longitude.isFinite()) {
                        Marker(
                            state = rememberMarkerState(
                                key = "stop_${stop.id}",
                                position = LatLng(stop.latitude, stop.longitude)
                            ),
                            title = stop.name,
                            snippet = "Stop ${stop.sequence}"
                        )
                    }
                }

                // All Real-Time Animated Driver Markers on the corridor (excluding assigned driver)
                activeDrivers.forEach { driver ->
                    if (driver.driverUid != ride.driverId && driver.lat.isFinite() && driver.lng.isFinite() && (driver.lat != 0.0 || driver.lng != 0.0)) {
                        key(driver.driverUid) {
                            AnimatedAutoMarker(
                                driver = driver,
                                autoIcon = autoIconDescriptor
                            )
                        }
                    }
                }

                // Assigned Driver Marker (High-precision tracking)
                assignedDriverLocation?.let { loc ->
                    if (loc.latitude.isFinite() && loc.longitude.isFinite() && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                        val assignedDriverPos = LiveDriverPosition(
                            driverUid = ride.driverId ?: "assigned",
                            lat = loc.latitude,
                            lng = loc.longitude,
                            heading = loc.bearing,
                            speed = loc.speed,
                            isRideActive = true,
                            isAvailable = false,
                            lastUpdated = loc.timestamp,
                            driverName = ride.driverName ?: "Your Auto",
                            vehicleNumber = ride.vehicleNumber ?: "Auto"
                        )
                        key("assigned_driver") {
                            AnimatedAutoMarker(
                                driver = assignedDriverPos,
                                autoIcon = autoIconDescriptor
                            )
                        }
                    }
                }
            }
        }

        // Driver & Trip Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = ride.driverName ?: "Corridor Auto",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Vehicle: ${ride.vehicleNumber ?: "Auto"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ride.driverPhone?.let { phone ->
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Call, contentDescription = "Call Driver", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Text(
                    text = "${ride.pickupStopName} → ${ride.destinationStopName} • ${ride.requestedSeats} seat(s)",
                    style = MaterialTheme.typography.bodyMedium
                )

                onCancel?.let { cancelAction ->
                    OutlinedButton(
                        onClick = cancelAction,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Ride")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedView(
    ride: RideRequest,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ride Completed!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You arrived at ${ride.destinationStopName}.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun CancelledView(
    reason: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ride Cancelled",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onDismiss) {
            Text("Back to Home")
        }
    }
}
