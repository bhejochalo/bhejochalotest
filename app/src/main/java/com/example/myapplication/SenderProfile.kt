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
import com.google.firebase.firestore.FirebaseFirestoreException

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
        if (document?.exists() == true) {
            var selfOrAuto = ""
            val deliveryPrice = document.getLong("deliveryOptionPrice") ?: 0L

            if (deliveryPrice == 750L) {
                selfOrAuto = "self"
            } else {
                selfOrAuto = "auto"

            }


            // only get the borzo delivery details on Auto pick drop
            if (document != null && document.exists() && selfOrAuto == "auto") {
                // Extract data from Firestore document
                val key = document.getString("uniqueKey") ?: ""
                fetchTheCurrentSenderBorzoOrder(key, "Sender")

                val status = "" // document.getString("status") ?: "N/A"
                tvStatus.text = "Status: $status"
                Log.d("SenderProfile", "Loaded booking details:  $status")
            } else {

                if (selfOrAuto == "auto") {
                    tvStatus.text = "No booking data found!"
                } else {
                    tvStatus.text = "Self Pick And Drop !"
                }

                Log.e("SenderProfile", "Sender document is null or doesn't exist")
            }
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
        val mainStatus = findViewById<TextView>(R.id.bookingStatus)

        val db = FirebaseFirestore.getInstance()
        Log.d("Firestore", "Fetching order with uniqueKey: $uniqueKey")

        db.collection("borzo_orders")
            .whereEqualTo("uniqueKey", uniqueKey)
            .whereNotEqualTo("order.status", "finished")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                try {
                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        Log.d("Firestore", "Found document: ${document.id}")

                        // 1. Get and display status
                        val status =
                            (document.get("order") as? Map<*, *>)?.get("status")?.toString()
                                ?: document.getString("status") ?: "N/A"
                        mainStatus.text = "Status: $status"
                        subStatus.text = "Status: $status"

                        // 2. Get points subcollection
                        document.reference.collection("points")
                            .get()
                            .addOnSuccessListener { pointsSnapshot ->
                                val points = pointsSnapshot.documents.map {
                                    it.data ?: emptyMap<String, Any>()
                                }

                                if (points.isEmpty()) {
                                    subStatus.text = "No points data available"
                                    Log.w("Firestore", "Empty points subcollection")
                                    return@addOnSuccessListener
                                }

                                // 3. Find and process SENDER point
                                points.firstOrNull { point ->
                                    try {
                                        // Handle both dot notation and nested map
                                        val contactName = point["contactPerson.name"]?.toString()
                                            ?: (point["contactPerson"] as? Map<*, *>)?.get("name")
                                                ?.toString()
                                        contactName?.contains("Sender", ignoreCase = true) == true
                                    } catch (e: Exception) {
                                        Log.e("Firestore", "Error checking contact person", e)
                                        false
                                    }
                                }?.let { senderPoint ->
                                    // Tracking URL
                                    senderPoint["trackingUrl"]?.toString()?.let { url ->
                                        trackingUrlTextView.text =
                                            "Tracking: ${url.takeIf { it.isNotBlank() } ?: "Not available"}"
                                    } ?: run {
                                        trackingUrlTextView.text = "Tracking: Not available"
                                    }

                                    // Format and display times
                                    fun formatDateTime(raw: Any?): String {
                                        return try {
                                            raw?.toString()
                                                ?.replace("T", " ")
                                                ?.substringBefore("+")
                                                ?: "Not specified"
                                        } catch (e: Exception) {
                                            Log.e("Firestore", "Error formatting date", e)
                                            "Invalid date"
                                        }
                                    }

                                    startTimeSender.text =
                                        "Pickup: ${formatDateTime(senderPoint["requiredStartDatetime"])}"
                                    endTimeSender.text =
                                        "Delivery: ${formatDateTime(senderPoint["requiredFinishDatetime"])}"
                                } ?: run {
                                    subStatus.text =
                                        "No sender point found in ${points.size} points"
                                    Log.w(
                                        "Firestore",
                                        "Sender point not found. Points: ${points.map { it.keys }}"
                                    )
                                }
                            }
                            .addOnFailureListener { e ->
                                subStatus.text = "Failed to load points"
                                Log.e("Firestore", "Error getting points subcollection", e)
                            }
                    } else {
                        subStatus.text = "No active order found"
                        Log.d("Firestore", "No document found with key: $uniqueKey")
                    }
                } catch (e: Exception) {
                    subStatus.text = "Error processing data"
                    Log.e("Firestore", "Document processing error", e)
                }
            }
            .addOnFailureListener { exception ->
                subStatus.text = when {
                    exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission denied"

                    exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.NOT_FOUND ->
                        "Data not found"

                    else -> "Connection failed: ${exception.localizedMessage}"
                }
                Log.e("Firestore", "Query failed", exception)
            }
    }
}

