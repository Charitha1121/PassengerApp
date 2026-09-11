package com.example.ruraltransport.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.DriverLocationUpdate
import com.example.ruraltransport.data.model.RideRequest
import com.example.ruraltransport.data.model.RideStatus
import com.example.ruraltransport.data.repository.RideRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RideUiState {
    object Idle : RideUiState()
    object Requesting : RideUiState()
    data class Searching(val requestId: String) : RideUiState()
    data class DriverAssigned(val ride: RideRequest, val driverLocation: DriverLocationUpdate?) : RideUiState()
    data class InProgress(val ride: RideRequest, val driverLocation: DriverLocationUpdate?) : RideUiState()
    data class Completed(val ride: RideRequest) : RideUiState()
    data class Cancelled(val message: String) : RideUiState()
    data class Error(val message: String) : RideUiState()
}

class ActiveRideViewModel(
    private val rideRepository: RideRepository = RideRepository()
) : ViewModel() {

    private val _rideState = MutableStateFlow<RideUiState>(RideUiState.Idle)
    val rideState: StateFlow<RideUiState> = _rideState.asStateFlow()

    private var activeRideJob: Job? = null
    private var driverLocationJob: Job? = null
    private var currentRequestId: String? = null

    fun requestRide(
        passengerId: String,
        passengerName: String,
        passengerPhone: String,
        routeId: String,
        routeName: String,
        pickupStopId: String,
        pickupStopName: String,
        destStopId: String,
        destStopName: String,
        requestedSeats: Int,
        targetTime: Long
    ) {
        _rideState.value = RideUiState.Requesting
        viewModelScope.launch {
            val initialRequest = RideRequest(
                passengerId = passengerId,
                passengerName = passengerName,
                passengerPhone = passengerPhone,
                routeId = routeId,
                routeName = routeName,
                pickupStopId = pickupStopId,
                pickupStopName = pickupStopName,
                destinationStopId = destStopId,
                destinationStopName = destStopName,
                requestedSeats = requestedSeats,
                targetTime = targetTime,
                status = RideStatus.PENDING.name
            )

            val result = rideRepository.createRideRequest(initialRequest)
            result.onSuccess { createdRide ->
                currentRequestId = createdRide.requestId
                _rideState.value = RideUiState.Searching(createdRide.requestId)
                listenToRideUpdates(createdRide.requestId)
            }.onFailure { error ->
                _rideState.value = RideUiState.Error(
                    error.localizedMessage ?: "Failed to submit ride request. Please try again."
                )
            }
        }
    }

    private fun listenToRideUpdates(requestId: String) {
        activeRideJob?.cancel()
        activeRideJob = viewModelScope.launch {
            rideRepository.observeRideRequest(requestId).collect { ride ->
                if (ride == null) return@collect

                when (ride.status.uppercase()) {
                    RideStatus.PENDING.name, RideStatus.MATCHING.name -> {
                        _rideState.value = RideUiState.Searching(requestId)
                    }

                    RideStatus.ACCEPTED.name -> {
                        val driverId = ride.driverId
                        if (driverId != null) {
                            listenToDriverLocation(driverId, ride, isProgress = false)
                        } else {
                            _rideState.value = RideUiState.DriverAssigned(ride, null)
                        }
                    }

                    RideStatus.IN_PROGRESS.name -> {
                        val driverId = ride.driverId
                        if (driverId != null) {
                            listenToDriverLocation(driverId, ride, isProgress = true)
                        } else {
                            _rideState.value = RideUiState.InProgress(ride, null)
                        }
                    }

                    RideStatus.COMPLETED.name -> {
                        _rideState.value = RideUiState.Completed(ride)
                        cleanupListeners()
                    }

                    RideStatus.CANCELLED.name -> {
                        _rideState.value = RideUiState.Cancelled("Ride was cancelled.")
                        cleanupListeners()
                    }

                    RideStatus.REJECTED.name -> {
                        _rideState.value = RideUiState.Error("No available driver could accept this ride.")
                        cleanupListeners()
                    }
                }
            }
        }
    }

    private fun listenToDriverLocation(driverId: String, ride: RideRequest, isProgress: Boolean) {
        driverLocationJob?.cancel()
        driverLocationJob = viewModelScope.launch {
            rideRepository.observeDriverLocation(driverId).collect { location ->
                if (isProgress) {
                    _rideState.value = RideUiState.InProgress(ride, location)
                } else {
                    _rideState.value = RideUiState.DriverAssigned(ride, location)
                }
            }
        }
    }

    fun cancelActiveRide() {
        val reqId = currentRequestId ?: return
        viewModelScope.launch {
            val result = rideRepository.cancelRideRequest(reqId)
            result.onSuccess {
                _rideState.value = RideUiState.Cancelled("You have cancelled your ride request.")
                cleanupListeners()
            }.onFailure { error ->
                _rideState.value = RideUiState.Error(
                    error.localizedMessage ?: "Failed to cancel ride."
                )
            }
        }
    }

    fun resetState() {
        cleanupListeners()
        _rideState.value = RideUiState.Idle
    }

    private fun cleanupListeners() {
        activeRideJob?.cancel()
        driverLocationJob?.cancel()
        activeRideJob = null
        driverLocationJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
