package com.example.ruraltransport.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.AvailabilityForecast
import com.example.ruraltransport.data.repository.ForecastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ForecastUiState {
    object Loading : ForecastUiState()
    data class Success(val forecast: AvailabilityForecast) : ForecastUiState()
    data class Error(val message: String) : ForecastUiState()
}

class ForecastViewModel(
    private val forecastRepository: ForecastRepository = ForecastRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForecastUiState>(ForecastUiState.Loading)
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    private var currentRouteId: String = ""
    private var currentRouteName: String = ""
    private var currentPickupId: String = ""
    private var currentPickupName: String = ""
    private var currentDestId: String = ""
    private var currentDestName: String = ""
    private var currentTargetMillis: Long = 0L

    fun loadForecast(
        routeId: String,
        routeName: String,
        pickupId: String,
        pickupName: String,
        destId: String,
        destName: String,
        targetEpochMillis: Long,
        queryEpochMillis: Long = System.currentTimeMillis()
    ) {
        currentRouteId = routeId
        currentRouteName = routeName
        currentPickupId = pickupId
        currentPickupName = pickupName
        currentDestId = destId
        currentDestName = destName
        currentTargetMillis = targetEpochMillis

        _uiState.value = ForecastUiState.Loading
        viewModelScope.launch {
            try {
                val forecast = forecastRepository.generateForecast(
                    routeId = routeId,
                    routeName = routeName,
                    pickupStopId = pickupId,
                    pickupStopName = pickupName,
                    destStopId = destId,
                    destStopName = destName,
                    targetEpochMillis = targetEpochMillis,
                    queryEpochMillis = queryEpochMillis
                )
                _uiState.value = ForecastUiState.Success(forecast)
            } catch (e: Exception) {
                _uiState.value = ForecastUiState.Error(
                    e.localizedMessage ?: "Failed to generate availability forecast. Please try again."
                )
            }
        }
    }

    fun refreshForecast() {
        if (currentTargetMillis > 0L) {
            loadForecast(
                routeId = currentRouteId,
                routeName = currentRouteName,
                pickupId = currentPickupId,
                pickupName = currentPickupName,
                destId = currentDestId,
                destName = currentDestName,
                targetEpochMillis = currentTargetMillis,
                queryEpochMillis = System.currentTimeMillis()
            )
        }
    }
}
