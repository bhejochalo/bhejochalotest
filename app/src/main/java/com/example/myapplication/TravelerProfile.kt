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
        placeBorzoOrder()

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


    private fun placeBorzoOrder() {
        println("in borzo place order")
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""

        // Format phone numbers according to Borzo requirements
        val senderPhone = formatPhoneForBorzo("918696888060")
        val receiverPhone = formatPhoneForBorzo("918696888060") // Your test receiver number

        if (senderPhone == null || receiverPhone == null) {
            runOnUiThread {
                Toast.makeText(
                    this@TravelerProfile,
                    "Invalid phone number format",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        val fromAddress = buildFullAddress(
            "904",
            "gurudatta nagar",
            "Hadapsar",
            "Pune",
            "maharashtra",
            "412308"
        )

        val toAddress = buildFullAddress(
            "999 green crest",
            "fursungi nagar",
            "Hadapsar",
            "Pune",
            "maharashtra",
            "412308"
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val jsonBody = JSONObject().apply {
            put("matter", "Documents")
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
}