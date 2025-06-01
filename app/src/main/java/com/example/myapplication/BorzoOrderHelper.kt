package com.example.myapplication

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BorzoOrderHelper(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()

    fun placeOrder(
        senderId: String,
        travelerAddress: Map<String, String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        getTheRelatedSenderData(senderId) { senderData ->
            val sharedPref = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""

            val senderPhone = formatPhoneForBorzo(senderData["phoneNumber"] ?: "")
            val receiverPhone = formatPhoneForBorzo(phoneNumber)

            if (senderPhone == null || receiverPhone == null) {
                onFailure("Invalid phone number format")
                return@getTheRelatedSenderData
            }

            val toAddress = buildFullAddress(travelerAddress)
            val fromAddress = buildFullAddress(senderData)

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

            val request = Request.Builder()
                .url("https://robotapitest-in.borzodelivery.com/api/business/1.6/create-order")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-DV-Auth-Token", "3F561C810EDAC4F9339582C4BCB9F1A1B3800B87")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("BORZO", "Request failed: ${e.message}")
                    onFailure("Booking failed. Please try again.")
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        Log.d("BORZO", "Response: $responseBody")

                        if (response.isSuccessful) {
                            onSuccess()
                        } else {
                            onFailure("Booking failed: ${response.message}\n$responseBody")
                        }
                    } catch (e: Exception) {
                        Log.e("BORZO", "Error parsing response", e)
                        onFailure("Error processing booking")
                    }
                }
            })
        }
    }

    private fun getTheRelatedSenderData(senderId: String, callback: (Map<String, String?>) -> Unit) {
        db.collection("Sender")
            .whereEqualTo("uniqueKey", senderId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
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
                    callback(senderData)
                } else {
                    callback(emptyMap())
                }
            }
            .addOnFailureListener { e ->
                callback(emptyMap())
            }
    }

    private fun formatPhoneForBorzo(phone: String): String? {
        val digitsOnly = phone.replace("[^0-9]".toRegex(), "")
        return when {
            digitsOnly.length == 10 -> "+91$digitsOnly"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            digitsOnly.startsWith("+") -> phone
            else -> null
        }
    }

    private fun buildFullAddress(components: Map<String, String?>): String {
        return listOfNotNull(
            components["houseNumber"],
            components["street"],
            components["area"],
            components["city"],
            components["state"],
            components["postalCode"]
        ).joinToString(", ")
    }
}