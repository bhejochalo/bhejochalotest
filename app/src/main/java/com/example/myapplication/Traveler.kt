package com.example.myapplication

data class Traveler(
    val name: String = "",
    val airline: String = "",
    val destination: String = "",
    val pnr: String = "",
    var bookingStatus: String = "available" // available/pending/booked
)