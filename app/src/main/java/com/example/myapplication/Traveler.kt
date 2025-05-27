package com.example.myapplication

import com.google.firebase.firestore.DocumentSnapshot

data class Traveler(
    val name: String = "",
    val airline: String = "",
    val destination: String = "",
    val pnr: String = "",
    var bookingStatus: String = "available", // available/pending/booke
    var documentSnapshot: DocumentSnapshot? = null // To store the Firestore document snapshot
)//new code