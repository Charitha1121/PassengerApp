package com.example.ruraltransport.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ruraltransport.R
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.ui.home.JourneyViewModel
import com.example.ruraltransport.ui.home.PassengerViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule

/**
 * Live Corridor Tracking Map Screen:
 * - Browse Mode (Pre-booking): Displays all operating autos on the corridor road using `drivers/{uid}/liveLocation`.
 * - Tap Marker: Displays driver details, available seats, distance, and straight-line ETA (20 km/h) to passenger pickup.
 * - Accepted Ride Mode: Switches to tracking the assigned driver via `drivers/{driverId}/liveTracking`.
 */
@Composable
fun LiveTrackingMapScreen(
    onBack: () -> Unit,
    journeyViewModel: JourneyViewModel = viewModel(),
    passengerViewModel: PassengerViewModel = viewModel(),
    liveTrackingViewModel: LiveTrackingViewModel = viewModel()
) {
    val context = LocalContext.current
    val journeyState by journeyViewModel.uiState.collectAsState()
    val activeDrivers by liveTrackingViewModel.activeDrivers.collectAsState()
    val selectedDriver by liveTrackingViewModel.selectedDriver.collectAsState()

    // Ensure Google Maps is initialized early to prevent BitmapDescriptorFactory crashes
    remember {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context)
        } catch (e: Exception) {
            android.util.Log.e("LiveTrackingMap", "MapsInitializer failed: ${e.message}")
        }
        true
    }


    val trackingRoute by liveTrackingViewModel.trackingRoute.collectAsState()
    val trackingDirection by liveTrackingViewModel.trackingDirection.collectAsState()

    val currentRoute = trackingRoute ?: journeyState.selectedRoute ?: com.example.ruraltransport.data.repository.RouteRepository().defaultRoute
    val pickupStop = journeyState.pickupStop ?: currentRoute.stops.firstOrNull() ?: RouteData.canonicalStops.first()
    val destStop = journeyState.destinationStop ?: currentRoute.stops.lastOrNull() ?: RouteData.canonicalStops.last()
    val pickupStopName = pickupStop.name
    val destStopName = destStop.name

    val direction = trackingDirection ?: journeyState.selectedDirection ?: RouteData.getDirection(pickupStopName, destStopName)

    val pickupLatLng = remember(pickupStop) {
        val lat = pickupStop.latitude
        val lng = pickupStop.longitude
        if (lat.isFinite() && lng.isFinite() && lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0 && (lat != 0.0 || lng != 0.0)) {
            LatLng(lat, lng)
        } else {
            LatLng(17.2942, 78.5675)
        }
    }

    // STEP 4: Ungate map from ride acceptance. Always observe all operating autos on corridor via liveLocation.
    LaunchedEffect(currentRoute.id, direction, pickupLatLng) {
        liveTrackingViewModel.observeCorridorDrivers(
            route = currentRoute,
            routeId = currentRoute.id,
            direction = direction,
            pickupLatLng = pickupLatLng
        )
    }

    // Cache the custom auto-rickshaw icon - handle potential null if SDK not ready
    val autoIconDescriptor by androidx.compose.runtime.produceState<BitmapDescriptor?>(initialValue = null) {
        value = try {
            bitmapDescriptorFromVector(context, R.drawable.ic_auto_rickshaw, 64)
        } catch (e: Exception) {
            null
        }
    }

    // ========================================================
    // USER GPS LOCATION PERMISSION & RESOLUTION
    // ========================================================
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = LatLng(location.latitude, location.longitude)
                    }
                }
            } catch (_: SecurityException) {}
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        val lat = pickupLatLng.latitude
        val lng = pickupLatLng.longitude
        val validTarget = if (lat.isFinite() && lng.isFinite() && (lat != 0.0 || lng != 0.0)) {
            LatLng(lat, lng)
        } else {
            LatLng(17.2942, 78.5675) // Default fallback
        }
        
        position = CameraPosition.fromLatLngZoom(validTarget, 14.2f)
    }

    LaunchedEffect(currentRoute.id, pickupLatLng) {
        if (pickupLatLng.latitude != 0.0 || pickupLatLng.longitude != 0.0) {
            try {
                val update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    pickupLatLng,
                    14.2f
                )
                cameraPositionState.animate(update)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(selectedDriver?.driverUid) {
        selectedDriver?.let { driver ->
            if (driver.lat.isFinite() && driver.lng.isFinite() && 
                driver.lat >= -90.0 && driver.lat <= 90.0 && 
                driver.lng >= -180.0 && driver.lng <= 180.0 &&
                (driver.lat != 0.0 || driver.lng != 0.0)
            ) {
                try {
                    val update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                        LatLng(driver.lat, driver.lng),
                        15.0f
                    )
                    cameraPositionState.animate(update)
                } catch (e: Exception) {
                    android.util.Log.e("LiveTrackingMap", "Camera animation failed: ${e.message}")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ====================================================
        // GOOGLE MAP
        // ====================================================
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Corridor Route Line
            Polyline(
                points = currentRoute.stops
                    .filter { it.latitude.isFinite() && it.longitude.isFinite() && (it.latitude != 0.0 || it.longitude != 0.0) }
                    .map { LatLng(it.latitude, it.longitude) },
                color = Color(0xFF1976D2),
                width = 12f
            )

            // Corridor Stops
            val totalStops = currentRoute.stops.size
            currentRoute.stops.forEach { stop ->
                if (stop.latitude.isFinite() && stop.longitude.isFinite() &&
                    stop.latitude >= -90.0 && stop.latitude <= 90.0 &&
                    stop.longitude >= -180.0 && stop.longitude <= 180.0 &&
                    (stop.latitude != 0.0 || stop.longitude != 0.0)
                ) {
                    val isPickup = stop.name.equals(pickupStopName, true)
                    val isDest = stop.name.equals(destStopName, true)

                    val markerTitle = when {
                        isPickup -> "Pickup: ${stop.name}"
                        isDest -> "Destination: ${stop.name}"
                        else -> stop.name
                    }
                    val markerSnippet = when {
                        isPickup -> "Boarding point (${stop.sequence}/$totalStops)"
                        isDest -> "Drop-off point (${stop.sequence}/$totalStops)"
                        else -> "Corridor stop ${stop.sequence}/$totalStops"
                    }

                    key("stop_${stop.id}_${stop.name}") {
                        Marker(
                            state = rememberMarkerState(
                                position = LatLng(stop.latitude, stop.longitude)
                            ),
                            title = markerTitle,
                            snippet = markerSnippet
                        )
                    }
                }
            }

            // User Location Marker
            currentLocation?.let { loc ->
                if (loc.latitude.isFinite() && loc.longitude.isFinite() &&
                    loc.latitude >= -90.0 && loc.latitude <= 90.0 &&
                    loc.longitude >= -180.0 && loc.longitude <= 180.0
                ) {
                    key("user_location") {
                        Marker(
                            state = rememberMarkerState(position = loc),
                            title = "Your Location",
                            snippet = "GPS Position",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }
            }

            // Real-Time Animated Driver Markers
            activeDrivers.forEach { driver ->
                if (driver.lat.isFinite() && driver.lng.isFinite() && (driver.lat != 0.0 || driver.lng != 0.0)) {
                    key(driver.driverUid) {
                        AnimatedAutoMarker(
                            driver = driver,
                            autoIcon = autoIconDescriptor,
                            onClick = { liveTrackingViewModel.selectDriver(it) }
                        )
                    }
                }
            }
        }

        // ====================================================
        // TOP OVERLAY: BACK BUTTON & CORRIDOR HEADER
        // ====================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 42.dp, start = 16.dp, end = 16.dp)
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
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = currentRoute.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$pickupStopName → $destStopName",
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

            // ====================================================
            // STATUS BANNER: BROWSE OR EMPTY STATE
            // ====================================================
            if (activeDrivers.isEmpty()) {
                // Empty State Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "No autos currently on the move",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Live operating vehicles will appear as drivers broadcast location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Browse Corridor Drivers Count Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${activeDrivers.size} auto${if (activeDrivers.size > 1) "s" else ""} operating on corridor",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap any auto marker to view available seats & ETA to your stop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // ====================================================
        // BOTTOM OVERLAY: SELECTED DRIVER DETAILS CARD
        // ====================================================
        selectedDriver?.let { driver ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = driver.vehicleNumber.ifBlank { "Auto #${driver.driverUid.takeLast(4).uppercase()}" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = driver.driverName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { liveTrackingViewModel.selectDriver(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seats & ETA Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Available Seats
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${driver.availableSeats} seat(s) free",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Estimated Arrival Time to Passenger's Pickup Stop
                        driver.etaMinutesToPickup?.let { eta ->
                            val distStr = driver.distanceKmToPickup?.let {
                                String.format(java.util.Locale.US, " (%.1f km)", it)
                            } ?: ""
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (eta <= 1) "Arriving now$distStr" else "~$eta min away$distStr",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Speed & Freshness Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val speedKmh = (driver.speed * 3.6f).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$speedKmh km/h",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val secondsAgo = ((System.currentTimeMillis() - driver.lastUpdated) / 1000).coerceAtLeast(0)
                            Text(
                                text = if (secondsAgo < 5) "Live now" else "${secondsAgo}s ago",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}


