package com.example.ruraltransport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.data.repository.RideRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel managing real-time vehicle positions on the corridor map.
 * ISSUE 2 Architecture:
 * - Pre-booking / Browse Mode: Subscribes to available operating autos on the corridor via `drivers/{uid}/liveLocation`.
 * - Post-acceptance / Active Mode: Can subscribe to the passenger's assigned driver via `drivers/{driverId}/liveTracking`.
 */
class LiveTrackingViewModel(
    private val repository: RideRepository = RideRepository()
) : ViewModel() {

    private val _activeDrivers = MutableStateFlow<List<LiveDriverPosition>>(emptyList())
    val activeDrivers: StateFlow<List<LiveDriverPosition>> = _activeDrivers.asStateFlow()

    private val _selectedDriver = MutableStateFlow<LiveDriverPosition?>(null)
    val selectedDriver: StateFlow<LiveDriverPosition?> = _selectedDriver.asStateFlow()

    private val _corridorRouteId = MutableStateFlow(RouteData.DEFAULT_ROUTE_ID)
    val corridorRouteId: StateFlow<String> = _corridorRouteId.asStateFlow()

    private var browseJob: Job? = null
    private var trackingJob: Job? = null

    init {
        observeCorridorDrivers(RouteData.DEFAULT_ROUTE_ID)
    }

    /**
     * Observes all available corridor autos continuously using `drivers/{uid}/liveLocation`.
     * Calculates distance and ETA to [pickupLatLng] at rural corridor speed (20 km/h).
     */
    fun observeCorridorDrivers(
        routeId: String? = RouteData.DEFAULT_ROUTE_ID,
        direction: RouteDirection? = null,
        pickupLatLng: LatLng? = null
    ) {
        _corridorRouteId.value = routeId ?: RouteData.DEFAULT_ROUTE_ID
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            repository.observeCorridorBrowseDrivers(
                routeId = routeId,
                direction = direction,
                pickupLatLng = pickupLatLng
            ).catch { emit(emptyList()) }
                .collect { drivers ->
                    _activeDrivers.value = drivers
                    // Keep selected driver updated in real-time
                    val selectedId = _selectedDriver.value?.driverUid
                    if (selectedId != null) {
                        _selectedDriver.value = drivers.find { it.driverUid == selectedId }
                    }
                }
        }
    }

    /**
     * Backward-compatible helper for callers passing only routeId.
     */
    fun observeDriversForRoute(routeId: String?) {
        observeCorridorDrivers(routeId = routeId)
    }

    /**
     * Switches to tracking a specific accepted driver via `drivers/{driverId}/liveTracking`.
     */
    fun observeAcceptedDriver(driverId: String, driverName: String? = null, vehicleNumber: String? = null) {
        trackingJob?.cancel()
        browseJob?.cancel()
        trackingJob = viewModelScope.launch {
            repository.observeDriverLocation(driverId)
                .catch { /* ignore */ }
                .collect { loc ->
                    if (loc != null && loc.latitude.isFinite() && loc.longitude.isFinite() && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                        val singleDriver = LiveDriverPosition(
                            driverUid = driverId,
                            lat = loc.latitude,
                            lng = loc.longitude,
                            heading = loc.bearing,
                            speed = loc.speed,
                            isRideActive = true,
                            isAvailable = false,
                            lastUpdated = loc.timestamp,
                            driverName = driverName ?: "Assigned Driver",
                            vehicleNumber = vehicleNumber ?: "Auto"
                        )
                        _activeDrivers.value = listOf(singleDriver)
                        _selectedDriver.value = singleDriver
                    }
                }
        }
    }

    fun selectDriver(driver: LiveDriverPosition?) {
        _selectedDriver.value = driver
    }

    override fun onCleared() {
        super.onCleared()
        browseJob?.cancel()
        trackingJob?.cancel()
        browseJob = null
        trackingJob = null
    }
}

