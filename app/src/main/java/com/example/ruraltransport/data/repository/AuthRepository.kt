package com.example.ruraltransport.data.repository

import com.example.ruraltransport.data.model.PassengerProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance("https://ruraltransport-54174-default-rtdb.asia-southeast1.firebasedatabase.app")
) {

    private val passengersRef = database.reference.child("passengers")

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    suspend fun register(
        name: String,
        phone: String,
        email: String,
        password: String
    ): Result<PassengerProfile> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user ?: throw Exception("User registration failed. No user returned.")
            
            val profile = PassengerProfile(
                uid = user.uid,
                name = name.trim(),
                phone = phone.trim(),
                email = email.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Save to Firebase Realtime Database
            passengersRef.child(user.uid).setValue(profile).await()
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        email: String,
        password: String
    ): Result<PassengerProfile> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user ?: throw Exception("Login failed. User is null.")
            
            // Fetch profile from database
            val snapshot = passengersRef.child(user.uid).get().await()
            val profile = if (snapshot.exists()) {
                snapshot.getValue(PassengerProfile::class.java) ?: PassengerProfile(
                    uid = user.uid,
                    email = user.email ?: "",
                    name = user.displayName ?: "Passenger"
                )
            } else {
                // Initial creation if profile missing
                val newProfile = PassengerProfile(
                    uid = user.uid,
                    email = user.email ?: "",
                    name = user.displayName ?: "Passenger"
                )
                passengersRef.child(user.uid).setValue(newProfile).await()
                newProfile
            }

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProfile(uid: String): Result<PassengerProfile?> {
        return try {
            val snapshot = passengersRef.child(uid).get().await()
            val profile = snapshot.getValue(PassengerProfile::class.java)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeProfile(uid: String): Flow<PassengerProfile?> = callbackFlow {
        val ref = passengersRef.child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(PassengerProfile::class.java)
                trySend(profile)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateProfile(profile: PassengerProfile): Result<Unit> {
        return try {
            val updated = profile.copy(updatedAt = System.currentTimeMillis())
            passengersRef.child(profile.uid).setValue(updated).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }
}
