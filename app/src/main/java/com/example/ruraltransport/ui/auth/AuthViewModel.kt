package com.example.ruraltransport.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ruraltransport.data.model.AuthState
import com.example.ruraltransport.data.model.PassengerProfile
import com.example.ruraltransport.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        val currentUser = authRepository.currentUser
        if (currentUser != null) {
            _authState.value = AuthState.Loading
            viewModelScope.launch {
                val result = authRepository.fetchProfile(currentUser.uid)
                result.onSuccess { profile ->
                    if (profile != null) {
                        _authState.value = AuthState.Authenticated(profile)
                    } else {
                        val fallbackProfile = PassengerProfile(
                            uid = currentUser.uid,
                            email = currentUser.email ?: "",
                            name = currentUser.displayName ?: "Passenger"
                        )
                        _authState.value = AuthState.Authenticated(fallbackProfile)
                    }
                }.onFailure {
                    // Fallback to basic session if network is slow
                    val fallbackProfile = PassengerProfile(
                        uid = currentUser.uid,
                        email = currentUser.email ?: "",
                        name = currentUser.displayName ?: "Passenger"
                    )
                    _authState.value = AuthState.Authenticated(fallbackProfile)
                }
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun register(
        name: String,
        phone: String,
        email: String,
        password: String,
        confirmPass: String
    ) {
        if (name.isBlank()) {
            _authState.value = AuthState.Error("Please enter your full name.")
            return
        }
        if (phone.isBlank() || phone.length < 10) {
            _authState.value = AuthState.Error("Please enter a valid 10-digit phone number.")
            return
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters long.")
            return
        }
        if (password != confirmPass) {
            _authState.value = AuthState.Error("Passwords do not match.")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authRepository.register(name, phone, email, password)
            result.onSuccess { profile ->
                _authState.value = AuthState.Authenticated(profile)
            }.onFailure { exception ->
                _authState.value = AuthState.Error(
                    exception.localizedMessage ?: "Registration failed. Please check your credentials."
                )
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _authState.value = AuthState.Error("Please enter your password.")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            result.onSuccess { profile ->
                _authState.value = AuthState.Authenticated(profile)
            }.onFailure { exception ->
                _authState.value = AuthState.Error(
                    exception.localizedMessage ?: "Invalid email or password. Please try again."
                )
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _authState.value = AuthState.Unauthenticated
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}
