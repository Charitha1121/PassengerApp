package com.example.ruraltransport.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.GeoUtils
import com.example.ruraltransport.data.model.LiveDriverPosition
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.data.model.RouteInfo
import com.example.ruraltransport.data.model.RouteMatchResult
import com.example.ruraltransport.data.model.TransportStop
import com.example.ruraltransport.data.repository.RideRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class DirectRouteMatch(
    val matchResult: RouteMatchResult,
    val matchingDrivers: List<LiveDriverPosition>
)

data class NearbyRouteStopMatch(
    val nearbyStop: TransportStop,
    val distanceKm: Double,
    val route: RouteInfo,
    val operatingDrivers: List<LiveDriverPosition>
)

sealed interface SearchOutcome {
    object Idle : SearchOutcome
    data class ResultsFound(
        val directMatches: List<DirectRouteMatch>,
        val nearbyFallbacks: List<NearbyRouteStopMatch>
    ) : SearchOutcome
    data class NoResults(
        val sourceStop: TransportStop,
        val destinationStop: TransportStop
    ) : SearchOutcome
}

sealed interface SearchableItem {
    data class Stop(val stop: TransportStop) : SearchableItem {
        override val displayName: String get() = stop.name
    }
    data class Route(val route: RouteInfo) : SearchableItem {
        override val displayName: String get() = route.name
    }
    val displayName: String
}

data class RouteSearchUiState(
    val sourceStop: TransportStop? = null,
    val destinationStop: TransportStop? = null,
    val searchableItems: List<SearchableItem> = emptyList(),
    val isSearching: Boolean = false,
    val outcome: SearchOutcome = SearchOutcome.Idle,
    val error: String? = null
)

class RouteSearchViewModel(
    private val repository: RideRepository = RideRepository(),
    private val routeRepository: com.example.ruraltransport.data.repository.RouteRepository = com.example.ruraltransport.data.repository.RouteRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteSearchUiState())
    val uiState: StateFlow<RouteSearchUiState> = _uiState.asStateFlow()

    private var activeDriversJob: Job? = null

    init {
        loadRoutesAndStops()
    }

    fun loadRoutesAndStops() {
        viewModelScope.launch {
            val dbRoutes = routeRepository.getRoutes()
            dbRoutes.forEach { RouteData.registerRoute(it) }
            
            val stops = RouteData.getAllKnownStops().map { SearchableItem.Stop(it) }
            val routes = RouteData.allRoutes.map { SearchableItem.Route(it) }
            
            val currentSource = _uiState.value.sourceStop ?: (stops.firstOrNull() as? SearchableItem.Stop)?.stop
            val currentDest = _uiState.value.destinationStop ?: (stops.lastOrNull() as? SearchableItem.Stop)?.stop
            
            _uiState.value = _uiState.value.copy(
                searchableItems = stops + routes,
                sourceStop = currentSource,
                destinationStop = currentDest
            )
        }
    }

    private fun setSourceStop(stop: TransportStop) {
        _uiState.value = _uiState.value.copy(sourceStop = stop, error = null)
    }

    private fun setDestinationStop(stop: TransportStop) {
        _uiState.value = _uiState.value.copy(destinationStop = stop, error = null)
    }

    fun selectSearchableItem(item: SearchableItem, isSource: Boolean) {
        when (item) {
            is SearchableItem.Stop -> {
                if (isSource) setSourceStop(item.stop) else setDestinationStop(item.stop)
            }
            is SearchableItem.Route -> {
                // If a route is selected, populate BOTH Source and Destination with its terminals
                val first = item.route.stops.firstOrNull()
                val last = item.route.stops.lastOrNull()
                if (first != null && last != null) {
                    _uiState.value = _uiState.value.copy(
                        sourceStop = first,
                        destinationStop = last,
                        error = null
                    )
                }
            }
        }
    }

    fun swapStops() {
        val src = _uiState.value.sourceStop
        val dst = _uiState.value.destinationStop
        _uiState.value = _uiState.value.copy(
            sourceStop = dst,
            destinationStop = src,
            error = null
        )
    }

    fun searchRoutes() {
        val src = _uiState.value.sourceStop
        val dst = _uiState.value.destinationStop

        if (src == null || dst == null) {
            _uiState.value = _uiState.value.copy(error = "Please select both Source and Destination stops.")
            return
        }

        if (src.name.trim().equals(dst.name.trim(), ignoreCase = true) || src.id == dst.id) {
            _uiState.value = _uiState.value.copy(error = "Source and Destination cannot be the same stop.")
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        activeDriversJob?.cancel()

        activeDriversJob = viewModelScope.launch {
            // 1. Find all matching routes for this pair
            val directMatches = RouteData.findAllMatchingRoutes(src.name, dst.name)
            
            // 2. Find nearby stops as fallbacks
            val nearbyStopsWithDist = RouteData.findNearbyStops(src, maxDistanceKm = 3.0)

            // 3. Observe ALL active drivers to filter them later
            repository.observeCorridorBrowseDrivers(
                routeId = null,
                direction = null,
                pickupLatLng = LatLng(src.latitude, src.longitude)
            ).catch {
                _uiState.value = _uiState.value.copy(isSearching = false, error = "Failed to fetch active autos.")
            }.collect { allDrivers ->
                Log.d("SEARCH_DEBUG", "Found ${allDrivers.size} total active drivers in corridor")
                allDrivers.forEach { d ->
                    Log.d("SEARCH_DEBUG", "Driver: ${d.driverName}, Route: '${d.routeId}', Dir: '${d.activeDirection}', Stop: '${d.currentStop}'")
                }

                val directResults = directMatches.map { match ->
                    val driversOnThisRoute = allDrivers.filter { d ->
                        val isMatch = RouteData.isDriverMatchingRoute(d, match.route, match.direction)
                        if (isMatch) {
                            Log.d("SEARCH_DEBUG", ">> MATCH FOUND: ${d.driverName} on route ${match.route.name}")
                        } else {
                            Log.d("SEARCH_DEBUG", ">> NO MATCH: ${d.driverName} on route ${match.route.name}")
                        }
                        isMatch
                    }
                    DirectRouteMatch(match, driversOnThisRoute)
                }

                val nearbyResults = mutableListOf<NearbyRouteStopMatch>()
                for ((nearbyStop, distance) in nearbyStopsWithDist) {
                    val routesForStop = RouteData.getRoutesForStop(nearbyStop.name)
                    for (route in routesForStop) {
                        val driversOnNearbyRoute = allDrivers.filter { d ->
                            RouteData.isDriverMatchingRoute(d, route, null)
                        }
                        if (driversOnNearbyRoute.isNotEmpty()) {
                            nearbyResults.add(NearbyRouteStopMatch(nearbyStop, distance, route, driversOnNearbyRoute))
                        }
                    }
                }

                if (directResults.isEmpty() && nearbyResults.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        outcome = SearchOutcome.NoResults(src, dst)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        outcome = SearchOutcome.ResultsFound(
                            directMatches = directResults,
                            nearbyFallbacks = nearbyResults.sortedBy { it.distanceKm }
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDriversJob?.cancel()
    }
}
