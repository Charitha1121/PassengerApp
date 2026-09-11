package com.example.ruraltransport.data.model

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val profile: PassengerProfile) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
