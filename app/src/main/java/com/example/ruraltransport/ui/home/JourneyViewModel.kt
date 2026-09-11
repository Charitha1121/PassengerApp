package com.example.ruraltransport.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.JourneyTimeSelection
import com.example.ruraltransport.data.model.JourneyValidationResult
import com.example.ruraltransport.data.model.RouteInfo
import com.example.ruraltransport.data.model.TimeSelectionMode
import com.example.ruraltransport.data.model.TransportStop
import com.example.ruraltransport.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class JourneyUiState(
    val routes: List<RouteInfo> = emptyList(),
    val selectedRoute: RouteInfo? = null,
    val pickupStop: TransportStop? = null,
    val destinationStop: TransportStop? = null,
    val timeSelection: JourneyTimeSelection = JourneyTimeSelection(),
    val validationResult: JourneyValidationResult = JourneyValidationResult.VALID,
    val errorMessage: String? = null
)

class JourneyViewModel(
    private val routeRepository: RouteRepository = RouteRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(JourneyUiState())
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            val routes = routeRepository.getRoutes()
            val primaryRoute = routes.firstOrNull() ?: routeRepository.defaultRoute
            val defaultPickup = primaryRoute.stops.firstOrNull()
            val defaultDestination = primaryRoute.stops.getOrNull(2) // e.g. Sphoorthy College

            _uiState.value = _uiState.value.copy(
                routes = routes,
                selectedRoute = primaryRoute,
                pickupStop = defaultPickup,
                destinationStop = defaultDestination
            )
            validate()
        }
    }

    fun selectRoute(route: RouteInfo) {
        val newPickup = route.stops.firstOrNull()
        val newDestination = route.stops.getOrNull(2) ?: route.stops.lastOrNull()
        _uiState.value = _uiState.value.copy(
            selectedRoute = route,
            pickupStop = newPickup,
            destinationStop = newDestination
        )
        validate()
    }

    fun selectPickupStop(stop: TransportStop) {
        _uiState.value = _uiState.value.copy(pickupStop = stop)
        validate()
    }

    fun selectDestinationStop(stop: TransportStop) {
        _uiState.value = _uiState.value.copy(destinationStop = stop)
        validate()
    }

    fun setTimeMode(mode: TimeSelectionMode) {
        val current = _uiState.value.timeSelection
        val newSelection = if (mode == TimeSelectionMode.NOW) {
            JourneyTimeSelection(
                mode = TimeSelectionMode.NOW,
                targetEpochMillis = System.currentTimeMillis(),
                queryEpochMillis = System.currentTimeMillis()
            )
        } else {
            // Default future time to +1 hour ahead
            val calendar = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            JourneyTimeSelection(
                mode = TimeSelectionMode.FUTURE,
                targetEpochMillis = calendar.timeInMillis,
                queryEpochMillis = System.currentTimeMillis()
            )
        }
        _uiState.value = _uiState.value.copy(timeSelection = newSelection)
        validate()
    }

    fun setCustomFutureTime(targetMillis: Long) {
        val newSelection = JourneyTimeSelection(
            mode = TimeSelectionMode.FUTURE,
            targetEpochMillis = targetMillis,
            queryEpochMillis = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(timeSelection = newSelection)
        validate()
    }

    fun swapStops() {
        val currentPickup = _uiState.value.pickupStop
        val currentDest = _uiState.value.destinationStop
        _uiState.value = _uiState.value.copy(
            pickupStop = currentDest,
            destinationStop = currentPickup
        )
        validate()
    }

    fun validate(): Boolean {
        val state = _uiState.value
        val result = routeRepository.validateJourney(state.pickupStop, state.destinationStop)

        if (result == JourneyValidationResult.SAME_STOP) {
            _uiState.value = state.copy(
                validationResult = result,
                errorMessage = "Pickup and Destination cannot be the same stop."
            )
            return false
        }

        if (state.timeSelection.isPastTime) {
            _uiState.value = state.copy(
                validationResult = JourneyValidationResult.INVALID_TARGET_TIME,
                errorMessage = "Selected future time has already passed. Please select an upcoming time."
            )
            return false
        }

        _uiState.value = state.copy(
            validationResult = JourneyValidationResult.VALID,
            errorMessage = null
        )
        return true
    }
}
