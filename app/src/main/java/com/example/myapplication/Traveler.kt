package com.example.myapplication

import com.google.firebase.firestore.DocumentSnapshot

data class Traveler(
    val name: String,
    val airline: String,
    val destination: String,
    val pnr: String,
    var bookingStatus: String, // "available", "pending", "booked"
    val phoneNumber: String,
    val leavingTime: String,
    val weightUpto: Int,
    val documentSnapshot: DocumentSnapshot? = null,
    val flightNumber: String? = null,
    val arrivalTime: String? = null,
    val flightDuration: String? = null, // Add this
    val price: Int = 0, // Add this
    val notAcceptedItems: List<String> = emptyList(),
    val distance: Double = 0.0
)