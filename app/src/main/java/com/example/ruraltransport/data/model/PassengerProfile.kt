package com.example.ruraltransport.data.model

data class PassengerProfile(
    val uid: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
