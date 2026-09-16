package com.example.ruraltransport.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.LiveDriverUiModel
import com.example.ruraltransport.data.model.RouteData
import com.example.ruraltransport.data.model.RouteDirection
import com.example.ruraltransport.data.repository.RideRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class WaitingUiState(
    val isWaiting: Boolean = false,
    val routeId: String = RouteData.DEFAULT_ROUTE_ID,
    val direction: RouteDirection = RouteDirection.FORWARD,
    val pickupStop: String = "",
    val destinationStop: String = "",
    val waitingPassengersCount: Int = 0,
    val approachingDriversCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class PassengerViewModel(
    private val repository: RideRepository = RideRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _waitingState = MutableStateFlow(WaitingUiState())
    val waitingState: StateFlow<WaitingUiState> = _waitingState.asStateFlow()

    private val _liveDrivers = MutableStateFlow<List<LiveDriverUiModel>>(emptyList())
    val liveDrivers: StateFlow<List<LiveDriverUiModel>> = _liveDrivers.asStateFlow()

    private var countObservationJob: Job? = null
    private var driversObservationJob: Job? = null

    init {
        checkExistingWaitingSession()
    }

    private fun getCurrentPassengerUid(): String {
        return auth.currentUser?.uid ?: "anon_${android.os.Build.DEVICE.take(6)}"
    }

    /**
     * Checks if a previous waiting session exists on app load (e.g. app was killed).
     * Automatically cleans up if >30 minutes old, or resumes active state if recent.
     */
    fun checkExistingWaitingSession() {
        viewModelScope.launch {
            val uid = getCurrentPassengerUid()
            val result = repository.findAndHandleExistingWaitingSession(uid)
            result.onSuccess { activeSession ->
                if (activeSession != null) {
                    _waitingState.value = _waitingState.value.copy(
                        isWaiting = true,
                        routeId = activeSession.routeId,
                        direction = activeSession.direction,
                        pickupStop = activeSession.pickupStop,
                        destinationStop = activeSession.destinationStop,
                        isLoading = false
                    )
                    startObservingWaitingCount(
                        routeId = activeSession.routeId,
                        direction = activeSession.direction,
                        pickupStop = activeSession.pickupStop
                    )
                    observeDriversOnCorridor(
                        routeId = activeSession.routeId,
                        direction = activeSession.direction,
                        pickupStop = activeSession.pickupStop
                    )
                }
            }
        }
    }

    /**
     * Starts waiting demand: writes node, arms onDisconnect(), and observes live counts.
     */
    fun startWaiting(
        pickupStop: String,
        destinationStop: String,
        routeId: String = RouteData.DEFAULT_ROUTE_ID
    ) {
        val uid = getCurrentPassengerUid()
        val direction = RouteData.getDirection(pickupStop, destinationStop)

        // If already waiting elsewhere, cleanly remove old entry first
        val current = _waitingState.value
        if (current.isWaiting && (current.pickupStop != pickupStop || current.direction != direction)) {
            viewModelScope.launch {
                repository.stopWaitingRequest(
                    routeId = current.routeId,
                    direction = current.direction,
                    pickupStop = current.pickupStop,
                    passengerUid = uid
                )
            }
        }

        _waitingState.value = _waitingState.value.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            val result = repository.startWaitingRequest(
                routeId = routeId,
                direction = direction,
                pickupStop = pickupStop,
                destinationStop = destinationStop,
                passengerUid = uid
            )

            result.onSuccess {
                Log.i("PassengerViewModel", "Waiting demand successfully placed at $pickupStop ($direction) for route $routeId")
                _waitingState.value = _waitingState.value.copy(
                    isWaiting = true,
                    routeId = routeId,
                    direction = direction,
                    pickupStop = pickupStop,
                    destinationStop = destinationStop,
                    isLoading = false,
                    error = null
                )
                startObservingWaitingCount(routeId, direction, pickupStop)
                observeDriversOnCorridor(routeId, direction, pickupStop)
            }.onFailure { ex ->
                Log.e("PassengerViewModel", "Failed to record waiting request: ${ex.message}", ex)
                _waitingState.value = _waitingState.value.copy(
                    isLoading = false,
                    error = ex.message ?: "Failed to record waiting request"
                )
            }
        }
    }

    /**
     * Cancels / stops waiting demand: removes node and cancels onDisconnect().
     */
    fun stopWaiting() {
        val current = _waitingState.value
        if (!current.isWaiting) return

        val uid = getCurrentPassengerUid()
        countObservationJob?.cancel()
        countObservationJob = null

        _waitingState.value = _waitingState.value.copy(
            isWaiting = false,
            waitingPassengersCount = 0,
            isLoading = false
        )

        viewModelScope.launch {
            val res = repository.stopWaitingRequest(
                routeId = current.routeId,
                direction = current.direction,
                pickupStop = current.pickupStop,
                passengerUid = uid
            )
            res.onSuccess {
                Log.i("PassengerViewModel", "Waiting request cancelled successfully for $uid at ${current.pickupStop}")
            }.onFailure { ex ->
                Log.e("PassengerViewModel", "Error cancelling waiting request: ${ex.message}", ex)
            }
        }
    }

    /**
     * Handles edge case: Passenger changes destination or pickup while already waiting.
     */
    fun updateWaitingJourneyIfChanged(newPickup: String, newDestination: String) {
        val current = _waitingState.value
        if (!current.isWaiting) return

        if (current.pickupStop != newPickup || current.destinationStop != newDestination) {
            startWaiting(newPickup, newDestination, current.routeId)
        }
    }

    /**
     * Observe live waiting count at the pickup stop.
     */
    private fun startObservingWaitingCount(
        routeId: String,
        direction: RouteDirection,
        pickupStop: String
    ) {
        countObservationJob?.cancel()
        countObservationJob = viewModelScope.launch {
            repository.observeWaitingPassengersCount(routeId, direction, pickupStop)
                .catch { /* ignore */ }
                .collect { count ->
                    _waitingState.value = _waitingState.value.copy(
                        waitingPassengersCount = count
                    )
                }
        }
    }

    /**
     * Observes matching corridor drivers live (isOnline, same direction, at or before pickup).
     */
    fun observeDriversOnCorridor(
        routeId: String = RouteData.DEFAULT_ROUTE_ID,
        direction: RouteDirection,
        pickupStop: String
    ) {
        driversObservationJob?.cancel()
        driversObservationJob = viewModelScope.launch {
            repository.observeDriversOnCorridor(routeId, direction, pickupStop)
                .catch { emit(emptyList()) }
                .collect { drivers ->
                    _liveDrivers.value = drivers
                    _waitingState.value = _waitingState.value.copy(
                        approachingDriversCount = drivers.size
                    )
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countObservationJob?.cancel()
        driversObservationJob?.cancel()
    }
}
