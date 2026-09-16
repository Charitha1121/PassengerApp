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

/**
 * Calculates the shortest angular difference to prevent spinning across North (0° / 360°).
 */
internal fun shortestAngleDiff(from: Float, to: Float): Float {
    if (from.isNaN() || to.isNaN()) return 0f
    var diff = (to - from) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

/**
 * Converts a vector drawable resource to a [BitmapDescriptor] suitable for Google Maps markers.
 */
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int, sizeDp: Int = 44): BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, vectorResId)
        ?: return BitmapDescriptorFactory.defaultMarker()
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Live Corridor Tracking Map Screen:
 * Displays active vehicles moving smoothly in real-time along the corridor road,
 * rotating in the direction of travel (heading), just like Rapido/Ola/Uber.
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

    val currentRoute = journeyState.selectedRoute ?: com.example.ruraltransport.data.repository.RouteRepository().defaultRoute
    val pickupStopName = journeyState.pickupStop?.name ?: RouteData.stops.first()
    val destStopName = journeyState.destinationStop?.name ?: RouteData.stops.last()
    val direction = RouteData.getDirection(pickupStopName, destStopName)

    // Keep liveTrackingViewModel in sync with selected corridor routeId
    LaunchedEffect(currentRoute.id) {
        liveTrackingViewModel.observeDriversForRoute(currentRoute.id)
    }

    // Cache the custom auto-rickshaw icon
    val autoIconDescriptor = remember(context) {
        bitmapDescriptorFromVector(context, R.drawable.ic_auto_rickshaw, 44)
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

    // Default camera position focused on the corridor's pickup stop or first stop
    val pickupTransportStop = RouteData.canonicalStops.find { it.name.equals(pickupStopName, true) }
        ?: RouteData.canonicalStops.first()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(pickupTransportStop.latitude, pickupTransportStop.longitude),
            13.8f
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ====================================================
        // GOOGLE MAP
        // ====================================================
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Corridor Fixed Route Line
            Polyline(
                points = RouteData.canonicalStops.map { LatLng(it.latitude, it.longitude) },
                color = Color(0xFF1976D2),
                width = 12f
            )

            // Fixed Corridor Stops
            RouteData.canonicalStops.forEach { stop ->
                val isPickup = stop.name.equals(pickupStopName, true)
                val isDest = stop.name.equals(destStopName, true)

                val markerTitle = when {
                    isPickup -> "Pickup: ${stop.name}"
                    isDest -> "Destination: ${stop.name}"
                    else -> stop.name
                }
                val markerSnippet = when {
                    isPickup -> "Boarding point (${stop.sequence}/4)"
                    isDest -> "Drop-off point (${stop.sequence}/4)"
                    else -> "Corridor stop ${stop.sequence}"
                }

                Marker(
                    state = rememberMarkerState(position = LatLng(stop.latitude, stop.longitude)),
                    title = markerTitle,
                    snippet = markerSnippet
                )
            }

            // User Location Marker
            currentLocation?.let { loc ->
                Marker(
                    state = rememberMarkerState(position = loc),
                    title = "Your Location",
                    snippet = "GPS Position",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
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
            // STATUS BANNER: ACTIVE OR EMPTY STATE
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
                                text = "Live vehicles will appear on this corridor as drivers begin moving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Active Drivers Count Banner
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
                                text = "${activeDrivers.size} auto${if (activeDrivers.size > 1) "s" else ""} moving live",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "GPS updates stream in real-time with road rotation & speed",
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

                    Spacer(modifier = Modifier.height(10.dp))

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

/**
 * Animated Google Maps marker for an active driver:
 * - Interpolates LatLng continuously over ~3.5 seconds with [LinearEasing] to smoothly glide between GPS updates.
 * - Interpolates heading rotation with shortest angular distance to prevent 360° spin flips.
 * - Uses [anchor] (0.5, 0.5) so rotation centers on the vehicle roof.
 * - Uses [flat] = true so the icon lays on the map plane.
 */
@Composable
fun AnimatedAutoMarker(
    driver: LiveDriverPosition,
    autoIcon: BitmapDescriptor?,
    onClick: (LiveDriverPosition) -> Unit
) {
    // Initial coordinates snap immediately to prevent flying from (0, 0)
    val initialLat = if (driver.lat.isFinite()) driver.lat.toFloat() else 0f
    val initialLng = if (driver.lng.isFinite()) driver.lng.toFloat() else 0f
    val initialHeading = if (driver.heading.isFinite()) driver.heading else 0f

    val animLat = remember(driver.driverUid) { Animatable(initialLat) }
    val animLng = remember(driver.driverUid) { Animatable(initialLng) }
    val animHeading = remember(driver.driverUid) { Animatable(initialHeading) }

    // Smooth position glide between updates (typical driver GPS interval ~3-4s)
    LaunchedEffect(driver.lat, driver.lng) {
        if (driver.lat.isFinite() && driver.lng.isFinite()) {
            launch {
                animLat.animateTo(
                    targetValue = driver.lat.toFloat(),
                    animationSpec = tween(durationMillis = 3500, easing = LinearEasing)
                )
            }
            launch {
                animLng.animateTo(
                    targetValue = driver.lng.toFloat(),
                    animationSpec = tween(durationMillis = 3500, easing = LinearEasing)
                )
            }
        }
    }

    // Smooth heading rotation with shortest angle difference
    LaunchedEffect(driver.heading) {
        if (driver.heading.isFinite()) {
            val diff = shortestAngleDiff(animHeading.value, driver.heading)
            animHeading.animateTo(
                targetValue = animHeading.value + diff,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
        }
    }

    val currentLat = if (animLat.value.isFinite()) animLat.value.toDouble() else 0.0
    val currentLng = if (animLng.value.isFinite()) animLng.value.toDouble() else 0.0
    val currentPosition = LatLng(currentLat, currentLng)
    val markerState = rememberMarkerState(key = driver.driverUid, position = currentPosition)
    LaunchedEffect(animLat.value, animLng.value) {
        markerState.position = currentPosition
    }

    val speedKmh = (driver.speed * 3.6f).toInt()
    val speedSnippet = if (speedKmh > 0) "$speedKmh km/h • On the move" else "Active ride"

    Marker(
        state = markerState,
        title = driver.vehicleNumber.ifBlank { "Auto #${driver.driverUid.takeLast(4).uppercase()}" },
        snippet = "${driver.driverName} • $speedSnippet",
        icon = autoIcon,
        rotation = animHeading.value,
        anchor = Offset(0.5f, 0.5f),
        flat = true,
        onClick = {
            onClick(driver)
            false
        }
    )
}
