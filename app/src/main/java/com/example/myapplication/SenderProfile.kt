package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class SenderProfile : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences
    private var uniqueKey: String? = null
    private var senderDoc: DocumentSnapshot? = null
    private var travelerDoc: DocumentSnapshot? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_profile)

        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        //uniqueKey = sharedPref.getString("uniqueKey", null) ?: intent.getStringExtra("uniqueKey")
        uniqueKey = "asdf"

        setupTabSwitching()
        setupEditButtons()
        setupEditItemButton()

        loadSenderData {
            loadAddressData()
            loadItemDetails()
        }

        loadTravelerDataOnce {
            // load travelerDoc if found
            travelerDoc?.let {
                updateFlightUI(it) // This will now load flight data on activity creation
                updateTravelerUI(it)
                checkAndUpdateBookingStatus(it)
                loadStatusData()
                // set up listeners for mile controls
                setupMileControls()
            }
        }

        findViewById<Button>(R.id.btnBookOtherTravelers).setOnClickListener {
            navigateToSenderDashboard()
        }

        // Wire up OTP verify button (present in layout)
        findViewById<Button?>(R.id.btnVerifyOtp)?.setOnClickListener {
            val entered = findViewById<EditText>(R.id.etOtpInput).text.toString().trim()
            if (entered.isEmpty()) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyLastMileOtp(entered)
        }
    }

    private fun navigateToSenderDashboard() {
        val intent = Intent(this, SenderDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish() // Close current activity
    }

    private fun loadSenderData(onLoaded: () -> Unit) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    senderDoc = querySnapshot.documents[0]
                    onLoaded()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading sender data", e)
            }
    }

    private fun loadTravelerDataOnce(onLoaded: () -> Unit) {
        if (uniqueKey.isNullOrEmpty()) {
            Log.w("SenderProfile", "uniqueKey is null or empty, skipping traveler load")
            return
        }

        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    travelerDoc = querySnapshot.documents[0]
                    onLoaded()
                } else {
                    Log.w("SenderProfile", "No traveler found for uniqueKey=$uniqueKey")
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading traveler data", e)
            }
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

    private fun checkAndUpdateBookingStatus(travelerDoc: DocumentSnapshot) {
        try {
            val status = travelerDoc.getString("status") ?: ""
            runOnUiThread {
                findViewById<TextView>(R.id.subStatus).text = status
                val flightNumber = travelerDoc.getString("FlightNumber") ?: "N/A"
                val trackingUrl = "https://www.flightaware.com/live/flight/$flightNumber"
                val bookOtherBtn = findViewById<Button>(R.id.btnBookOtherTravelers)
                findViewById<TextView>(R.id.trackingUrl).text = "Flight Tracking: $trackingUrl"
                bookOtherBtn.visibility = if (status == "Rejected By Traveler") View.VISIBLE else View.GONE
                reorganizeItemDetailsLayout(status)
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error checking booking status", e)
        }
    }

    private fun reorganizeItemDetailsLayout(status: String) {
        val itemDetailsCard = findViewById<CardView>(R.id.itemDetailsCard)
        val flightInfoCard = findViewById<CardView>(R.id.flightInfoCard)
        val addressCard = findViewById<CardView>(R.id.addressCard)
        val statusCard = findViewById<CardView>(R.id.statusCard)

        if (status == "Request Accepted By Traveler") {
            // Flight Info first, then Item Details, then Address, then Status
            val flightParams = flightInfoCard.layoutParams as ConstraintLayout.LayoutParams
            flightParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            flightParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
            flightInfoCard.layoutParams = flightParams

            val itemParams = itemDetailsCard.layoutParams as ConstraintLayout.LayoutParams
            itemParams.topToBottom = R.id.flightInfoCard
            itemParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
            itemDetailsCard.layoutParams = itemParams

            val addressParams = addressCard.layoutParams as ConstraintLayout.LayoutParams
            addressParams.topToBottom = R.id.itemDetailsCard
            addressCard.layoutParams = addressParams

            val statusParams = statusCard.layoutParams as ConstraintLayout.LayoutParams
            statusParams.topToBottom = R.id.addressCard
            statusCard.layoutParams = statusParams

        } else {
            // Flight Info first, then Address, then Status, then Item Details at bottom
            val flightParams = flightInfoCard.layoutParams as ConstraintLayout.LayoutParams
            flightParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            flightParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
            flightInfoCard.layoutParams = flightParams

            val addressParams = addressCard.layoutParams as ConstraintLayout.LayoutParams
            addressParams.topToBottom = R.id.flightInfoCard
            addressCard.layoutParams = addressParams

            val statusParams = statusCard.layoutParams as ConstraintLayout.LayoutParams
            statusParams.topToBottom = R.id.addressCard
            statusCard.layoutParams = statusParams

            val itemParams = itemDetailsCard.layoutParams as ConstraintLayout.LayoutParams
            itemParams.topToBottom = R.id.statusCard
            itemParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            itemDetailsCard.layoutParams = itemParams
        }

        (itemDetailsCard.parent as? ViewGroup)?.requestLayout()
    }

    private fun setupEditButtons() {
        // From Address Edit Button
        findViewById<Button>(R.id.btnEditFromAddress).setOnClickListener {
            showEditAddressDialog("From") { newAddress ->
                findViewById<TextView>(R.id.tvFromAddress).text = newAddress
            }
        }

        // To Address Edit Button
        findViewById<Button>(R.id.btnEditToAddress).setOnClickListener {
            showEditAddressDialog("To") { newAddress ->
                findViewById<TextView>(R.id.tvToAddress).text = newAddress
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

        val currentAddressField = if (title == "From") "fromAddress" else "toAddress"
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val address = document.get(currentAddressField) as? Map<String, Any>

                    etStreet.setText(address?.get("street") as? String ?: "")
                    etHouseNumber.setText(address?.get("houseNumber") as? String ?: "")
                    etArea.setText(address?.get("area") as? String ?: "")
                    etCity.setText(address?.get("city") as? String ?: "")
                    etState.setText(address?.get("state") as? String ?: "")
                    etPostalCode.setText(address?.get("postalCode") as? String ?: "")
                }
            }

        AlertDialog.Builder(this)
            .setTitle("Edit $title Address")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val street = etStreet.text.toString().trim()
                val houseNumber = etHouseNumber.text.toString().trim()
                val area = etArea.text.toString().trim()
                val city = etCity.text.toString().trim()
                val state = etState.text.toString().trim()
                val postalCode = etPostalCode.text.toString().trim()

                val newAddress = buildString {
                    append(street)
                    append(", ")
                    append(houseNumber)
                    append(", ")
                    append(area)
                    append(", ")
                    append(city)
                    append(", ")
                    append(state)
                    append(" - ")
                    append(postalCode)
                }

                updateCompleteAddressInFirestore(currentAddressField, street, houseNumber, area, city, state, postalCode)

                onSave(newAddress)
                Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCompleteAddressInFirestore(
        addressType: String,
        street: String,
        houseNumber: String,
        area: String,
        city: String,
        state: String,
        postalCode: String
    ) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        val addressMap = hashMapOf(
            "street" to street,
            "houseNumber" to houseNumber,
            "area" to area,
            "city" to city,
            "state" to state,
            "postalCode" to postalCode,
            "fullAddress" to "$street, $houseNumber, $area, $city, $state - $postalCode"
        )

        val updates = hashMapOf<String, Any>(
            addressType to addressMap
        )

        db.collection("Sender").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("SenderProfile", "Complete address updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error updating complete address", e)
            }
    }

    private fun showEditItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_item_details, null)

        val currentName = findViewById<TextView>(R.id.tvItemName).text.toString().replace("Item: ", "")
        val currentWeight = findViewById<TextView>(R.id.tvItemWeight).text.toString().replace("Weight: ", "")
        val currentInstructions = findViewById<TextView>(R.id.tvItemInstructions).text.toString().replace("Instructions: ", "")
        val currentDeliveryOption = findViewById<TextView>(R.id.tvDeliveryOption).text.toString().replace("Delivery Option: ", "")

        val etItemName = dialogView.findViewById<EditText>(R.id.etItemName)
        val etKg = dialogView.findViewById<EditText>(R.id.etKg)
        val etGram = dialogView.findViewById<EditText>(R.id.etGram)
        val etInstructions = dialogView.findViewById<EditText>(R.id.etInstructions)
        val rgDeliveryOption = dialogView.findViewById<RadioGroup>(R.id.rgDeliveryOption)

        etItemName.setText(currentName)
        etInstructions.setText(currentInstructions)

        val weightParts = currentWeight.split(" ")
        if (weightParts.size >= 2) {
            val kg = weightParts[0].toIntOrNull() ?: 0
            etKg.setText(kg.toString())
        }
        if (weightParts.size >= 4) {
            val gram = weightParts[2].toIntOrNull() ?: 0
            etGram.setText(gram.toString())
        }

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

                findViewById<TextView>(R.id.tvItemName).text = "Item: $newName"
                findViewById<TextView>(R.id.tvItemWeight).text = "Weight: $kg kg $gram g"
                findViewById<TextView>(R.id.tvItemInstructions).text = "Instructions: $instructions"
                findViewById<TextView>(R.id.tvDeliveryOption).text = "Delivery Option: $deliveryOption"

                updateItemDetailsInFirestore(newName, kg, gram, instructions, deliveryOption)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateItemDetailsInFirestore(
        name: String,
        kg: Int,
        gram: Int,
        instructions: String,
        deliveryOption: String
    ) {
        val phoneNumber = sharedPref.getString("PHONE_NUMBER", null) ?: return

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

        db.collection("Sender").document(phoneNumber)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Item details updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update item details", Toast.LENGTH_SHORT).show()
                Log.e("SenderProfile", "Error updating item details", e)
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
        loadItemDetails()
        if (uniqueKey != null) {
            loadTravelerDataForStatus()
        }
    }

    private fun loadTravelerDataForStatus() {
        if (uniqueKey.isNullOrEmpty()) return

        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val travelerDocLocal = querySnapshot.documents[0]
                    checkAndUpdateBookingStatus(travelerDocLocal)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading traveler status", e)
            }
    }

    private fun loadAddressData() {
        val doc = senderDoc ?: return

        val fromAddress = doc.getString("fromAddress.fullAddress") ?: ""
        val toAddress = doc.getString("toAddress.fullAddress") ?: ""

        val firstMileStatus = doc.getString("FirstMileStatus") ?: "Not Started"
        val secondMileStatus = doc.getString("SecondMileStatus") ?: "Not Started"
        val lastMileStatus = doc.getString("LastMileStatus") ?: "Not Started"

        findViewById<TextView>(R.id.fileMileMainStatus)?.text = "✓ 1st Stage - $firstMileStatus"
        findViewById<TextView>(R.id.tvFromAddress).text = fromAddress
        findViewById<TextView>(R.id.tvToAddress).text = toAddress

        if (firstMileStatus == "Completed") {
            findViewById<TextView>(R.id.secondmilesender)?.text = "✓ 2nd stage - Started"
        }
        if (secondMileStatus == "Completed") {
            findViewById<TextView>(R.id.secondmilesender)?.text = "✓ 2nd stage - Completed"
            findViewById<TextView>(R.id.lastmilestatussender)?.text = "✓ 3rd stage - Started"
        }
    }

    private fun loadItemDetails() {
        val doc = senderDoc ?: return

        val itemDetails = doc.get("itemDetails") as? Map<*, *>
        itemDetails?.let {
            val name = it["itemName"] as? String ?: "N/A"
            val kg = (it["weightKg"] as? Number)?.toInt() ?: 0
            val gram = (it["weightGram"] as? Number)?.toInt() ?: 0
            val instructions = it["instructions"] as? String ?: "N/A"

            val price = doc.getLong("deliveryOptionPrice")?.toInt() ?: 750
            val deliveryOption = if (price == 750) "Self Pickup" else "Auto Pickup"

            findViewById<TextView>(R.id.tvItemName).text = "Item: $name"
            findViewById<TextView>(R.id.tvItemWeight).text = "Weight: $kg kg $gram g"
            findViewById<TextView>(R.id.tvItemInstructions).text = "Instructions: $instructions"
            findViewById<TextView>(R.id.tvDeliveryOption).text = "Delivery Option: $deliveryOption"
        }
    }

    private fun loadStatusData() {
        if (uniqueKey.isNullOrEmpty()) return

        db.collection("borzo_orders")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { orderQuery ->
                if (!orderQuery.isEmpty) {
                    val document = orderQuery.documents[0]
                    val status = document.getString("order.status") ?: "N/A"
                    val startTime = document.getString("order.required_start_datetime") ?: "N/A"
                    val endTime = document.getString("order.required_finish_datetime") ?: "N/A"

                    findViewById<TextView>(R.id.subStatus).text = status

                    val flightNumber = travelerDoc?.getString("FlightNumber") ?: "N/A"
                    val trackingUrl = "https://www.flightaware.com/live/flight/$flightNumber"
                    findViewById<TextView>(R.id.trackingUrl).text = "Flight Tracking: $trackingUrl"
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading status data", e)
            }
    }

    private fun loadTravelerData() {
        if (uniqueKey.isNullOrEmpty()) {
            Log.w("SenderProfile", "loadTravelerData: uniqueKey is null — using fallback 'asdf' in your original code is unsafe")
            return
        }

        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val travelerDocLocal = querySnapshot.documents[0]
                    travelerDoc = travelerDocLocal
                    checkAndUpdateBookingStatus(travelerDocLocal)
                    updateFlightUI(travelerDocLocal)
                    updateTravelerUI(travelerDocLocal)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading traveler details", e)
            }
    }

    private fun updateFlightUI(travelerDoc: DocumentSnapshot) {
        try {
            val fromAddress = travelerDoc.get("fromAddress") as? Map<String, Any>
            val toAddress = travelerDoc.get("toAddress") as? Map<String, Any>

            val fromCity = fromAddress?.get("city") as? String ?: "N/A"
            val toCity = toAddress?.get("city") as? String ?: "N/A"

            val airline = travelerDoc.getString("airline") ?: "N/A"
            val flightNumber = travelerDoc.getString("FlightNumber") ?: "N/A"

            val departureTimeStr = travelerDoc.getString("departureTime") ?: "N/A"
            val formattedDepartureTime = formatDateTime(departureTimeStr)
            val formattedArrivalTime = calculateArrivalTime(departureTimeStr)

            val status = travelerDoc.getString("status") ?: "N/A"

            runOnUiThread {
                findViewById<TextView>(R.id.tvFromCity).text = fromCity
                findViewById<TextView>(R.id.tvToCity).text = toCity
                findViewById<TextView>(R.id.tvFlightStatus).text = "Airline: $airline"
                findViewById<TextView>(R.id.tvFromTime).text = "Departure: $formattedDepartureTime"
                findViewById<TextView>(R.id.tvToTime).text = "Arrival: $formattedArrivalTime"
                findViewById<TextView>(R.id.tvFlightNumber).text = "Flight: $flightNumber"

                val statusTextView = findViewById<TextView>(R.id.subStatus)
                statusTextView.text = "Flight Status: $status"

                when (status.lowercase()) {
                    "scheduled", "ontime", "request accepted by traveler" -> statusTextView.setTextColor(Color.GREEN)
                    "delayed", "in progress" -> statusTextView.setTextColor(Color.YELLOW)
                    "cancelled", "rejected" -> statusTextView.setTextColor(Color.RED)
                    else -> statusTextView.setTextColor(Color.GRAY)
                }

                updateTravelerUI(travelerDoc)
            }

        } catch (e: Exception) {
            Log.e("SenderProfile", "Error updating flight UI", e)
        }
    }

    private fun formatDateTime(dateTimeStr: String): String {
        return try {
            if (dateTimeStr.contains("T")) {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateTimeStr)
                outputFormat.format(date)
            } else {
                dateTimeStr
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error formatting date", e)
            dateTimeStr
        }
    }

    private fun calculateArrivalTime(departureTimeStr: String): String {
        return try {
            if (departureTimeStr.contains("T")) {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(departureTimeStr)
                val arrivalDate = Date(date.time + (2 * 60 * 60 * 1000))
                outputFormat.format(arrivalDate)
            } else {
                "N/A"
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error calculating arrival time", e)
            "N/A"
        }
    }

    private fun updateTravelerUI(travelerDoc: DocumentSnapshot) {
        try {
            val lastName = travelerDoc.getString("lastName") ?: "N/A"
            val airline = travelerDoc.getString("airline") ?: "N/A"
            val flightNumber = travelerDoc.getString("flightNumber") ?: "N/A"
            val departureTime = travelerDoc.getString("departureTime") ?: "N/A"
            val arrivalTime = travelerDoc.getString("arrivalTime") ?: "N/A"
            val weightUpto = travelerDoc.getString("weightUpto") ?: "0"
            val destination = travelerDoc.getString("toPlace") ?: "N/A"

            runOnUiThread {
                findViewById<TextView>(R.id.tvTravelerName)?.text = lastName
                findViewById<TextView>(R.id.tvTravelerAirline)?.text = airline
                findViewById<TextView>(R.id.tvTravelerFlightNumber)?.text = flightNumber
                findViewById<TextView>(R.id.tvTravelerDeparture)?.text = departureTime
                findViewById<TextView>(R.id.tvTravelerArrival)?.text = arrivalTime
                findViewById<TextView>(R.id.tvTravelerDestination)?.text = destination
                findViewById<TextView>(R.id.tvTravelerWeight)?.text = "$weightUpto kg"
            }

        } catch (e: Exception) {
            Log.e("SenderProfile", "Error updating traveler UI", e)
            Toast.makeText(this, "Error displaying traveler details", Toast.LENGTH_SHORT).show()
        }
    }

    // -----------------------
    // First/Second/Last mile logic (added)
    // -----------------------

    private fun setupMileControls() {
        // Wire up click listeners for the textviews representing mile stages
        val firstTv = findViewById<TextView>(R.id.firstmilesender)
        val secondTv = findViewById<TextView>(R.id.secondmilesender)
        val lastTv = findViewById<TextView>(R.id.lastmilestatussender)

        // Refresh local travelerDoc from Firestore when tab shown for latest values
        firstTv.setOnClickListener {
            refreshTravelerDoc { doc ->
                val currentFirst = doc?.getString("FirstMileStatus") ?: "Not Started"
                when (currentFirst) {
                    "Not Started" -> {
                        // Start first mile -> generate OTP & set In Progress
                        initiateFirstMile()
                    }
                    "In Progress" -> {
                        // Mark first mile completed
                        completeFirstMile()
                    }
                    "Completed" -> {
                        Toast.makeText(this, "First mile already completed", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "First mile: $currentFirst", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        secondTv.setOnClickListener {
            refreshTravelerDoc { doc ->
                val first = doc?.getString("FirstMileStatus") ?: "Not Started"
                val second = doc?.getString("SecondMileStatus") ?: "Not Started"

                if (first != "Completed") {
                    Toast.makeText(this, "Complete first mile before marking second mile", Toast.LENGTH_SHORT).show()
                    return@refreshTravelerDoc
                }

                if (second == "Completed") {
                    Toast.makeText(this, "Second mile already completed", Toast.LENGTH_SHORT).show()
                    return@refreshTravelerDoc
                }

                // For sender, marking second mile completed will prepare last mile OTP
                completeSecondMileAndPrepareLastMile()
            }
        }

        lastTv.setOnClickListener {
            // Last-mile actions are primarily OTP driven (OTP verification handled by Verify button)
            Toast.makeText(this, "Use the OTP field and Verify button to complete last mile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshTravelerDoc(onComplete: (DocumentSnapshot?) -> Unit) {
        if (uniqueKey.isNullOrEmpty()) {
            onComplete(null)
            return
        }
        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    travelerDoc = snapshot.documents[0]
                    onComplete(travelerDoc)
                } else {
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error refreshing traveler doc", e)
                onComplete(null)
            }
    }

    // Generate 6-digit OTP
    private fun generateOtp(): String {
        val otp = Random.nextInt(100000, 999999)
        return otp.toString()
    }

    /**
     * Initiates first mile:
     * - sets FirstMileStatus = "In Progress"
     * - sets FirstMileOTP = <generated>
     * - updates traveler.status to indicate progress if desired
     */
    private fun initiateFirstMile() {
        if (uniqueKey.isNullOrEmpty()) {
            Toast.makeText(this, "No journey key available", Toast.LENGTH_SHORT).show()
            return
        }

        val otp = generateOtp()
        val updates = hashMapOf<String, Any>(
            "FirstMileStatus" to "In Progress",
            "FirstMileOTP" to otp,
            "status" to "First Mile - In Progress"
        )

        updateTravelerFieldsByUniqueKey(updates) { success, docRef ->
            if (success) {
                runOnUiThread {
                    findViewById<TextView>(R.id.firstmilesender)?.text = "✓ 1st Stage - In Progress"
                    // show OTP to sender (for manual share) and toast
                    Toast.makeText(this, "First mile started. OTP: $otp", Toast.LENGTH_LONG).show()
                    // Also update sender doc display if needed
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to start first mile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Marks first mile as completed.
     */
    private fun completeFirstMile() {
        if (uniqueKey.isNullOrEmpty()) {
            Toast.makeText(this, "No journey key available", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf<String, Any>(
            "FirstMileStatus" to "Completed",
            "status" to "First Mile - Completed"
        )

        updateTravelerFieldsByUniqueKey(updates) { success, _ ->
            if (success) {
                runOnUiThread {
                    findViewById<TextView>(R.id.firstmilesender)?.text = "✓ 1st Stage - Completed"
                    Toast.makeText(this, "First mile marked completed", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to mark first mile completed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Completes second mile and prepares last mile:
     * - sets SecondMileStatus = "Completed"
     * - sets LastMileStatus = "In Progress"
     * - generates LastMileOTP and writes it to traveler doc
     */
    private fun completeSecondMileAndPrepareLastMile() {
        if (uniqueKey.isNullOrEmpty()) {
            Toast.makeText(this, "No journey key available", Toast.LENGTH_SHORT).show()
            return
        }

        val lastOtp = generateOtp()
        val updates = hashMapOf<String, Any>(
            "SecondMileStatus" to "Completed",
            "LastMileStatus" to "In Progress",
            "LastMileOTP" to lastOtp,
            "status" to "Second Mile - Completed, Last Mile - In Progress"
        )

        updateTravelerFieldsByUniqueKey(updates) { success, _ ->
            if (success) {
                runOnUiThread {
                    findViewById<TextView>(R.id.secondmilesender)?.text = "✓ 2nd Stage - Completed"
                    findViewById<TextView>(R.id.lastmilestatussender)?.text = "📍 3rd Stage - In Progress"
                    Toast.makeText(this, "Second mile completed. Last mile OTP: $lastOtp", Toast.LENGTH_LONG).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to update second/last mile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Verifies Last Mile OTP entered by the sender (delivered by picker/delivery person).
     * If OTP matches traveler's LastMileOTP -> mark LastMileStatus = "Completed" and status = "Completed"
     */
    private fun verifyLastMileOtp(enteredOtp: String) {
        if (uniqueKey.isNullOrEmpty()) {
            Toast.makeText(this, "No journey key available", Toast.LENGTH_SHORT).show()
            return
        }

        // Get current traveler doc to compare OTP
        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents[0]
                    val expected = doc.getString("LastMileOTP") ?: ""
                    if (enteredOtp == expected && enteredOtp.isNotEmpty()) {
                        // OTP matched -> complete last mile
                        val updates = hashMapOf<String, Any>(
                            "LastMileStatus" to "Completed",
                            "status" to "Completed"
                        )
                        doc.reference.update(updates)
                            .addOnSuccessListener {
                                runOnUiThread {
                                    findViewById<TextView>(R.id.lastmilestatussender)?.text = "📍 3rd Stage - Completed"
                                    findViewById<TextView>(R.id.tvDestinationStatus)?.text = "Order Completed, Thanks!"
                                    Toast.makeText(this, "OTP Verified. Order completed!", Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("SenderProfile", "Failed to update last mile status", e)
                                runOnUiThread {
                                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Traveler document not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error fetching traveler for OTP", e)
                runOnUiThread {
                    Toast.makeText(this, "Error verifying OTP", Toast.LENGTH_SHORT).show()
                }
            }
    }

    /**
     * Generic helper: find traveler doc by uniqueKey and update fields.
     * Calls callback(success, docReferenceIfAvailable)
     */
    private fun updateTravelerFieldsByUniqueKey(updates: Map<String, Any>, callback: (Boolean, com.google.firebase.firestore.DocumentReference?) -> Unit) {
        if (uniqueKey.isNullOrEmpty()) {
            callback(false, null)
            return
        }

        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents[0]
                    doc.reference.update(updates)
                        .addOnSuccessListener {
                            // refresh local travelerDoc
                            doc.reference.get()
                                .addOnSuccessListener { refreshed ->
                                    travelerDoc = refreshed
                                    callback(true, doc.reference)
                                }
                                .addOnFailureListener {
                                    // still consider true but no refreshed doc
                                    callback(true, doc.reference)
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("SenderProfile", "Failed to update traveler fields", e)
                            callback(false, doc.reference)
                        }
                } else {
                    Log.w("SenderProfile", "No traveler doc found for uniqueKey=$uniqueKey")
                    callback(false, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Failed to query traveler by uniqueKey", e)
                callback(false, null)
            }
    }

}
