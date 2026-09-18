package com.example.ruraltransport.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
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
 * Safe for use before Maps SDK is fully ready by catching potential factory exceptions.
 */
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int, sizeDp: Int = 44): BitmapDescriptor? {
    return try {
        val drawable = ContextCompat.getDrawable(context, vectorResId)
            ?: return BitmapDescriptorFactory.defaultMarker()
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (e: Exception) {
        // Log error and return null to avoid crashing if BitmapDescriptorFactory is not yet initialized
        android.util.Log.e("MapComponents", "Failed to create bitmap descriptor: ${e.message}")
        null
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
    onClick: (LiveDriverPosition) -> Unit = {}
) {
    val initialLat = if (driver.lat.isFinite()) driver.lat.toFloat() else 0f
    val initialLng = if (driver.lng.isFinite()) driver.lng.toFloat() else 0f
    val initialHeading = if (driver.heading.isFinite()) driver.heading else 0f

    val animLat = remember(driver.driverUid) { Animatable(initialLat) }
    val animLng = remember(driver.driverUid) { Animatable(initialLng) }
    val animHeading = remember(driver.driverUid) { Animatable(initialHeading) }

    // Smooth position glide between updates (typical driver GPS interval ~2-4s)
    // For simulator (1s updates), we use a shorter 1.5s duration to stay responsive.
    LaunchedEffect(driver.lat, driver.lng) {
        if (driver.lat.isFinite() && driver.lng.isFinite()) {
            launch {
                animLat.animateTo(
                    targetValue = driver.lat.toFloat(),
                    animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
                )
            }
            launch {
                animLng.animateTo(
                    targetValue = driver.lng.toFloat(),
                    animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
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
                animationSpec = tween(durationMillis = 800, easing = LinearEasing)
            )
        }
    }

    // Stable marker state tied to driverUid
    val markerState = rememberMarkerState(key = driver.driverUid, position = LatLng(initialLat.toDouble(), initialLng.toDouble()))
    
    // Bridge the Animatable values to the MarkerState.
    // By reading animLat.value and animLng.value here, we ensure this block 
    // runs on every frame of the animation, updating the marker's position on the map.
    val currentLat = if (animLat.value.isFinite()) animLat.value.toDouble() else 0.0
    val currentLng = if (animLng.value.isFinite()) animLng.value.toDouble() else 0.0
    markerState.position = LatLng(currentLat, currentLng)

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
