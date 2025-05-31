package com.example.myapplication

import android.content.Context
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_profile)
        enableEdgeToEdge()

        // Set up window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Check if AddressHolder has data
        if (AddressHolder.fromHouseNumber != null) {
            displayAddressFromHolder()
        } else {
            // If AddressHolder is null, fetch from database
            getTheCurrentTravelerData()
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



                    val SenderIDPresent = "${document.getString("senderId")}"
                    println("isSenderIDPresent ===> $SenderIDPresent")

                    if (SenderIDPresent.isNotEmpty()) {
                        showSenderRequest(SenderIDPresent)
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
    private fun showSenderRequest(senderRef: String) {  // Removed context parameter since we're in Activity
        println("inside showSenderRequest ===> $senderRef")


        println("inside showSenderRequest ===> Hardcoded data for testing")

        // Hardcoded sender data
        val name = "John Doe"
        val phone = "+1 555-123-4567"
        val address = "123 Main St, New York, NY 10001"

        runOnUiThread {
            try {
                if (!isFinishing) {
                    showSenderDialog(name, phone, address, senderRef)
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


    private fun showSenderDialog(name: String, phone: String, address: String, senderRef: String) {
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
                    onAcceptRequest(senderRef)
                    dialog.dismiss()
                }
                .setNegativeButton("Reject") { dialog, _ ->
                    onRejectRequest(senderRef)
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

    private fun onAcceptRequest(senderRef: String) {
        isFirstMile = true;
        placeBorzoOrder(senderRef) // need to pass the other details also, address of traveler

    }

    private fun onRejectRequest(senderRef: String) {
        FirebaseFirestore.getInstance().document(senderRef)
            .update("status", "Rejected")
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
            val senderPhone = formatPhoneForBorzo(senderId)
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

            val fromAddress = buildFullAddress(
                houseNumber_Traveler,
                street_Traveler,
                area_Traveler,
                city_Traveler,
                state_Traveler,
                pincode_Traveler
            )

            val toAddress = buildFullAddress(
                senderData["houseNumber"] ?: "",
                senderData["street"] ?: "",
                senderData["area"] ?: "",
                senderData["city"] ?: "",
                senderData["state"] ?: "",
                senderData["postalCode"] ?: ""
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
        println("currentUserId in Sender ===> $senderId")
        db.collection("Sender").document(senderId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val senderData = hashMapOf<String, String?>(
                        "houseNumber" to document.getString("fromAddress.houseNumber"),
                        "street" to document.getString("fromAddress.street"),
                        "area" to document.getString("fromAddress.area"),
                        "city" to document.getString("fromAddress.city"),
                        "postalCode" to document.getString("fromAddress.postalCode"),
                        "state" to document.getString("fromAddress.state")
                    )


                    // last mile sender address will come here

                    // Update the class fields
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
                    println("Pincodesender = : ${senderData["postalCode"]}") */

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

    private fun firstMileAddressTraveler(document: DocumentSnapshot){

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
}