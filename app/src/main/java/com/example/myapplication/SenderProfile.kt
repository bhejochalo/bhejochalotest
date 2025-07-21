package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class SenderProfile : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences
    private var uniqueKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_profile)

        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        uniqueKey = sharedPref.getString("UNIQUE_KEY", null) ?: intent.getStringExtra("UNIQUE_KEY")

        // Initialize UI components
        setupTabSwitching()
        setupEditButtons()
        loadInitialData()
    }

    private fun setupTabSwitching() {
        val tabStatusContainer = findViewById<LinearLayout>(R.id.tabStatusContainer)
        val tabSenderContainer = findViewById<LinearLayout>(R.id.tabSenderContainer)
        val statusContent = findViewById<LinearLayout>(R.id.statusContent)
        val senderContent = findViewById<NestedScrollView>(R.id.senderContent)
        val underlineStatus = findViewById<View>(R.id.underlineStatus)
        val underlineSender = findViewById<View>(R.id.underlineSender)
        val tabStatus = findViewById<TextView>(R.id.tabStatus)
        val tabSender = findViewById<TextView>(R.id.tabSender)

        tabStatusContainer.setOnClickListener {
            // Switch to Status tab
            statusContent.visibility = View.VISIBLE
            senderContent.visibility = View.GONE
            underlineStatus.visibility = View.VISIBLE
            underlineSender.visibility = View.GONE
            tabStatus.setTextColor(getColor(R.color.orange_primary))
            tabSender.setTextColor(getColor(R.color.gray_secondary))
        }

        tabSenderContainer.setOnClickListener {
            // Switch to Traveler tab
            statusContent.visibility = View.GONE
            senderContent.visibility = View.VISIBLE
            underlineStatus.visibility = View.GONE
            underlineSender.visibility = View.VISIBLE
            tabStatus.setTextColor(getColor(R.color.gray_secondary))
            tabSender.setTextColor(getColor(R.color.orange_primary))

            // Load traveler data when tab is clicked
            loadTravelerData()
        }

        // Set up "View Complete Details" button
        findViewById<Button>(R.id.btnViewFullDetails).setOnClickListener {
            showCompleteTravelerDetails()
        }
    }

    private fun setupEditButtons() {
        // From Address Edit Button
        findViewById<Button>(R.id.btnEditFromAddress).setOnClickListener {
            showEditAddressDialog("From") { newAddress ->
                findViewById<TextView>(R.id.tvFromAddress).text = newAddress
                // Here you would also update the address in Firestore
                updateAddressInFirestore("fromAddress", newAddress)
            }
        }

        // To Address Edit Button
        findViewById<Button>(R.id.btnEditToAddress).setOnClickListener {
            showEditAddressDialog("To") { newAddress ->
                findViewById<TextView>(R.id.tvToAddress).text = newAddress
                // Here you would also update the address in Firestore
                updateAddressInFirestore("toAddress", newAddress)
            }
        }
    }

    private fun showEditAddressDialog(title: String, onSave: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_address, null)
        val etStreet = dialogView.findViewById<EditText>(R.id.etStreet)
        val etHouseNumber = dialogView.findViewById<EditText>(R.id.etHouseNumber)
        val etArea = dialogView.findViewById<EditText>(R.id.etArea)
        val etCity = dialogView.findViewById<EditText>(R.id.etCity)
        val etState = dialogView.findViewById<EditText>(R.id.etState)
        val etPostalCode = dialogView.findViewById<EditText>(R.id.etPostalCode)

        AlertDialog.Builder(this)
            .setTitle("Edit $title Address")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newAddress = buildString {
                    append(etStreet.text.toString().trim())
                    append(", ")
                    append(etHouseNumber.text.toString().trim())
                    append(", ")
                    append(etArea.text.toString().trim())
                    append(", ")
                    append(etCity.text.toString().trim())
                    append(", ")
                    append(etState.text.toString().trim())
                    append(" - ")
                    append(etPostalCode.text.toString().trim())
                }
                onSave(newAddress)
                Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAddressInFirestore(addressType: String, newAddress: String) {
        // Get current user ID or phone number from SharedPreferences
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        // Update the address in Firestore
        val updates = hashMapOf<String, Any>(
            "$addressType.fullAddress" to newAddress
            // Add other address fields if needed
        )

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    document.reference.update(updates)
                        .addOnSuccessListener {
                            Log.d("SenderProfile", "Address updated successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("SenderProfile", "Error updating address", e)
                        }
                }
            }
    }

    private fun loadInitialData() {
        // Load address data
        loadAddressData()

        // Load status data by default
        loadStatusData()
    }

    private fun loadAddressData() {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val fromAddress = document.getString("fromAddress.fullAddress") ?: ""
                    val toAddress = document.getString("toAddress.fullAddress") ?: ""

                    findViewById<TextView>(R.id.tvFromAddress).text = fromAddress
                    findViewById<TextView>(R.id.tvToAddress).text = toAddress
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading address data", e)
            }
    }

    private fun loadStatusData() {
        if (uniqueKey.isNullOrEmpty()) return

        db.collection("borzo_orders")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val status = document.getString("order.status") ?: "N/A"
                    val trackingUrl = document.getString("order.tracking_url") ?: "N/A"
                    val startTime = document.getString("order.required_start_datetime") ?: "N/A"
                    val endTime = document.getString("order.required_finish_datetime") ?: "N/A"

                    findViewById<TextView>(R.id.subStatus).text = status
                    findViewById<TextView>(R.id.trackingUrl).text = "Tracking: $trackingUrl"
                    findViewById<TextView>(R.id.startTimeSender).text = "Pickup: $startTime"
                    findViewById<TextView>(R.id.endTimeSender).text = "Delivery: $endTime"
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading status data", e)
            }
    }

    private fun loadTravelerData() {
        if (uniqueKey.isNullOrEmpty()) {
            Toast.makeText(this, "No traveler information available", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val travelerDoc = querySnapshot.documents[0]
                    updateTravelerUI(travelerDoc)
                } else {
                    Toast.makeText(this, "Traveler details not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading traveler details: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("SenderProfile", "Error loading traveler", e)
            }
    }

    private fun updateTravelerUI(travelerDoc: DocumentSnapshot) {
        findViewById<TextView>(R.id.tvTravelerName).text = travelerDoc.getString("lastName") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerAirline).text = travelerDoc.getString("airline") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerPnr).text = travelerDoc.getString("pnr") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerFlightNumber).text = travelerDoc.getString("flightNumber") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerDeparture).text = travelerDoc.getString("departureTime") ?: travelerDoc.getString("leavingTime") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerArrival).text = travelerDoc.getString("arrivalTime") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerDestination).text = travelerDoc.getString("destination") ?: "N/A"
        findViewById<TextView>(R.id.tvTravelerWeight).text = "${travelerDoc.getLong("weightUpto") ?: 0} kg"
    }

    private fun showCompleteTravelerDetails() {
        if (uniqueKey.isNullOrEmpty()) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_complete_traveler_details, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Complete Traveler Details")
            .setPositiveButton("Close", null)
            .create()

        // Load data into dialog
        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val travelerDoc = querySnapshot.documents[0]
                    dialogView.findViewById<TextView>(R.id.tvFullName).text = travelerDoc.getString("lastName") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullAirline).text = travelerDoc.getString("airline") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullPnr).text = travelerDoc.getString("pnr") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullFlight).text = travelerDoc.getString("flightNumber") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullDeparture).text = travelerDoc.getString("departureTime") ?: travelerDoc.getString("leavingTime") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullArrival).text = travelerDoc.getString("arrivalTime") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullDestination).text = travelerDoc.getString("destination") ?: "N/A"
                    dialogView.findViewById<TextView>(R.id.tvFullWeight).text = "${travelerDoc.getLong("weightUpto") ?: 0} kg"
                    dialogView.findViewById<TextView>(R.id.tvFullPhone).text = travelerDoc.getString("phoneNumber") ?: "N/A"
                }
            }

        dialog.show()
    }
}