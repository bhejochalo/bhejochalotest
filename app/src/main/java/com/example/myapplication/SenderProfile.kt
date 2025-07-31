package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

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
        setupEditItemButton() // Add this line
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
            statusContent.visibility = View.VISIBLE
            senderContent.visibility = View.GONE
            underlineStatus.visibility = View.VISIBLE
            underlineSender.visibility = View.GONE
            tabStatus.setTextColor(getColor(R.color.orange_primary))
            tabSender.setTextColor(getColor(R.color.gray_secondary))
        }

        tabSenderContainer.setOnClickListener {
            statusContent.visibility = View.GONE
            senderContent.visibility = View.VISIBLE
            underlineStatus.visibility = View.GONE
            underlineSender.visibility = View.VISIBLE
            tabStatus.setTextColor(getColor(R.color.gray_secondary))
            tabSender.setTextColor(getColor(R.color.orange_primary))
            loadTravelerData()
        }
    }

    private fun setupEditButtons() {
        // From Address Edit Button
        findViewById<Button>(R.id.btnEditFromAddress).setOnClickListener {
            showEditAddressDialog("From") { newAddress ->
                findViewById<TextView>(R.id.tvFromAddress).text = newAddress
                updateAddressInFirestore("fromAddress", newAddress)
            }
        }

        // To Address Edit Button
        findViewById<Button>(R.id.btnEditToAddress).setOnClickListener {
            showEditAddressDialog("To") { newAddress ->
                findViewById<TextView>(R.id.tvToAddress).text = newAddress
                updateAddressInFirestore("toAddress", newAddress)
            }
        }
    }

    private fun setupEditItemButton() {
        findViewById<Button>(R.id.btnEditItemDetails).setOnClickListener {
            showEditItemDialog()
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

    private fun showEditItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_item_details, null)

        // Get current values
        val currentName = findViewById<TextView>(R.id.tvItemName).text.toString().replace("Item: ", "")
        val currentWeight = findViewById<TextView>(R.id.tvItemWeight).text.toString().replace("Weight: ", "")
        val currentInstructions = findViewById<TextView>(R.id.tvItemInstructions).text.toString().replace("Instructions: ", "")
        val currentDeliveryOption = findViewById<TextView>(R.id.tvDeliveryOption).text.toString().replace("Delivery Option: ", "")

        val etItemName = dialogView.findViewById<EditText>(R.id.etItemName)
        val etKg = dialogView.findViewById<EditText>(R.id.etKg)
        val etGram = dialogView.findViewById<EditText>(R.id.etGram)
        val etInstructions = dialogView.findViewById<EditText>(R.id.etInstructions)
        val rgDeliveryOption = dialogView.findViewById<RadioGroup>(R.id.rgDeliveryOption)

        // Set current values
        etItemName.setText(currentName)
        etInstructions.setText(currentInstructions)

        // Parse weight
        val weightParts = currentWeight.split(" ")
        if (weightParts.size >= 2) {
            val kg = weightParts[0].toIntOrNull() ?: 0
            etKg.setText(kg.toString())
        }
        if (weightParts.size >= 4) {
            val gram = weightParts[2].toIntOrNull() ?: 0
            etGram.setText(gram.toString())
        }

        // Set delivery option
        when (currentDeliveryOption) {
            "Self Pickup" -> rgDeliveryOption.check(R.id.rbSelfPickup)
            "Auto Pickup" -> rgDeliveryOption.check(R.id.rbAutoPickup)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Item Details")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etItemName.text.toString().trim()
                val kg = etKg.text.toString().toIntOrNull() ?: 0
                val gram = etGram.text.toString().toIntOrNull() ?: 0
                val instructions = etInstructions.text.toString().trim()

                val deliveryOption = when (rgDeliveryOption.checkedRadioButtonId) {
                    R.id.rbSelfPickup -> "Self Pickup"
                    R.id.rbAutoPickup -> "Auto Pickup"
                    else -> "Self Pickup"
                }

                // Update UI
                findViewById<TextView>(R.id.tvItemName).text = "Item: $newName"
                findViewById<TextView>(R.id.tvItemWeight).text = "Weight: $kg kg $gram g"
                findViewById<TextView>(R.id.tvItemInstructions).text = "Instructions: $instructions"
                findViewById<TextView>(R.id.tvDeliveryOption).text = "Delivery Option: $deliveryOption"

                // Update Firestore
                updateItemDetailsInFirestore(newName, kg, gram, instructions, deliveryOption)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateItemDetailsInFirestore(name: String, kg: Int, gram: Int, instructions: String, deliveryOption: String) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return
        val totalWeight = (kg * 1000) + gram
        val price = when (deliveryOption) {
            "Self Pickup" -> 750
            "Auto Pickup" -> 1500
            else -> 750
        }

        val updates = hashMapOf<String, Any>(
            "itemDetails.itemName" to name,
            "itemDetails.weightKg" to kg,
            "itemDetails.weightGram" to gram,
            "itemDetails.totalWeight" to totalWeight,
            "itemDetails.instructions" to instructions,
            "deliveryOptionPrice" to price
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
                            Toast.makeText(this, "Item details updated", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update item details", Toast.LENGTH_SHORT).show()
                            Log.e("SenderProfile", "Error updating item details", e)
                        }
                }
            }
    }

    private fun updateAddressInFirestore(addressType: String, newAddress: String) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return
        val updates = hashMapOf<String, Any>(
            "$addressType.fullAddress" to newAddress
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
        loadAddressData()
        loadStatusData()
        loadItemDetails() // Add this line
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

    private fun loadItemDetails() {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val itemDetails = document.get("itemDetails") as? Map<*, *>

                    itemDetails?.let {
                        val name = it["itemName"] as? String ?: "N/A"
                        val kg = (it["weightKg"] as? Number)?.toInt() ?: 0
                        val gram = (it["weightGram"] as? Number)?.toInt() ?: 0
                        val instructions = it["instructions"] as? String ?: "N/A"

                        val price = document.getLong("deliveryOptionPrice")?.toInt() ?: 750
                        val deliveryOption = if (price == 750) "Self Pickup" else "Auto Pickup"

                        findViewById<TextView>(R.id.tvItemName).text = "Item: $name"
                        findViewById<TextView>(R.id.tvItemWeight).text = "Weight: $kg kg $gram g"
                        findViewById<TextView>(R.id.tvItemInstructions).text = "Instructions: $instructions"
                        findViewById<TextView>(R.id.tvDeliveryOption).text = "Delivery Option: $deliveryOption"
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading item details", e)
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
        val uniqueKey = "TX_902e81a15155a38d18898c3e9143dd4b1358ef3629008e1e2a74459df9ad187e"

        Log.d("SenderProfile", "Loading traveler data with uniqueKey: $uniqueKey")

        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val travelerDoc = querySnapshot.documents[0]
                    Log.d("SenderProfile", "Traveler data: ${travelerDoc.data}")

                    // Debug log addresses before updating UI
                    val fromAddress = travelerDoc.get("fromAddress") as? Map<String, Any>
                    val toAddress = travelerDoc.get("toAddress") as? Map<String, Any>
                    Log.d("AddressDebug", "From Address: $fromAddress")
                    Log.d("AddressDebug", "To Address: $toAddress")

                    updateFlightUI(travelerDoc)
                    updateTravelerUI(travelerDoc)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading traveler details", e)
            }
    }

    private fun updateFlightUI(travelerDoc: DocumentSnapshot) {
        try {
            // 1. First verify we have the correct document
            Log.d("FlightDebug", "Document ID: ${travelerDoc.id}")
            Log.d("FlightDebug", "Document data: ${travelerDoc.data}")

            // 2. Extract address data with proper null checks
            val fromAddress = (travelerDoc.get("fromAddress") as? Map<*, *>)?.let {
                Pair(
                    it["city"] as? String ?: "N/A",
                    it["state"] as? String ?: "N/A"
                )
            } ?: Pair("N/A", "N/A")

            val toAddress = (travelerDoc.get("toAddress") as? Map<*, *>)?.let {
                Pair(
                    it["city"] as? String ?: "N/A",
                    it["state"] as? String ?: "N/A"
                )
            } ?: Pair("N/A", "N/A")

            // 3. Log the extracted values
            Log.d("FlightDebug", "From: ${fromAddress.first}, ${fromAddress.second}")
            Log.d("FlightDebug", "To: ${toAddress.first}, ${toAddress.second}")

            // 4. Update UI on main thread
            runOnUiThread {
                findViewById<TextView>(R.id.tvFromCity).text = "${fromAddress.first}, ${fromAddress.second}"
                findViewById<TextView>(R.id.tvToCity).text = "${toAddress.first}, ${toAddress.second}"

                // Verify TextView IDs
                Log.d("ViewDebug", "tvFromCity found: ${findViewById<TextView>(R.id.tvFromCity) != null}")
                Log.d("ViewDebug", "tvToCity found: ${findViewById<TextView>(R.id.tvToCity) != null}")
            }

            // Rest of your flight UI updates...
            val airline = travelerDoc.getString("airline") ?: "N/A"
            val pnr = travelerDoc.getString("pnr") ?: "N/A"
            val leavingDate = travelerDoc.getString("leavingDate") ?: "N/A"
            val leavingTime = travelerDoc.getString("leavingTime") ?: "N/A"

            runOnUiThread {
                findViewById<TextView>(R.id.tvFlightNumber).text = "PNR: $pnr"
                findViewById<TextView>(R.id.tvFlightStatus).text = "Airline: $airline"
                findViewById<TextView>(R.id.tvFromTime).text = "Leaving at: $leavingDate $leavingTime"
            }

        } catch (e: Exception) {
            Log.e("SenderProfile", "Error updating flight UI", e)
        }
    }

    private fun updateTravelerUI(travelerDoc: DocumentSnapshot) {
        try {
            // Update basic traveler info
            findViewById<TextView>(R.id.tvTravelerName)?.text = travelerDoc.getString("lastName") ?: "N/A"
            findViewById<TextView>(R.id.tvTravelerAirline)?.text = travelerDoc.getString("airline") ?: "N/A"
            findViewById<TextView>(R.id.tvTravelerPnr)?.text = travelerDoc.getString("pnr") ?: "N/A"
            findViewById<TextView>(R.id.tvTravelerFlightNumber)?.text = travelerDoc.getString("flightNumber") ?: "N/A"
            findViewById<TextView>(R.id.tvTravelerDeparture)?.text =
                travelerDoc.getString("departureTime") ?: travelerDoc.getString("leavingTime") ?: "N/A"
            findViewById<TextView>(R.id.tvTravelerArrival)?.text = travelerDoc.getString("arrivalTime") ?: "N/A"

            // Update destination from 'toAddress.city'
            val toAddress = travelerDoc.get("toAddress") as? Map<String, Any>
            val destinationCity = toAddress?.get("city") as? String ?: "N/A"
            findViewById<TextView>(R.id.tvTravelerDestination)?.text = destinationCity

            // Update weight allowance from 'weightUpto'
            val weightUpto = travelerDoc.getString("weightUpto") ?: 0
            findViewById<TextView>(R.id.tvTravelerWeight)?.text = "$weightUpto kg"

        } catch (e: Exception) {
            Log.e("SenderProfile", "Error updating traveler UI", e)
            Toast.makeText(this, "Error displaying traveler details", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showCompleteTravelerDetails() {
        if (uniqueKey.isNullOrEmpty()) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_complete_traveler_details, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Complete Traveler Details")
            .setPositiveButton("Close", null)
            .create()

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