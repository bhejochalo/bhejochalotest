package com.example.myapplication

data class Traveler(
    val name: String,
    val airline: String,
    val destination: String,
    val pnr: String,
    var bookingStatus: String = "available" // can be "available", "pending", or "booked"
)