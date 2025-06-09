package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.provider.SyncStateContract.Helpers.update
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore // Assuming you're using Firestore
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
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreException


class TravelerProfile : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private var houseNumber_Traveler = "";
    private var street_Traveler = "";
    private var area_Traveler = "";
    private var city_Traveler = "";
    private var pincode_Traveler = "";
    private var state_Traveler = "";

    private var houseNumber_Sender = "";
    private var street_Sender = "";
    private var area_Sender = "";
    private var city_Sender = "";
    private var pincode_Sender = "";
    private var state_Sender = "";
    private var isFirstMile = false;
    //private var uniqueKey = "";
    private lateinit var borzoHelper: BorzoOrderHelper

    override fun onCreate(savedInstanceState: Bundle?) {



        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traveler_profile)
        enableEdgeToEdge()
            borzoHelper = BorzoOrderHelper(this)

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
        // Get current user ID - you'll need to implement this based on your auth system
        val currentUserId = getCurrentUserId() ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        println("currentUserId ===> $currentUserId")
        db.collection("traveler").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Populate the TextViews with data from Firestore
                    println("document exists == >")
                    val tvFromAddress = findViewById<TextView>(R.id.tvFromAddress)
                    val tvToAddress = findViewById<TextView>(R.id.tvToAddress)

                    val fromAddress = """
                        ${document.getString("fromAddress.fullAddress")} 
                     
                    """.trimIndent()

                    val toAddress = """
                        ${document.getString("toAddress.fullAddress")}
                      
                    """.trimIndent()



                    tvFromAddress.text = fromAddress
                    tvToAddress.text = toAddress

                    println("document ===> ")

                    firstMileAddressTraveler(document); // added by himanshu for the first mile address of traveler



                    val uniqueKey = "${document.getString("uniqueKey")}"
                    val status = "${document.getString("status")}"
                   // val hasReq = "${document.getString("SenderRequest")}"
                    println("isSenderIDPresent ===> $uniqueKey")

                    if (uniqueKey.isNotEmpty() && status != "Request Accepted By Traveler") {
                        showSenderRequest(uniqueKey,document) // method to show the sender request to Traveler
                    }else {
                        fetchTheAcceptedOrder(uniqueKey) // show the order details to the traveler

                    }


                } else {
                    Toast.makeText(this, "Traveler data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching traveler data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun getCurrentUserId(): String? {
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val phoneNumber = sharedPref.getString("PHONE_NUMBER", "")

        return phoneNumber //
    }
    private fun showSenderRequest(senderRef: String, document: DocumentSnapshot) {  // Removed context parameter since we're in Activity
        println("inside showSenderRequest ===> $senderRef")


        println("inside showSenderRequest ===> Hardcoded data for testing")

        // Hardcoded sender data
        val name = "John Doe"
        val phone = "+1 555-123-4567"
        val address = "123 Main St, New York, NY 10001"

        runOnUiThread {
            try {
                if (!isFinishing) {
                    showSenderDialog(name, phone, address, senderRef,document)
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


    private fun showSenderDialog(name: String, phone: String, address: String, senderRef: String, document: DocumentSnapshot) {
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
                    onAcceptRequest(senderRef,document)
                    dialog.dismiss()
                }
                .setNegativeButton("Reject") { dialog, _ ->
                    onRejectRequest(senderRef,document)
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


        document.reference.update("status", "Request Accepted By Traveler") // // updating the traveler status
            .addOnSuccessListener {
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Request Accepted", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
       }



    private fun onRejectRequest(senderRef: String,document: DocumentSnapshot) { // in document we have current traveler

        document.reference.update("status", "Request Rejected By Traveler") // updating the traveler status
            .addOnSuccessListener {
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Request Rejected", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this@TravelerProfile, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun firstMileAddressTraveler(document: DocumentSnapshot){
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

    private fun lastMileAddressTraveler(){
      // traveler to address will be from address in last mile

    }

    private fun updateBookingDetailsOnSender(response: Response){

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


    private fun updateBookingDetailsOnTraveler(response: Response){

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


}