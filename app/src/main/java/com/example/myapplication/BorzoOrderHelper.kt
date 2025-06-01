package com.example.myapplication

import android.content.Context
import android.util.Log
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
            try {
                val (senderPhone, receiverPhone) = getValidatedPhoneNumbers(senderData)
                    ?: throw IllegalArgumentException("Invalid phone number format")

                val toAddress = buildFullAddress(travelerAddress)
                val fromAddress = buildFullAddress(senderData)

                val request = createBorzoRequest(fromAddress, toAddress, senderPhone, receiverPhone)
                executeBorzoRequest(request, onSuccess, onFailure)
            } catch (e: Exception) {
                Log.e("BORZO", "Order preparation failed", e)
                onFailure(e.message ?: "Failed to prepare order")
            }
        }
    }

    private fun getValidatedPhoneNumbers(senderData: Map<String, String?>): Pair<String, String>? {
        val sharedPref = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""

        val senderPhone = formatPhoneForBorzo(senderData["phoneNumber"] ?: "")
        val receiverPhone = formatPhoneForBorzo(phoneNumber)

        return if (senderPhone != null && receiverPhone != null) {
            Pair(senderPhone, receiverPhone)
        } else {
            null
        }
    }

    private fun createBorzoRequest(
        fromAddress: String,
        toAddress: String,
        senderPhone: String,
        receiverPhone: String
    ): Request {
        val jsonBody = JSONObject().apply {
            put("matter", "Documents")
            put("vehicle_type_id", 2)
            put("points", JSONArray().apply {
                put(createPointJson(fromAddress, senderPhone, "Sender"))
                put(createPointJson(toAddress, receiverPhone, "Receiver"))
            })
        }.toString()

        return Request.Builder()
            .url("https://robotapitest-in.borzodelivery.com/api/business/1.6/create-order")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-DV-Auth-Token", "3F561C810EDAC4F9339582C4BCB9F1A1B3800B87")
            .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    private fun createPointJson(address: String, phone: String, name: String): JSONObject {
        return JSONObject().apply {
            put("address", address)
            put("contact_person", JSONObject().apply {
                put("phone", phone)
                put("name", name)
            })
        }
    }

    private fun executeBorzoRequest(
        request: Request,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("BORZO", "Request failed", e)
                    onFailure("Network error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    handleBorzoResponse(response, onSuccess, onFailure)
                }
            })
    }

    private fun handleBorzoResponse(
        response: Response,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val responseBody = response.body?.string()
            Log.d("BORZO", "Response: ${response.code} - $responseBody")

            when {
                !response.isSuccessful -> {
                    onFailure("API error: ${response.code} - ${response.message}")
                }
                responseBody == null -> {
                    onFailure("Empty response from server")
                }
                else -> {
                    parseAndSaveResponse(responseBody, onSuccess, onFailure)
                }
            }
        } catch (e: Exception) {
            Log.e("BORZO", "Response handling failed", e)
            onFailure("Failed to process response: ${e.message}")
        }
    }

    private fun parseAndSaveResponse(
        json: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val orderResponse = parseBorzoResponse(json) ?: throw Exception("Invalid response format")
            BorzoFirestoreHelper.saveOrderResponse(orderResponse)
            onSuccess()
        } catch (e: Exception) {
            Log.e("BORZO", "Failed to parse/save response", e)
            onFailure("Failed to save order: ${e.message}")
        }
    }

    private fun parseBorzoResponse(json: String): BorzoModels.BorzoOrderResponse? {
        val jsonObject = JSONObject(json)

        return BorzoModels.BorzoOrderResponse(
            isSuccessful = jsonObject.optBoolean("is_successful", false),
            errors = jsonObject.optJSONArray("errors")?.toStringList(),
            parameterErrors = jsonObject.optJSONObject("parameter_errors")?.toParameterErrors(),
            order = jsonObject.getJSONObject("order").toOrderDetail()
        )
    }

    private fun JSONObject.toOrderDetail(): BorzoModels.OrderDetail {
        return BorzoModels.OrderDetail(
            orderId = getString("order_id"),
            orderName = optString("order_name"),
            type = optString("type"),
            vehicleTypeId = optInt("vehicle_type_id"),
            createdDatetime = optString("created_datetime"),
            status = optString("status"),
            statusDescription = optString("status_description"),
            paymentAmount = optDouble("payment_amount"),
            clientId = optString("client_id"),
            points = getJSONArray("points").toOrderPoints(),
            courier = optJSONObject("courier")?.toCourier(),
            matter = optString("matter", ""),
            totalWeightKg = optDouble("total_weight_kg", 0.0),
            isClientNotificationEnabled = optBoolean("is_client_notification_enabled", false),
            isContactPersonNotificationEnabled = optBoolean("is_contact_person_notification_enabled", false),
            loadersCount = optInt("loaders_count", 0),
            paymentMethod = optString("payment_method", ""),
            isReturnRequired = optBoolean("is_return_required", false),
            deliveryFeeAmount = optDouble("delivery_fee_amount", 0.0),
            weightFeeAmount = optDouble("weight_fee_amount", 0.0),
            insuranceAmount = optDouble("insurance_amount", 0.0),
            insuranceFeeAmount = optDouble("insurance_fee_amount", 0.0),
            loadingFeeAmount = optDouble("loading_fee_amount", 0.0),
            moneyTransferFeeAmount = optDouble("money_transfer_fee_amount", 0.0),
            doorToDoorFeeAmount = optDouble("door_to_door_fee_amount", 0.0),
            promoCodeDiscountAmount = optDouble("promo_code_discount_amount", 0.0),
            backpaymentAmount = optDouble("backpayment_amount", 0.0),
            codFeeAmount = optDouble("cod_fee_amount", 0.0),
            returnFeeAmount = optDouble("return_fee_amount", 0.0),
            waitingFeeAmount = optDouble("waiting_fee_amount", 0.0)
            //takingAmount = optDouble("taking_amount", 0.0),
          //  buyoutAmount = optDouble("buyout_amount", 0.0),
          //  previousPointDrivingDistanceMeters = optInt("previous_point_driving_distance_meters", 0)
        )
    }
    private fun JSONArray.toOrderPoints(): List<BorzoModels.OrderPoint> {
        return List(length()) { i ->
            getJSONObject(i).toOrderPoint()
        }
    }

    private fun JSONObject.toOrderPoint(): BorzoModels.OrderPoint {
        return BorzoModels.OrderPoint(
            pointId = getString("point_id"),
            pointType = optString("point_type"),
            address = optString("address"),
            contactPerson = getJSONObject("contact_person").toContactPerson(),
            packages = optJSONArray("packages")?.toPackages() ?: emptyList(),
            // Add other required fields with default values
            latitude = optString("latitude", ""),
            longitude = optString("longitude", ""),
            requiredStartDatetime = optString("required_start_datetime", ""),
            requiredFinishDatetime = optString("required_finish_datetime", ""),
            trackingUrl = optString("tracking_url", "")
        )
    }

    private fun JSONObject.toContactPerson(): BorzoModels.ContactPerson {
        return BorzoModels.ContactPerson(
            name = optString("name"),
            phone = optString("phone")
        )
    }

    private fun JSONArray.toPackages(): List<BorzoModels.Package> {
        return List(length()) { i ->
            getJSONObject(i).toPackage()
        }
    }

    private fun JSONObject.toPackage(): BorzoModels.Package {
        return BorzoModels.Package(
            packageId = optString("package_id"),
            weightKg = optDouble("weight_kg"),
            description = optString("description")
        )
    }

    private fun JSONObject.toCourier(): BorzoModels.Courier {
        return BorzoModels.Courier(
            courierId = optString("courier_id"),
            name = optString("name"),
            phone = optString("phone"),
            vehicleType = optString("vehicle_type"),
            rating = optDouble("rating")
        )
    }

    private fun JSONArray?.toStringList(): List<String>? {
        return this?.let { array ->
            List(array.length()) { i -> array.getString(i) }
        }
    }

    private fun JSONObject?.toParameterErrors(): Map<String, List<String>>? {
        return this?.let { obj ->
            mutableMapOf<String, List<String>>().apply {
                obj.keys().forEach { key ->
                    put(key, obj.getJSONArray(key).toStringList() ?: emptyList())
                }
            }
        }
    }

    // Rest of your existing helper methods remain unchanged
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