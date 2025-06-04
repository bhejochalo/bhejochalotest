package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.Sender // Replace with your actual package
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class SenderProfile : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_sender_profile)
        enableEdgeToEdge()

        // Set up window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Display addresses (from AddressHolder)
        displayAddresses()

        // Display sender booking details
        displaySenderBookingDetails()
    }

    private fun displayAddresses() {
        val tvFromAddress = findViewById<TextView>(R.id.tvFromAddress)
        val tvToAddress = findViewById<TextView>(R.id.tvToAddress)

        val fromAddress = """
            ${AddressHolder.fromHouseNumber}, 
            ${AddressHolder.fromStreet}, 
            ${AddressHolder.fromArea}, 
            ${AddressHolder.fromCity}, 
            ${AddressHolder.fromState} - ${AddressHolder.fromPostalCode}
        """.trimIndent()

        val toAddress = """
            ${AddressHolder.toHouseNumber}, 
            ${AddressHolder.toStreet}, 
            ${AddressHolder.toArea}, 
            ${AddressHolder.toCity}, 
            ${AddressHolder.toState} - ${AddressHolder.toPostalCode}
        """.trimIndent()

        tvFromAddress.text = fromAddress
        tvToAddress.text = toAddress
    }

    private fun displaySenderBookingDetails() {
        val tvStatus = findViewById<TextView>(R.id.bookingStatus)
        val document = Sender.senderRecord
        if (document != null && document.exists()) {
            // Extract data from Firestore document
            val key = document.getString("uniqueKey") ?: ""
            fetchTheCurrentSenderBorzoOrder(key, "Sender")

            val status = "" // document.getString("status") ?: "N/A"
            tvStatus.text = "Status: $status"
            Log.d("SenderProfile", "Loaded booking details:  $status")
        } else {
            tvStatus.text = "No booking data found!"
            Log.e("SenderProfile", "Sender document is null or doesn't exist")
        }
    }

    /*private fun fetchTheCurrentSenderBorzoOrder(uniqueKey: String, sender: String) {
        val subStatus = findViewById<TextView>(R.id.subStatus)
        val trackingUrlTextView = findViewById<TextView>(R.id.trackingUrl)
        val startTimeSender = findViewById<TextView>(R.id.startTimeSender)
        val endTimeSender = findViewById<TextView>(R.id.endTimeSender)

        val db = FirebaseFirestore.getInstance()

        db.collection("borzo_orders")
            .whereEqualTo("uniqueKey", uniqueKey)
            .whereNotEqualTo("status", "finished")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        val status = document.getString("status") ?: "N/A"
                        subStatus.text = status

                        // Get points array from document
                        @Suppress("UNCHECKED_CAST")
                        val points = document.get("points") as? List<Map<String, Any>>

                        if (points.isNullOrEmpty()) {
                            Log.w("Firestore", "No points data found")
                            return@addOnSuccessListener
                        }

                        Log.d("Firestore", "Found ${points.size} points")

                        // Process first point
                        points[0].let { firstPoint ->
                            val trackingUrl = firstPoint["trackingUrl"]?.toString() ?: "URL Null"
                            trackingUrlTextView.text = trackingUrl
                            val name = firstPoint["contactPerson.name"]?.toString() ?: "Unnamed Point"
                            Log.d("FirstPoint", "Name: $name")

                            if (name.contains("Sender")) {
                                // Set sender times
                                firstPoint["requiredStartDatetime"]?.toString()?.let {
                                    startTimeSender.text = "Start: $it"
                                } ?: run {
                                    startTimeSender.text = "Start time missing"
                                }

                                firstPoint["requiredFinishDatetime"]?.toString()?.let {
                                    endTimeSender.text = "End: $it"
                                } ?: run {
                                    endTimeSender.text = "End time missing"
                                }
                            }
                        }

                        // Process second point if available
                        if (points.size > 1) {
                            points[1].let { secondPoint ->
                                val trackingUrl = secondPoint["trackingUrl"]?.toString() ?: "URL Null"
                                trackingUrlTextView.text = trackingUrl
                                val name = secondPoint["contactPerson.name"]?.toString() ?: "Unnamed Point"
                                Log.d("SecondPoint", "Name: $name")

                                if (name.contains("Sender")) {
                                    // Set sender times
                                    secondPoint["requiredStartDatetime"]?.toString()?.let {
                                        startTimeSender.text = "Start: $it"
                                    } ?: run {
                                        startTimeSender.text = "Start time missing"
                                    }

                                    secondPoint["requiredFinishDatetime"]?.toString()?.let {
                                        endTimeSender.text = "End: $it"
                                    } ?: run {
                                        endTimeSender.text = "End time missing"
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d("Firestore", "No document found with key: $uniqueKey")
                       // subStatus.text = "No order found"
                    }
                } catch (e: Exception) {
                    Log.e("Firestore", "Error processing document", e)
                    //subStatus.text = "Error loading data"
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error fetching order", exception)
                *//* subStatus.text = "Failed to load data" *//*
            }
    }*/

    private fun fetchTheCurrentSenderBorzoOrder(uniqueKey: String, sender: String) {
        val subStatus = findViewById<TextView>(R.id.subStatus)
        val trackingUrlTextView = findViewById<TextView>(R.id.trackingUrl)
        val startTimeSender = findViewById<TextView>(R.id.startTimeSender)
        val endTimeSender = findViewById<TextView>(R.id.endTimeSender)

        val db = FirebaseFirestore.getInstance()

        db.collection("borzo_orders")
            .whereEqualTo("uniqueKey", uniqueKey)
            .whereNotEqualTo("status", "finished")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]

                        // 1. Get and display status
                        val status = document.getString("status") ?: "N/A"
                        subStatus.text = "Status: $status"

                        // 2. Process points array
                        @Suppress("UNCHECKED_CAST")
                        val points = document.get("points") as? List<Map<String, Any>> ?: emptyList()

                        if (points.isEmpty()) {
                            subStatus.text = "No points data available"
                            return@addOnSuccessListener
                        }

                        // 3. Find and process SENDER point
                        points.firstOrNull { point ->
                            point["contactPerson.name"]?.toString()?.contains("Sender") == true
                        }?.let { senderPoint ->
                            // Tracking URL
                            senderPoint["trackingUrl"]?.toString()?.let { url ->
                                trackingUrlTextView.text = "Tracking: $url"
                            }

                            // Format and display times
                            fun formatDateTime(raw: String?): String {
                                return raw?.replace("T", " ")?.substringBefore("+") ?: "Not specified"
                            }

                            senderPoint["requiredStartDatetime"]?.toString()?.let {
                                startTimeSender.text = "Pickup: ${formatDateTime(it)}"
                            }

                            senderPoint["requiredFinishDatetime"]?.toString()?.let {
                                endTimeSender.text = "Delivery: ${formatDateTime(it)}"
                            }
                        } ?: run {
                            subStatus.text = "No sender point found"
                        }
                    } else {
                        subStatus.text = "No active order found"
                        Log.d("Firestore", "No document found with key: $uniqueKey")
                    }
                } catch (e: Exception) {
                    subStatus.text = "Error loading data"
                    Log.e("Firestore", "Error processing document", e)
                }
            }
            .addOnFailureListener { exception ->
                subStatus.text = "Failed to connect"
                Log.e("Firestore", "Error fetching order", exception)
            }
    }
}