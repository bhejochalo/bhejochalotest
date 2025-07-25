package com.example.myapplication

import FlightStatusHandler
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.SyncStateContract.Helpers.update
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore // Assuming you're using Firestore
import android.widget.ScrollView
import androidx.core.content.ContextCompat

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.myapplication.BorzoOrderHelper
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

class TravelerProfile : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private var houseNumber_Traveler = "";
    private var street_Traveler = "";
    private var area_Traveler = "";
    private var city_Traveler = "";
    private var pincode_Traveler = "";
    private var state_Traveler = "";
    private var isProcessing = false

    private var houseNumber_Sender = "";
    private var street_Sender = "";
    private var area_Sender = "";
    private var city_Sender = "";
    private var pincode_Sender = "";
    private var state_Sender = "";
    private var isFirstMile = false;

    //private var uniqueKey = "";
    private var mileStatus = ""
    private var senderPhoneNumber = ""

    private lateinit var borzoHelper: BorzoOrderHelper
    private val apiFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
    private val displayFormatter =
        DateTimeFormatter.ofPattern("h:mm a, dd MMM yyyy", Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traveler_profile)
        enableEdgeToEdge()


        setupSlider()


        borzoHelper = BorzoOrderHelper(this)

        var key = intent.getStringExtra("KEY") ?: ""
        key?.let { nonNullKey ->
            setupBookingTabs(nonNullKey)
        }

        var statusOfRequest = intent.getStringExtra("StatusOnTraveler") ?: ""

        // Set up window insets
        // Use the root view of your layout instead of R.id.main
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Check if AddressHolder has data
        if (AddressHolder.fromHouseNumber != null) {
            displayAddressFromHolder() // fresh traveler
        } else {
            // If AddressHolder is null, fetch from database
            getTheCurrentTravelerData() //  registered travelers

        }
    }

    private fun setupSlider() {
        val slider = findViewById<Slider>(R.id.sliderConfirmArrival)

        slider.apply {
            trackActiveTintList = ColorStateList.valueOf(Color.parseColor("#FFA500")) // Orange
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#FFA500"))
            trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))

            addOnChangeListener { _, value, fromUser ->
                if (fromUser && value == 100f && !isProcessing) {
                    isProcessing = true
                    isEnabled = false
                    showConfirmationDialog()
                }
            }
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Confirm Arrival")
            setMessage("If you confirm you've reached home, we will initiate the final pickup process. This action cannot be undone.")
            setPositiveButton("Yes, I'm Home") { dialog, _ ->
                handleConfirmation()
                dialog.dismiss()
            }
            setNegativeButton("Cancel") { dialog, _ ->
                resetSlider()
                dialog.dismiss()
            }
            setOnCancelListener { resetSlider() }
            show()
        }
    }

    private fun handleConfirmation() {
        Toast.makeText(this, "Arrival Confirmed!", Toast.LENGTH_SHORT).show()
        findViewById<Slider>(R.id.sliderConfirmArrival).isEnabled = false


        val phNum = getCurrentUserId() ?: run {
            Log.e("Auth", "No user ID available")
            // Show error to user if needed
            return
        }

        updateTraveler2ndMileStatus(phNum)
        // yahi per borzo bhi book karenge
    }

    private fun resetSlider() {
        val slider = findViewById<Slider>(R.id.sliderConfirmArrival)
        slider.value = 0f
        slider.isEnabled = true
        isProcessing = false
    }


    private fun displayAddressFromHolder() {
        // Get references to TextViews
        val tvFromAddress = findViewById<TextView>(R.id.tvFromAddress)
        val tvToAddress = findViewById<TextView>(R.id.tvToAddress)

        // Build address strings
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

        // Display addresses
        tvFromAddress.text = fromAddress
        tvToAddress.text = toAddress
    }

    private fun getTheCurrentTravelerData() {
        val currentUserId = getCurrentUserId() ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("traveler").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Set address display text
                    findViewById<TextView>(R.id.tvFromAddress).text =
                        document.getString("fromAddress.fullAddress") ?: ""
                    findViewById<TextView>(R.id.tvToAddress).text =
                        document.getString("toAddress.fullAddress") ?: ""

                    // Set up edit buttons
                    findViewById<Button>(R.id.btnEditFromAddress).setOnClickListener {
                        showAddressEditDialog(document, "fromAddress") { newAddress ->
                            findViewById<TextView>(R.id.tvFromAddress).text = newAddress
                        }
                    }

                    findViewById<Button>(R.id.btnEditToAddress).setOnClickListener {
                        showAddressEditDialog(document, "toAddress") { newAddress ->
                            findViewById<TextView>(R.id.tvToAddress).text = newAddress
                        }
                    }

                    firstMileAddressTraveler(document)

                    val uniqueKey = document.getString("uniqueKey") ?: ""
                    val status = document.getString("status") ?: ""
                    val modofpickDrop = document.getString("pickAndDropMode") ?: ""

                    if (uniqueKey.isNotEmpty() && status != "Request Accepted By Traveler") {
                        showSenderRequest(uniqueKey, document)
                    } else {
                        if (modofpickDrop == "self") {
                            fetchTheAcceptedOrder_SelfPicknDrop()
                            fetchFlightDetails(document)
                            loadSenderDetails(uniqueKey); // need to add the condition if taveler has any active sender
                        } else {
                            fetchTheAcceptedOrder(uniqueKey)
                            fetchFlightDetails(document)
                            loadSenderDetails(uniqueKey); // need to add the condition if taveler has any active sender
                        }
                    }

                } else {
                    Toast.makeText(this, "Traveler data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error fetching traveler data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showAddressEditDialog(
        document: DocumentSnapshot,
        addressType: String,
        onSave: (String) -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(this).inflate(R.layout.dialog_edit_traveler_address, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Get current address data
        val currentAddress = document.get(addressType) as? Map<String, String> ?: mapOf()

        // Initialize views
        val etStreet = dialogView.findViewById<EditText>(R.id.etStreet)
        val etHouseNumber = dialogView.findViewById<EditText>(R.id.etHouseNumber)
        val etArea = dialogView.findViewById<EditText>(R.id.etArea)
        val etCity = dialogView.findViewById<EditText>(R.id.etCity)
        val etState = dialogView.findViewById<EditText>(R.id.etState)
        val etPostalCode = dialogView.findViewById<EditText>(R.id.etPostalCode)

        // Auto-populate all fields including state
        etStreet.setText(currentAddress["street"] ?: "")
        etHouseNumber.setText(currentAddress["houseNumber"] ?: "")
        etArea.setText(currentAddress["area"] ?: "")
        etCity.setText(currentAddress["city"] ?: "")
        etState.setText(currentAddress["state"] ?: "")
        etPostalCode.setText(currentAddress["postalCode"] ?: "")

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val newStreet = etStreet.text.toString().trim()
            val newHouseNumber = etHouseNumber.text.toString().trim()
            val newArea = etArea.text.toString().trim()
            val newCity = etCity.text.toString().trim()
            val newState = etState.text.toString().trim()
            val newPostalCode = etPostalCode.text.toString().trim()

            // Check if any field has changed
            val hasChanges = newStreet != currentAddress["street"] ||
                    newHouseNumber != currentAddress["houseNumber"] ||
                    newArea != currentAddress["area"] ||
                    newCity != currentAddress["city"] ||
                    newState != currentAddress["state"] ||
                    newPostalCode != currentAddress["postalCode"]

            if (!hasChanges) {
                Toast.makeText(this, "No changes made", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            // Create formatted address with state
            val fullAddress = buildString {
                append(newStreet)
                if (newHouseNumber.isNotEmpty()) append(" $newHouseNumber")
                if (newArea.isNotEmpty()) append(", $newArea")
                if (newCity.isNotEmpty()) append(", $newCity")
                if (newState.isNotEmpty()) append(", $newState")
                if (newPostalCode.isNotEmpty()) append(" $newPostalCode")
            }

            // Prepare updates only for changed fields
            val updates = hashMapOf<String, Any>(
                "$addressType.fullAddress" to fullAddress
            )

            // Add individual fields only if they've changed
            if (newStreet != currentAddress["street"]) {
                updates["$addressType.street"] = newStreet
            }
            if (newHouseNumber != currentAddress["houseNumber"]) {
                updates["$addressType.houseNumber"] = newHouseNumber
            }
            if (newArea != currentAddress["area"]) {
                updates["$addressType.area"] = newArea
            }
            if (newCity != currentAddress["city"]) {
                updates["$addressType.city"] = newCity
            }
            if (newState != currentAddress["state"]) {
                updates["$addressType.state"] = newState
            }
            if (newPostalCode != currentAddress["postalCode"]) {
                updates["$addressType.postalCode"] = newPostalCode
            }

            // Update Firestore
            db.collection("traveler").document(getCurrentUserId() ?: return@setOnClickListener)
                .update(updates)
                .addOnSuccessListener {
                    onSave(fullAddress)
                    Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()

        // Adjust dialog window size
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }


    private fun getCurrentUserId(): String? {
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val phoneNumber = sharedPref.getString("PHONE_NUMBER", "")

        return phoneNumber //
    }

    private fun showSenderRequest(
        senderRef: String,
        document: DocumentSnapshot
    ) {  // Removed context parameter since we're in Activity
        println("inside showSenderRequest ===> $senderRef")


        println("inside showSenderRequest ===> Hardcoded data for testing")

        // Hardcoded sender data
        val name = "John Doe"
        val phone = "+1 555-123-4567"
        val address = "123 Main St, New York, NY 10001"

        runOnUiThread {
            try {
                if (!isFinishing) {
                    showSenderDialog(name, phone, address, senderRef, document)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@TravelerProfile,
                    "Error showing dialog: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun showSenderDialog(
        name: String,
        phone: String,
        address: String,
        senderRef: String,
        document: DocumentSnapshot
    ) {
        try {
            val dialogView = LayoutInflater.from(this@TravelerProfile).inflate(
                R.layout.dialog_sender_details,
                findViewById(android.R.id.content),
                false
            )

            // Safe TextView access
            dialogView.findViewById<TextView>(R.id.tvSenderName)?.text = name
            dialogView.findViewById<TextView>(R.id.tvSenderPhone)?.text = phone
            dialogView.findViewById<TextView>(R.id.tvSenderAddress)?.text = address

            AlertDialog.Builder(this@TravelerProfile)
                .setTitle("Sender Request")
                .setView(dialogView)
                .setPositiveButton("Accept") { dialog, _ ->
                    onAcceptRequest(senderRef, document)
                    dialog.dismiss()
                }
                .setNegativeButton("Reject") { dialog, _ ->
                    onRejectRequest(senderRef, document)
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this@TravelerProfile, "Error showing dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAcceptRequest(senderRef: String, document: DocumentSnapshot) {
        // in document we have current traveler
        // isFirstMile = true;
        //placeBorzoOrder(senderRef) // need to pass the other details also, address of traveler
        isFirstMile = true
        val modofpickDrop = "${document.getString("pickAndDropMode")}"

        println("modofpickDrop ===> $modofpickDrop")

        // Create a map of the fields you want to update
        val updates = hashMapOf<String, Any>(
            "status" to "Request Accepted By Traveler",
            "FirstMileStatus" to "In Progress",// or use your own timestamp
            "SecondMileStatus" to "Not Started",
            "LastMileStatus" to "Not Started",


            // Add more fields as needed
        )



        document.reference.update(updates) // // updating the traveler status
            .addOnSuccessListener {
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Request Accepted", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Error: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }

        // Prepare traveler address data
        val travelerAddress = mapOf(
            "houseNumber" to houseNumber_Traveler,
            "street" to street_Traveler,
            "area" to area_Traveler,
            "city" to city_Traveler,
            "state" to state_Traveler,
            "postalCode" to pincode_Traveler
        )

        if (modofpickDrop == "self") {

            fetchTheAcceptedOrder_SelfPicknDrop(); // show the order details to the traveler
            fetchFlightDetails(document) // fetch flight details, in doc -  current traveler record

        } else {

            borzoHelper.placeOrder(
                senderId = senderRef,
                travelerAddress = travelerAddress,
                onSuccess = {
                    runOnUiThread {
                        //  updateBookingDetailsOnSender()
                        //  updateBookingDetailsOnTraveler()
                        Toast.makeText(this, "Booking successful!", Toast.LENGTH_LONG).show()
                    }
                },
                onFailure = { errorMessage ->
                    runOnUiThread {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            )
            fetchTheAcceptedOrder(senderRef) // show the order details to the traveler
            fetchFlightDetails(document) // fetch flight details,  passing current traveler record
        }


    }


    private fun onRejectRequest(
        senderRef: String,
        document: DocumentSnapshot
    ) { // in document we have current traveler

        document.reference.update(
            "status",
            "Request Rejected By Traveler"
        ) // updating the traveler status
            .addOnSuccessListener {
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Request Rejected", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Error: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }


    /*
        private fun formatPhoneForBorzo(phone: String): String? {
            // Remove all non-digit characters
            val digitsOnly = phone.replace("[^0-9]".toRegex(), "")

            // Borzo typically expects phone numbers in E.164 format (e.g., +918696888060)
            return when {
                digitsOnly.length == 10 -> "+91$digitsOnly" // Assuming Indian numbers
                digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
                digitsOnly.startsWith("+") -> phone // Already in correct format
                else -> null // Invalid format
            }
        }

        private fun buildFullAddress(
            houseNumber: String?,
            street: String?,
            area: String?,
            city: String?,
            state: String?,
            postalCode: String?
        ): String {
            println("in build full address")
            return listOfNotNull(houseNumber, street, area, city, state, postalCode)
                .joinToString(", ")
        }

        private fun placeBorzoOrder(senderId: String) {
            // First get the sender data, then place the order
            getTheRelatedSenderData(senderId) { senderData ->
                println("in borzo place order")
                val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                val phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""

                // Format phone numbers according to Borzo requirements
                val senderPhone = formatPhoneForBorzo(senderData["phoneNumber"] ?: "")// formatPhoneForBorzo(senderId)
                val receiverPhone = formatPhoneForBorzo(phoneNumber)

                if (senderPhone == null || receiverPhone == null) {
                    runOnUiThread {
                        Toast.makeText(
                            this@TravelerProfile,
                            "Invalid phone number format",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@getTheRelatedSenderData
                }

                val toAddress = buildFullAddress(
                    houseNumber_Traveler,
                    street_Traveler,
                    area_Traveler,
                    city_Traveler,
                    state_Traveler,
                    pincode_Traveler
                )

                val fromAddress = buildFullAddress(
                    senderData["houseNumber"] ?: "",
                    senderData["street"] ?: "",
                    senderData["area"] ?: "",
                    senderData["city"] ?: "",
                    senderData["state"] ?: "",
                    senderData["postalCode"] ?: "",
                   // senderData["phoneNumber"]?: ""
                )

                println("Debugging fromAddress components:")
                println("House Number 1: ${senderData["houseNumber"]}")
                println("Street1: ${senderData["street"]}")
                println("Area1: ${senderData["area"]}")
                println("City:1 ${senderData["city"]}")
                println("State:1 ${senderData["state"]}")
                println("Pincode1: ${senderData["postalCode"]}")

                // Rest of your Borzo order placement code...
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val jsonBody = JSONObject().apply {
                    put("matter", "Documents") // should be dynamic
                    put("vehicle_type_id", 2)
                  //  put("client_order_id", senderId) // Add client order ID
                  //  put("required_start_time", "2023-05-15T14:30:00Z")// Add required start time (format depends on API)
                  //  put("note", "full address dalgenge") // Add note
                 //   put("delivery_id", senderId) // Add delivery ID
                    put("points", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", fromAddress)
                            put("contact_person", JSONObject().apply {
                                put("phone", senderPhone)
                                put("name", "Sender")
                            })
                        })
                        put(JSONObject().apply {
                            put("address", toAddress)
                            put("contact_person", JSONObject().apply {
                                put("phone", receiverPhone)
                                put("name", "Receiver")
                            })
                        })
                    })
                 //   put("client_order_id", senderId)  // Your internal order ID (formerly client_order_id)
                  //  put("required_start_datetime", "2025-06-01T14:30:00+05:30")  // ISO 8601 format (for scheduled orders)
                 //   put("note", "Fragile items")  // General order note
                }.toString()

                println("Request JSON: $jsonBody")

                val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("https://robotapitest-in.borzodelivery.com/api/business/1.6/create-order")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-DV-Auth-Token", "3F561C810EDAC4F9339582C4BCB9F1A1B3800B87")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        println("in borzo place order failed")
                        Log.e("BORZO", "Request failed: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(
                                this@TravelerProfile,
                                "Booking failed. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            println("in borzo place order response")
                            Log.d("BORZO", "Response: $responseBody")

                            runOnUiThread {
                                if (response.isSuccessful) {

                                    updateBookingDetailsOnSender(response)
                                    updateBookingDetailsOnTraveler(response)

                                    Toast.makeText(
                                        this@TravelerProfile,
                                        "Booking successful!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@TravelerProfile,
                                        "Booking failed: ${response.message}\n$responseBody",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BORZO", "Error parsing response", e)
                            runOnUiThread {
                                Toast.makeText(
                                    this@TravelerProfile,
                                    "Error processing booking",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                })
            }
        }

        private fun getTheRelatedSenderData(senderId: String, callback: (Map<String, String?>) -> Unit) {
          //  println("uniqueKey   ===> $uniqueKey")
            db.collection("Sender")
                .whereEqualTo("uniqueKey", senderId) // Filters documents w
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        // Get the first matching document (assuming userId is unique)
                        val document = querySnapshot.documents[0]

                        // Extract nested address fields safely
                        val address = document.get("fromAddress") as? Map<String, String>

                        val senderData = hashMapOf<String, String?>(
                            "houseNumber" to address?.get("houseNumber"),
                            "street" to address?.get("street"),
                            "area" to address?.get("area"),
                            "city" to address?.get("city"),
                            "postalCode" to address?.get("postalCode"),
                            "state" to address?.get("state"),
                            "phoneNumber" to document.getString("phoneNumber")
                        )


                        // last mile sender address will come here

                        // Update the class fields
                      */
    /*  this.houseNumber_Sender = senderData["houseNumber"] ?: ""
                        this.street_Sender = senderData["street"] ?: ""
                        this.area_Sender = senderData["area"] ?: ""
                        this.city_Sender = senderData["city"] ?: ""
                        this.pincode_Sender = senderData["postalCode"] ?: ""
                        this.state_Sender = senderData["state"] ?: ""

                        println("Debugging sender fromAddress components:")
                        println("House Number sender : ${senderData["houseNumber"]}")
                        println("Streetsender = : ${senderData["street"]}")
                        println("Areasender = : ${senderData["area"]}")
                        println("Citysender = : ${senderData["city"]}")
                        println("Statesender = : ${senderData["state"]}")
                        println("Pincodesender = : ${senderData["postalCode"]}") *//*


                    callback(senderData)
                } else {
                    Toast.makeText(this, "Sender data not found", Toast.LENGTH_SHORT).show()
                    callback(emptyMap())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching Sender data: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(emptyMap())
            }
    }
*/

    private fun firstMileAddressTraveler(document: DocumentSnapshot) {
// unique key would be same for traveler and sender in a journey


        houseNumber_Traveler = """
                        ${document.getString("fromAddress.houseNumber")}
                      
                    """.trimIndent()

        street_Traveler = """
                        ${document.getString("fromAddress.street")}
                      
                    """.trimIndent()

        area_Traveler = """
                        ${document.getString("fromAddress.area")}
                      
                    """.trimIndent()

        city_Traveler = """
                        ${document.getString("fromAddress.city")}
                      
                    """.trimIndent()

        pincode_Traveler = """
                        ${document.getString("fromAddress.postalCode")}
                      
                    """.trimIndent()

        state_Traveler = """
                        ${document.getString("fromAddress.state")}
                      
                    """.trimIndent()


    }

    private fun lastMileAddressTraveler() {
        // traveler to address will be from address in last mile

    }

    private fun updateBookingDetailsOnSender(response: Response) {

        try {
            val responseBody = response.body?.string()
            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                // Parse JSON response (example using Kotlin's JSON parsing)
                val json = JSONObject(responseBody)

                // Extract data from response
                val bookingId = json.optString("order.order_id")
                //   order name = order_name
                //     "vehicle_type_id":2,
                //  created_datetime":"2025-05-31T12:07:13+05:30","finish_datetime":null,"status":"new","status_description":"Created","matter":"Documents","total_weight_kg":500,"is_client_notification_enabled":false,"is_contact_person_notification_enabled":false,"loaders_count":0,"backpayment_details":null
                val status = json.optString("status")
                val trackingUrl = json.optString("tracking_url")

                // Update UI or perform other actions
                runOnUiThread {
                    // Example: Update TextViews
                    // ? findViewById<TextView>(R.id.tvBookingId).text = bookingId
                    // ? findViewById<TextView>(R.id.tvStatus).text = status

                    // You can also update Sender.senderRecord if needed
                    // (Assuming you have a way to convert this to a DocumentSnapshot)
                }

                Log.d("UPDATE_SENDER", "Successfully updated sender details")
            } else {
                Log.e("UPDATE_SENDER", "Unsuccessful response: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("UPDATE_SENDER", "Error updating sender details", e)
        }

    }


    private fun updateBookingDetailsOnTraveler(response: Response) {

    }

    private fun fetchTheAcceptedOrder(uniqueKey: String) {
        val subStatus = findViewById<TextView>(R.id.subStatus)
        val trackingUrlTextView = findViewById<TextView>(R.id.trackingUrl)
        // val startTimeSender = findViewById<TextView>(R.id.startTimeSender)
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

                                    /*  startTimeSender.text =
                                          "Pickup: ${formatDateTime(senderPoint["requiredStartDatetime"])}"*/
                                    endTimeSender.text =
                                        "Parcel Arrival Time: ${formatDateTime(senderPoint["requiredFinishDatetime"])}"
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

    private fun fetchTheAcceptedOrder_SelfPicknDrop() {

        print("in fetchTheAcceptedOrder_SelfPicknDrop")

        val subStatus = findViewById<TextView>(R.id.subStatus)
        subStatus.text = "Mode - Self Pick And Drop"


    }

    private fun fetchFlightDetails(document: DocumentSnapshot) {
        try {
            // Create flight data map with proper types, in doc - traveler data
            val flightData = hashMapOf<String, Any>(
                "pnrVerified" to (document.getBoolean("verified") ?: false),
                "pnr" to (document.getString("pnr") ?: ""),
                "flightNumber" to (document.getString("flightNumber") ?: ""),
                "airline" to (document.getString("airline") ?: ""),
                "lastName" to (document.getString("lastName") ?: ""),
                "phoneNumber" to (document.getString("phoneNumber") ?: ""),
                "weightUpto" to (document.getString("weightUpto") ?: ""),
                "spaceAvailableIn" to (document.getString("spaceAvailableIn") ?: ""),
                "documentId" to document.id,
                // Add separate date and time fields
                "leavingDate" to (document.getString("leavingDate") ?: ""),
                "leavingTime" to (document.getString("leavingTime") ?: "")
            )

            // Maintain backward compatibility with departureTime
            document.getString("departureTime")?.let {
                flightData["departureTime"] = it
                // Parse into separate fields if needed
                if (flightData["leavingDate"].toString().isEmpty()) {
                    flightData["leavingDate"] = formatDate(it)
                }
                if (flightData["leavingTime"].toString().isEmpty()) {
                    flightData["leavingTime"] = formatTime(it)
                }
            }

            // Update UI on main thread
            runOnUiThread {
                try {
                    flightData["airline"]?.let {
                        findViewById<TextView>(R.id.tvAirline)?.text = it.toString()
                    }
                    flightData["lastName"]?.let {
                        findViewById<TextView>(R.id.tvLastName)?.text = it.toString()
                    }
                    // Use separate date and time fields
                    flightData["leavingDate"]?.let {
                        findViewById<TextView>(R.id.tvLeavingDate)?.text = it.toString()
                    }
                    flightData["leavingTime"]?.let {
                        findViewById<TextView>(R.id.tvLeavingTime)?.text = it.toString()
                    }
                    flightData["phoneNumber"]?.let {
                        findViewById<TextView>(R.id.tvPhoneNumber)?.text = it.toString()
                    }
                    flightData["pnr"]?.let {
                        findViewById<TextView>(R.id.tvPnr)?.text = it.toString()
                    }
                    flightData["weightUpto"]?.let {
                        findViewById<TextView>(R.id.tvWeightUpto)?.text = "$it kg"
                    }

                    flightData["spaceAvailableIn"]?.let {
                        findViewById<TextView>(R.id.tvsAIn)?.text = it.toString()
                    }

                    // Set up edit button with dialog
                    setupFlightDetailsEditDialog(flightData)
                } catch (e: Exception) {
                    Log.e("FlightDetails", "UI update error", e)
                    showToast("Error displaying flight details")
                }
            }

            // Fetch flight status if available
            (flightData["flightNumber"] as? String)?.takeIf { it.isNotEmpty() }
                ?.let { flightNumber ->
                    // fetchFlightStatus(flightNumber)
                    fetchFlightStatusFromTraveler(document)
                }

        } catch (e: Exception) {
            Log.e("FlightDetails", "Error fetching document", e)
            showToast("Error loading flight details")
        }
    }

    private fun setupFlightDetailsEditDialog(flightData: Map<String, Any>) {
        findViewById<Button>(R.id.btnEditFlightDetails)?.setOnClickListener {
            showEditFlightDetailsDialog(flightData)
        }
    }

    private fun showEditFlightDetailsDialog(flightData: Map<String, Any>) {
        val dialogView =
            LayoutInflater.from(this).inflate(R.layout.dialog_edit_flight_details, null)

        // Initialize dialog views
        val etAirline = dialogView.findViewById<TextInputEditText>(R.id.etAirline)
        val etLastName = dialogView.findViewById<TextInputEditText>(R.id.etLastName)
        val etLeavingDate = dialogView.findViewById<TextInputEditText>(R.id.etLeavingDate)
        val etLeavingTime = dialogView.findViewById<TextInputEditText>(R.id.etLeavingTime)
        val etPhoneNumber = dialogView.findViewById<TextInputEditText>(R.id.etPhoneNumber)
        val etPnr = dialogView.findViewById<TextInputEditText>(R.id.etPnr)
        val etWeightUpto = dialogView.findViewById<TextInputEditText>(R.id.etWeightUpto)
        val etspaceAvailableIn = dialogView.findViewById<TextInputEditText>(R.id.etspaceAvailableIn)
        // Set current values
        etAirline.setText(flightData["airline"].toString())
        etLastName.setText(flightData["lastName"].toString())
        // Use separate date and time fields
        etLeavingDate.setText(flightData["leavingDate"].toString())
        etLeavingTime.setText(flightData["leavingTime"].toString())
        etPhoneNumber.setText(flightData["phoneNumber"].toString())
        etPnr.setText(flightData["pnr"].toString())
        etWeightUpto.setText(flightData["weightUpto"].toString())
        etspaceAvailableIn.setText(flightData["spaceAvailableIn"].toString())
        // Setup date picker (keep existing implementation)
        etLeavingDate.setOnClickListener {
            showDatePickerDialog(etLeavingDate)
        }

        // Setup time picker (keep existing implementation)
        etLeavingTime.setOnClickListener {
            showTimePickerDialog(etLeavingTime)
        }

        // Create and show dialog
        val dialog = Dialog(this).apply {
            setContentView(dialogView)
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        // Set up button click listeners
        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSaveFlightDetails).setOnClickListener {
            try {
                val updates = hashMapOf<String, Any>(
                    "airline" to etAirline.text.toString(),
                    "lastName" to etLastName.text.toString(),
                    "phoneNumber" to etPhoneNumber.text.toString(),
                    "pnr" to etPnr.text.toString(),
                    "weightUpto" to etWeightUpto.text.toString(),
                    // Store separate date and time
                    "leavingDate" to etLeavingDate.text.toString(),
                    "leavingTime" to etLeavingTime.text.toString(),
                    "spaceAvailableIn" to etspaceAvailableIn.text.toString()
                )

                // Maintain backward compatibility with departureTime
                if (etLeavingDate.text?.isNotEmpty() == true && etLeavingTime.text?.isNotEmpty() == true) {
                    updates["departureTime"] = combineDateTime(
                        etLeavingDate.text.toString(),
                        etLeavingTime.text.toString()
                    )
                }

                if (validateFlightDetails(updates)) {
                    updateFlightDetailsInFirestore(
                        flightData["documentId"].toString(),
                        updates
                    ) { success ->
                        if (success) {
                            runOnUiThread {
                                // Update UI with new values
                                updates["airline"]?.let {
                                    findViewById<TextView>(R.id.tvAirline)?.text = it.toString()
                                }
                                updates["lastName"]?.let {
                                    findViewById<TextView>(R.id.tvLastName)?.text = it.toString()
                                }
                                updates["phoneNumber"]?.let {
                                    findViewById<TextView>(R.id.tvPhoneNumber)?.text = it.toString()
                                }
                                updates["pnr"]?.let {
                                    findViewById<TextView>(R.id.tvPnr)?.text = it.toString()
                                }
                                updates["weightUpto"]?.let {
                                    findViewById<TextView>(R.id.tvWeightUpto)?.text = "$it kg"
                                }
                                // Update separate date/time fields
                                updates["leavingDate"]?.let {
                                    findViewById<TextView>(R.id.tvLeavingDate)?.text = it.toString()
                                }
                                updates["leavingTime"]?.let {
                                    findViewById<TextView>(R.id.tvLeavingTime)?.text = it.toString()
                                }
                                updates["spaceAvailabelIn"]?.let {
                                    findViewById<TextView>(R.id.tvsAIn)?.text = it.toString()
                                }

                                showToast("Details updated successfully")
                                dialog.dismiss()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FlightDetails", "Error saving flight details", e)
                showToast("Error saving details: ${e.message}")
            }
        }

        dialog.show()
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = "${day}/${month + 1}/${year}"
                editText.setText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val selectedTime = String.format("%02d:%02d", hour, minute)
                editText.setText(selectedTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun combineDateTime(dateStr: String, timeStr: String): String {
        try {
            // Parse the date (assuming format dd/MM/yyyy)
            val dateParts = dateStr.split("/")
            val day = dateParts[0].toInt()
            val month = dateParts[1].toInt() - 1 // Calendar months are 0-based
            val year = dateParts[2].toInt()

            // Parse the time (assuming format HH:mm)
            val timeParts = timeStr.split(":")
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            // Create ZonedDateTime with system timezone
            val zonedDateTime = ZonedDateTime.of(
                year, month, day, hour, minute, 0, 0,
                ZoneId.systemDefault()
            )

            // Format as ISO string
            return zonedDateTime.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
        } catch (e: Exception) {
            Log.e("DateTime", "Error combining date and time", e)
            throw IllegalArgumentException("Invalid date or time format")
        }
    }

    private fun validateFlightDetails(data: Map<String, Any>): Boolean {
        return when {
            data["airline"].toString().isEmpty() -> {
                showToast("Airline cannot be empty")
                false
            }

            data["lastName"].toString().isEmpty() -> {
                showToast("Last name cannot be empty")
                false
            }

            data["phoneNumber"].toString().isEmpty() -> {
                showToast("Phone number cannot be empty")
                false
            }

            data["weightUpto"].toString().toIntOrNull() ?: 0 <= 0 -> {
                showToast("Weight must be greater than 0")
                false
            }

            data["leavingDate"].toString().isEmpty() -> {
                showToast("Please select a leaving date")
                false
            }

            data["leavingTime"].toString().isEmpty() -> {
                showToast("Please select a leaving time")
                false
            }

            else -> true
        }
    }

    private fun isValidFlightData(data: Map<String, Any>): Boolean {
        return when {
            data["airline"].toString().isEmpty() -> {
                showToast("Airline cannot be empty")
                false
            }

            data["lastName"].toString().isEmpty() -> {
                showToast("Last name cannot be empty")
                false
            }

            data["phoneNumber"].toString().isEmpty() -> {
                showToast("Phone number cannot be empty")
                false
            }

            (data["weightUpto"] as Long) <= 0 -> {
                showToast("Weight must be greater than 0")
                false
            }

            else -> true
        }
    }

    private fun updateFlightDetailsInFirestore(
        documentId: String,
        updates: Map<String, Any>,
        callback: (Boolean) -> Unit = {}
    ) {
        if (documentId.isEmpty()) {
            Log.e("Firestore", "Document ID is empty")
            callback(false)
            return
        }

        FirebaseFirestore.getInstance()
            .collection("traveler")
            .document(documentId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("Firestore", "Document updated successfully")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating document", e)
                showToast("Failed to update details")
                callback(false)
            }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Helper extension functions
    fun String?.orEmpty(): String = this ?: ""

    // Date/Time formatting helpers
    private fun formatDate(isoTime: String): String {
        return try {
            if (isoTime.isNotEmpty()) {
                ZonedDateTime.parse(isoTime, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    .format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
            } else "N/A"
        } catch (e: Exception) {
            Log.e("DateFormat", "Error formatting date", e)
            "N/A"
        }
    }

    private fun formatTime(isoTime: String): String {
        return try {
            if (isoTime.isNotEmpty()) {
                ZonedDateTime.parse(isoTime, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            } else "N/A"
        } catch (e: Exception) {
            Log.e("DateFormat", "Error formatting time", e)
            "N/A"
        }
    }


    /*  private fun fetchFlightStatus(flightNumber: String) {
           val handler = FlightStatusHandler("d39af3ab5bee86108417afdcf52134cb")

           // Get all UI references once
           val tvFromCode = findViewById<TextView>(R.id.tvFromCode)
           val tvFromCity = findViewById<TextView>(R.id.tvFromCity)
           val tvFromTime = findViewById<TextView>(R.id.tvFromTime)
           val tvToCode = findViewById<TextView>(R.id.tvToCode)
           val tvToCity = findViewById<TextView>(R.id.tvToCity)
           val tvToTime = findViewById<TextView>(R.id.tvToTime)
           val tvFlightStatus = findViewById<TextView>(R.id.tvFlightStatus)
           val tvDelay = findViewById<TextView>(R.id.tvDelay)

           CoroutineScope(Dispatchers.IO).launch {
               try {
                   when (val result = handler.getFlightStatus(flightNumber)) {
                       is FlightStatusHandler.FlightStatusResult.Success -> {
                           withContext(Dispatchers.Main) {
                               // 1. Set airport codes and names
                               tvFromCode.text = "[${result.departureAirportCode}]"
                               tvFromCity.text = result.departureAirport
                               tvToCode.text = "[${result.arrivalAirportCode}]"
                               tvToCity.text = result.arrivalAirport



                               tvFromTime.text = formatFlightTime(result.departureTime) // "10:53 PM, 17 Jun 2025"
                               tvToTime.text = formatFlightTime(result.arrivalTime)

                               // 3. Set status and delay
                               /*val statusText = when {
                                   result.delay > 0 -> "Delayed (${result.delay} mins)"
                                   else -> "On Time"
                               }*/
                               tvFlightStatus.text ="Status  : ${result.status}"
                               tvFlightStatus.setTextColor(if (result.delay > 0) Color.RED else Color.GREEN)

                               tvDelay.text = if (result.delay > 0) "⚠️ Delayed by ${result.delay} minutes" else ""
                           }
                       }
                       is FlightStatusHandler.FlightStatusResult.Error -> {
                           withContext(Dispatchers.Main) {
                               tvFlightStatus.text = "Error: ${result.message}"
                               tvFlightStatus.setTextColor(Color.RED)
                           }
                       }
                   }
               } catch (e: Exception) {
                   withContext(Dispatchers.Main) {
                       tvFlightStatus.text = "Error: ${e.message}"
                       tvFlightStatus.setTextColor(Color.RED)
                   }
               }
           }
       }*/

    /*  private fun formatFlightTime(isoTime: String): String {
          return try {
              ZonedDateTime.parse(isoTime, apiFormatter)
                  .format(displayFormatter)
          } catch (e: Exception) {
              "Time not available" // Fallback text
          }
      }
  */
    private fun fetchFlightStatusFromTraveler(document: DocumentSnapshot) {
        // Get all UI references once
        val tvFlightNumber = findViewById<TextView>(R.id.tvFlightNumber)
        val tvFromCode = findViewById<TextView>(R.id.tvFromCode)
        val tvFromCity = findViewById<TextView>(R.id.tvFromCity)
        val tvFromTime = findViewById<TextView>(R.id.tvFromTime)
        val tvToCode = findViewById<TextView>(R.id.tvToCode)
        val tvToCity = findViewById<TextView>(R.id.tvToCity)
        val tvToTime = findViewById<TextView>(R.id.tvToTime)
        val tvFlightStatus = findViewById<TextView>(R.id.tvFlightStatus)

        // Get data from document
        val toCode = document.getString("toCode") ?: ""
        val toPlace = document.getString("toPlace") ?: ""
        val fromCode = document.getString("fromCode") ?: ""
        val fromPlace = document.getString("fromPlace") ?: ""
        val airline = document.getString("airline") ?: ""
        val arrivalTime = document.getString("arrivalTime") ?: ""
        val departureTime = document.getString("departureTime") ?: ""
        val flightNumber = document.getString("flightNumber") ?: ""
        val status = document.getString("status") ?: ""
        mileStatus = status; // Update the global variable
        // Set values to TextViews
        tvFlightNumber.text = "$airline $flightNumber"
        tvFromCode.text = "[$fromCode]"
        tvFromCity.text = fromPlace
        tvFromTime.text = departureTime
        tvToCode.text = "[$toCode]"
        tvToCity.text = toPlace
        tvToTime.text = arrivalTime
        tvFlightStatus.text = "Status : $status"

        // Check if flight tracking should be enabled (3 hours before departure)
        val shouldEnableTracking = try {
            // Set IST (Indian Standard Time) TimeZone
            val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
            val dateFormat = if (departureTime.contains(" ")) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                    timeZone = istTimeZone
                }
            } else {
                // If only time is available, assume today's date in IST
                SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                    timeZone = istTimeZone
                }
            }

            // Parse departure time in IST
            val departureDateTime = if (departureTime.contains(" ")) {
                dateFormat.parse(departureTime)
            } else {
                // Combine today's date with the given time
                val today = Calendar.getInstance(istTimeZone)
                val timeParts = departureTime.split(":")
                today.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                today.set(Calendar.MINUTE, timeParts.getOrElse(1) { "0" }.toInt())
                today.time
            }

            // Current time in IST
            val now = Calendar.getInstance(istTimeZone).timeInMillis

            // Departure time in IST
            val departureCal =
                Calendar.getInstance(istTimeZone).apply { time = departureDateTime!! }
            val departureTimeInMillis = departureCal.timeInMillis

            // 3 hours before departure (in IST)
            val threeHoursBeforeDeparture = departureTimeInMillis - (3 * 60 * 60 * 1000)

            // Enable tracking if:
            // 1. Current time is within 3 hours before departure OR
            // 2. Flight is today (even if past departure, for status checks)
            now >= threeHoursBeforeDeparture && now <= departureTimeInMillis + (12 * 60 * 60 * 1000) // Allow 12h after departure for status
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        // Enable FlightAware tracking only if flight is upcoming or active
        if (shouldEnableTracking && flightNumber.isNotBlank()) {
            tvFlightNumber.setOnClickListener {
                val trackingUrl = "https://www.flightaware.com/live/flight/$flightNumber"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trackingUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open tracking", Toast.LENGTH_SHORT).show()
                }
            }
            // Visual indication (optional)
            tvFlightNumber.setTextColor(Color.BLUE)
            tvFlightNumber.paintFlags = tvFlightNumber.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        } else {
            // Reset if not clickable
            tvFlightNumber.setOnClickListener(null)
            tvFlightNumber.setTextColor(Color.BLACK) // Or your default text color
            tvFlightNumber.paintFlags =
                tvFlightNumber.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()

            // Show departure date if not today
            if (departureTime.contains(" ")) {
                tvFlightStatus.text = "Will Depart On ${departureTime.substringBefore(" ")}"
            } else {
                tvFlightStatus.text = "Status: Scheduled"
            }
        }
    }

    private fun setupBookingTabs(uniqueKey: String) {
        val tabStatus = findViewById<TextView>(R.id.tabStatus)
        val tabSender = findViewById<TextView>(R.id.tabSender)
        val underlineStatus = findViewById<View>(R.id.underlineStatus)
        val underlineSender = findViewById<View>(R.id.underlineSender)
        val statusContent = findViewById<LinearLayout>(R.id.statusContent)
        // Changed from ScrollView to NestedScrollView
        val senderContent = findViewById<androidx.core.widget.NestedScrollView>(R.id.senderContent)

        // default to Status tab
        statusContent.visibility = View.VISIBLE
        senderContent.visibility = View.GONE
        tabStatus.setTextColor(Color.parseColor("#F25C05"))
        tabSender.setTextColor(Color.parseColor("#888888"))
        underlineStatus.visibility = View.VISIBLE
        underlineSender.visibility = View.GONE

        // Use the tab containers for click listeners instead of just TextViews
        val tabStatusContainer = findViewById<LinearLayout>(R.id.tabStatusContainer)
        val tabSenderContainer = findViewById<LinearLayout>(R.id.tabSenderContainer)

        tabStatusContainer.setOnClickListener {
            statusContent.visibility = View.VISIBLE
            senderContent.visibility = View.GONE

            tabStatus.setTextColor(Color.parseColor("#F25C05"))
            tabSender.setTextColor(Color.parseColor("#888888"))

            underlineStatus.visibility = View.VISIBLE
            underlineSender.visibility = View.GONE

            showMilesStatus(uniqueKey);// for status tab.
        }

        tabSenderContainer.setOnClickListener {
            statusContent.visibility = View.GONE
            senderContent.visibility = View.VISIBLE

            tabStatus.setTextColor(Color.parseColor("#888888"))
            tabSender.setTextColor(Color.parseColor("#F25C05"))

            underlineStatus.visibility = View.GONE
            underlineSender.visibility = View.VISIBLE

            loadSenderDetails(uniqueKey)
        }
    }

    private fun loadSenderDetails(uniqueKey: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Sender")
            .whereEqualTo("uniqueKey", uniqueKey)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]

                    // Get nested maps
                    val fromAddressMap = document.get("fromAddress") as? Map<*, *>
                    val toAddressMap = document.get("toAddress") as? Map<*, *>
                    val itemMap = document.get("itemDetails") as? Map<*, *>
                    senderPhoneNumber = document.getString("phoneNumber") ?: ""
                    // Parse values
                    val fromAddress = fromAddressMap?.get("fullAddress") as? String ?: "N/A"
                    val fromLat = (fromAddressMap?.get("latitude") as? Number)?.toDouble() ?: 0.0
                    val fromLng = (fromAddressMap?.get("longitude") as? Number)?.toDouble() ?: 0.0

                    val toAddress = toAddressMap?.get("fullAddress") as? String ?: "N/A"
                    val toLat = (toAddressMap?.get("latitude") as? Number)?.toDouble() ?: 0.0
                    val toLng = (toAddressMap?.get("longitude") as? Number)?.toDouble() ?: 0.0

                    val isVerified = document.getBoolean("isVerified") ?: false

                    val itemName = itemMap?.get("itemName") as? String ?: "N/A"
                    val itemInstructions = itemMap?.get("instructions") as? String ?: "N/A"
                    val totalWeight = (itemMap?.get("totalWeight") as? Number)?.toDouble() ?: 0.0
                    val weightGram = (itemMap?.get("weightGram") as? Number)?.toDouble() ?: 0.0
                    val weightKg = (itemMap?.get("weightKg") as? Number)?.toDouble() ?: 0.0

                    Log.d(
                        "FirestoreDebug",
                        "fromAddress: $fromAddress, fromLat: $fromLat, fromLng: $fromLng"
                    )
                    Log.d("FirestoreDebug", "toAddress: $toAddress, toLat: $toLat, toLng: $toLng")
                    Log.d(
                        "FirestoreDebug",
                        "itemName: $itemName, instructions: $itemInstructions, totalWeight: $totalWeight"
                    )
                    Log.d("FirestoreDebug", "isVerified: $isVerified")

                    runOnUiThread {
                        findViewById<TextView>(R.id.tvSenderFromAddress).text =
                            "From Address:\n$fromAddress"
                        findViewById<TextView>(R.id.tvSenderFromCoords).text =
                            "Lat: $fromLat, Lng: $fromLng"

                        findViewById<TextView>(R.id.tvSenderToAddress).text =
                            "To Address:\n$toAddress"
                        findViewById<TextView>(R.id.tvSenderToCoords).text =
                            "Lat: $toLat, Lng: $toLng"

                        findViewById<TextView>(R.id.tvSenderIsVerified).text =
                            "Verified: ${if (isVerified) "Yes" else "No"}"

                        findViewById<TextView>(R.id.tvSenderItemName).text =
                            "Item: $itemName"
                        findViewById<TextView>(R.id.tvSenderItemInstructions).text =
                            "Instructions: $itemInstructions"

                        findViewById<TextView>(R.id.tvSenderWeight).text =
                            "Weight: $totalWeight g ($weightGram g / $weightKg kg)"
                    }

                } else {
                    runOnUiThread {
                        Toast.makeText(this, "No sender details found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showMilesStatus(key: String) {
        println("showMilesStatus called : $senderPhoneNumber ")
        getTravelerForStatus(key);

    }

    private fun getTravelerForStatus(uniqueKey: String) {
        // Get Firestore instance
        val db = FirebaseFirestore.getInstance()

        // Query traveler collection
        db.collection("traveler")
            .whereEqualTo("uniqueKey", uniqueKey)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.w("TravelerQuery", "No traveler found with uniqueKey: $uniqueKey")
                    return@addOnSuccessListener
                }

                // Get first document (assuming uniqueKey is unique)
                val travelerDoc = documents.first()

                try {
                    // Extract required fields with null checks
                    val pickAndDropMode =
                        travelerDoc.getString("pickAndDropMode") ?: "Not specified"
                    val leavingDate = travelerDoc.getString("leavingDate") ?: "Not specified"
                    val leavingTime = travelerDoc.getString("leavingTime") ?: "Not specified"
                    val firstMile = travelerDoc.getString("FirstMileStatus") ?: "Not specified"
                    val secMile = travelerDoc.getString("SecondMileStatus") ?: "Not specified"

                    val LastMile = travelerDoc.getString("LastMileStatus") ?: "Not specified"
                    val firstMileOTP = travelerDoc.getString("FirstMileOTP") ?: "Not specified"
                    val lastMileOTP = travelerDoc.getString("LastMileOTP") ?: "Not specified"
                    //val firstMileCompleted = travelerDoc.getBoolean("FirstMileCompleted") ?: false
                    val phoneNumber = travelerDoc.getString("phoneNumber") ?: "Not specified"
                    // Update UI on main thread
                    runOnUiThread {
                        updateStatusUI_FirstMile(
                            pickAndDropMode,
                            leavingTime,
                            firstMileOTP,
                            firstMile
                        )
                    }



                    if (secMile == "In Progress") {
                        // Update UI on main thread
                        runOnUiThread {
                            updateStatusUI_SecondMile() // need to show the flight tracking link
                        }
                    } else if (secMile == "Not Started") {
                        // Update UI on main thread
                        runOnUiThread {

                            findViewById<TextView>(R.id.tvTransitStatusMain)?.text =
                                "⏳ 2nd Stage - Not Started Yet"
                            findViewById<TextView>(R.id.tvTransitStatus)?.text = ""
                            findViewById<TextView>(R.id.tvLastUpdated)?.text = ""
                        }
                    } else {

                        findViewById<TextView>(R.id.tvTransitStatusMain)?.text =
                            "⏳ 2nd Stage - Completed "
                        findViewById<TextView>(R.id.tvTransitStatus)?.text =
                            "You Reached Home, We are initiating the final pickup process"

                    }

                    if (LastMile == "In Progress") {
                        // Update UI on main thread
                        runOnUiThread {
                            updateStatusUI_LastMile(lastMileOTP, phoneNumber);
                        }
                    } else if (LastMile == "Not Started") {
                        // Update UI on main thread
                        runOnUiThread {
                            findViewById<TextView>(R.id.tvDestinationStatusMain)?.text =
                                "\uD83D\uDCCD 3rd Stage - Not Started Yet"
                            findViewById<TextView>(R.id.tvDestinationStatus)?.text = ""
                            findViewById<TextView>(R.id.tvDeliveryOtp)?.text = ""
                            findViewById<TextView>(R.id.pickerContact)?.text = ""
                        }
                    } else {
                        // for completion of last mile
                        findViewById<TextView>(R.id.tvDestinationStatusMain)?.text =
                            "\uD83D\uDCCD 3rd Stage - Completed"
                        // findViewById<TextView>(R.id.tvDeliveryOtp)?.text = "Delivery OTP: $LMOTP"
                        findViewById<TextView>(R.id.pickerContact)?.text = ""
                        findViewById<TextView>(R.id.tvDestinationStatus)?.text =
                            "Order Completed, Thanks!"
                    }

                    Log.d("TravelerQuery", "Successfully fetched traveler data")

                } catch (e: Exception) {
                    Log.e("TravelerQuery", "Error parsing traveler data", e)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("TravelerQuery", "Error getting traveler document", exception)
            }
    }

    private fun updateStatusUI_FirstMile(
        pickAndDropMode: String,
        leavingTime: String,
        firstMileOTP: String,
        firstMileCompltd: String
    ) {
        // Update status content
        findViewById<TextView>(R.id.subStatus)?.text = when (pickAndDropMode.lowercase()) {
            "self" -> "Sender Will Pick and Drop"
            "company" -> "Company Pick and Drop"
            else -> "Pick/Drop: $pickAndDropMode"
        }

        if (firstMileCompltd == "In Progress") {

            // Update leaving date and time if needed
            findViewById<TextView>(R.id.tvPickupOtp)?.text = "OTP : $firstMileOTP"
            findViewById<TextView>(R.id.tvEta1st)?.text = "ETA : Before $leavingTime"
            findViewById<TextView>(R.id.tvDriverPhone)?.text =
                "Sender Phone: +91 $senderPhoneNumber"

        } else {

            findViewById<TextView>(R.id.fileMileMainStatus)?.text = "✓ 1st Stage - Completed"
            findViewById<TextView>(R.id.tvEta1st)?.text =
                "Parcel received – you’re now flying with it."
            findViewById<TextView>(R.id.tvDriverPhone)?.text = ""

        }


        // You can update other UI elements as needed
    }

    private fun updateStatusUI_SecondMile(

    ) {

        findViewById<TextView>(R.id.tvTransitStatusMain)?.text = "⏳ 2nd Stage - In Transit"
        findViewById<TextView>(R.id.tvTransitStatus)?.text = "You Are Flying With Parcel"
        findViewById<TextView>(R.id.tvLastUpdated)?.text = ""
        // You can update other UI elements as needed
    }


    private fun updateTraveler2ndMileStatus(travelerPhNum: String) {
        // Update UI first
        findViewById<TextView>(R.id.tvTransitStatus)?.text = "⏳ 2nd Stage - Completed"
        findViewById<TextView>(R.id.tvTransitStatusMain)?.text =
            "You Reached Home, We are initiating the final pickup process"

        // Get Firestore reference
        val db = FirebaseFirestore.getInstance()
        val travelerRef = db.collection("traveler").document(travelerPhNum)

        // Create updates for only the status fields
        val statusUpdates = hashMapOf<String, Any>(
            "SecondMileStatus" to "Completed",
            "LastMileStatus" to "In Progress" // Or whatever status you want to set
            // Omit LastMileOTP since we're not changing it
        )

        // Perform the update
        travelerRef.update(statusUpdates)
            .addOnSuccessListener {
                Log.d("Firestore", "Status fields updated successfully")
                // You can add any post-update UI changes here
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error updating status fields", e)
                // You might want to show an error to the user
                findViewById<TextView>(R.id.tvTransitStatus)?.text =
                    "Update failed. Please try again."
            }
    }

    private fun updateStatusUI_LastMile(LMOTP: String, uniqueKey: String) {


        // Setup OTP verification
        val etOtpInput = findViewById<EditText>(R.id.etOtpInput)
        val btnVerifyOtp = findViewById<Button>(R.id.btnVerifyOtp)

        btnVerifyOtp.setOnClickListener {
            val enteredOtp = etOtpInput.text.toString().trim()

            if (enteredOtp.isEmpty()) {
                etOtpInput.error = "Please enter OTP"
                return@setOnClickListener
            }

            if (enteredOtp == LMOTP) {
                // OTP matched
                Toast.makeText(this, "OTP Verified Successfully!", Toast.LENGTH_SHORT).show()


                findViewById<TextView>(R.id.tvDestinationStatus)?.text = "Status: OTP Verified - Order Completed"
                // You can proceed with your next steps here



                // udpate the traveler last mile status and order as complete and move this order to completed traveler db collection

                updateTravelerLastMileStatuses(uniqueKey) // passing the phone number
            } else {
                // OTP didn't match
                Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
                etOtpInput.error = "Wrong OTP"
                etOtpInput.text.clear()
            }
        }
    }


    private fun updateTravelerLastMileStatuses(key: String) {

        findViewById<TextView>(R.id.tvDestinationStatusMain)?.text =
            "\uD83D\uDCCD 3rd Stage - Completed"
        // findViewById<TextView>(R.id.tvDeliveryOtp)?.text = "Delivery OTP: $LMOTP"
        findViewById<TextView>(R.id.pickerContact)?.text = ""
        findViewById<TextView>(R.id.tvDestinationStatus)?.text = "Order Completed, Thanks!"


        val db = FirebaseFirestore.getInstance()

        db.collection("traveler")
            .document(key)
            .update(
                "LastMileStatus", "Completed",  // Use commas instead of 'to'
                "status", "Completed"          // Use commas between key-value pairs
            )
            .addOnSuccessListener {
                Log.d("UpdateStatus", "Traveler $key updated successfully!")
            }
            .addOnFailureListener { e ->
                Log.e("UpdateStatus", "Failed to update traveler $key", e)
            }
    }
}