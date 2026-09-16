package com.example.ruraltransport.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.repository.RideRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel managing real-time vehicle positions on the corridor map.
 * Subscribes to active drivers from `drivers/{uid}/liveTracking`.
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

    private var observationJob: Job? = null

    init {
        observeDriversForRoute(RouteData.DEFAULT_ROUTE_ID)
    }

    /**
     * Starts observing active drivers for a specific corridor route.
     * Passing null or empty observes all active drivers.
     */
    fun observeDriversForRoute(routeId: String?) {
        _corridorRouteId.value = routeId ?: ""
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observeLiveTrackingDrivers(routeId)
                .catch { emit(emptyList()) }
                .collect { drivers ->
                    _activeDrivers.value = drivers
                    // If selected driver is no longer active, clear selection or update position
                    val selectedId = _selectedDriver.value?.driverUid
                    if (selectedId != null) {
                        val updated = drivers.find { it.driverUid == selectedId }
                        _selectedDriver.value = updated
                    }
                }
        }
    }

    fun selectDriver(driver: LiveDriverPosition?) {
        _selectedDriver.value = driver
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}
