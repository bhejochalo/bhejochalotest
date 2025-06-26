package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.content.SharedPreferences
import android.widget.CheckBox
import android.widget.ImageButton
import java.security.MessageDigest
import android.location.Location

class TravelerAdapter(private val travelers: MutableList<Traveler>, private val selectedPrice: Int) :
    RecyclerView.Adapter<TravelerAdapter.TravelerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TravelerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_traveler_card, parent, false)
        return TravelerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TravelerViewHolder, position: Int) {
        val traveler = travelers[position]
        holder.bind(traveler)
    }

    override fun getItemCount() = travelers.size

    inner class TravelerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvTravelerName)
        private val tvAirline: TextView = itemView.findViewById(R.id.tvAirline)
        private val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        private val tvPnr: TextView = itemView.findViewById(R.id.tvPnr)
        private val btnBook: Button = itemView.findViewById(R.id.btnBook)
        private val tvLeavingTime: TextView = itemView.findViewById(R.id.tvLeavingTime) // Add this
        private val tvWeightUpto: TextView = itemView.findViewById(R.id.tvWeightUpto)
        val sharedPref = itemView.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val senderPhoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""
        private val btnMoreDetails: Button = itemView.findViewById(R.id.btnMoreDetails)
        private val db = FirebaseFirestore.getInstance()
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        val mode =sharedPref.getInt("SELECTED_PRICE", 0) ?: ""
        //    val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        //   val mode = sharedPref.getString("SELECTED_PRICE", "")

        private fun startPayment(traveler: Traveler) {
            val activity = itemView.context as? android.app.Activity ?: return
            val checkout = com.razorpay.Checkout()
            checkout.setKeyID("rzp_test_4HNx49ek9VPhNQ") // Replace with your Razorpay key

            // Convert selected price to paise (₹1 = 100 paise)
            val amountInPaise = selectedPrice * 100

            val options = JSONObject()
            try {
                options.put("name", "Turant")
                options.put("description", "Traveler Booking with ${traveler.name}")
                options.put("currency", "INR")
                options.put("amount", amountInPaise.toString()) // Use the selected price

                val prefill = JSONObject()
                prefill.put("email", "user@example.com")
                prefill.put("contact", "9876543210") // You can use user's actual number

                options.put("prefill", prefill)

                checkout.open(activity, options)
            } catch (e: Exception) {
                Log.e("Razorpay", "Error in starting Razorpay Checkout", e)
                Toast.makeText(itemView.context, "Payment failed to start", Toast.LENGTH_SHORT).show()
            }
        }

        fun bind(traveler: Traveler) {
            tvName.text = traveler.name
            tvAirline.text = "Airline: ${traveler.airline}"
            tvDestination.text = "To: ${traveler.destination}"
            tvPnr.text = "PNR: ${traveler.pnr}"
            tvLeavingTime.text = "Leaving Time: ${traveler.leavingTime}"
            tvWeightUpto.text = "Weight Upto: ${traveler.weightUpto} kg"
            tvDistance.text = "%.1f km".format(traveler.distance)
            when (traveler.bookingStatus) {
                "available" -> {
                    btnBook.text = "Book"
                    btnBook.isEnabled = true
                    btnBook.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                }
                "pending" -> {
                    btnBook.text = "Pending"
                    btnBook.isEnabled = false
                    btnBook.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.grey_500))
                }
                "booked" -> {
                    btnBook.text = "Booked"
                    btnBook.isEnabled = false
                    btnBook.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.grey_500))
                }
            }
            btnMoreDetails.setOnClickListener {
                showMoreDetailsDialog(traveler)
            }
            btnBook.setOnClickListener {
                if (traveler.bookingStatus == "available") {
                    showConfirmationDialog(traveler)
                }
            }
        }
        private fun showMoreDetailsDialog(traveler: Traveler) {
            val dialogView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.dialog_traveler_details, null)

            // Initialize views
            val tvDialogName: TextView = dialogView.findViewById(R.id.tvDialogName)
            val tvDialogRoute: TextView = dialogView.findViewById(R.id.tvDialogRoute)
            val tvDialogAirline: TextView = dialogView.findViewById(R.id.tvDialogAirline)
            val tvDialogPnr: TextView = dialogView.findViewById(R.id.tvDialogPnr)
            val tvDialogLeavingTime: TextView = dialogView.findViewById(R.id.tvDialogLeavingTime)
            val tvDialogWeight: TextView = dialogView.findViewById(R.id.tvDialogWeight)
            val tvDialogFlightNumber: TextView = dialogView.findViewById(R.id.tvDialogFlightNumber)
            val tvDialogArrivalTime: TextView = dialogView.findViewById(R.id.tvDialogArrivalTime)
            val tvDialogDuration: TextView = dialogView.findViewById(R.id.tvDialogDuration)
            val tvDialogPrice: TextView = dialogView.findViewById(R.id.tvDialogPrice)
            val tvNotAcceptedItems: TextView = dialogView.findViewById(R.id.tvNotAcceptedItems)
            val cbTerms: CheckBox = dialogView.findViewById(R.id.cbTerms)
            val btnBookInDialog: Button = dialogView.findViewById(R.id.btnBookInDialog)
            val btnClose: ImageButton = dialogView.findViewById(R.id.btnClose)

            // Set traveler details
            tvDialogName.text = traveler.name
            tvDialogRoute.text = "${traveler.destination}"
            tvDialogAirline.text = "Airline: ${traveler.airline}"
            tvDialogPnr.text = "PNR: ${traveler.pnr}"
            tvDialogLeavingTime.text = "Departure: ${traveler.leavingTime}"
            tvDialogArrivalTime.text = "Arrival: ${traveler.arrivalTime}"
            tvDialogWeight.text = "Available Weight: ${traveler.weightUpto} kg"
            tvDialogFlightNumber.text = "Flight: ${traveler.flightNumber}"
            tvDialogDuration.text = "Duration: ${traveler.flightDuration}"
            tvDialogPrice.text = "Price: ₹${traveler.price}"

            // Set not accepted items
            tvNotAcceptedItems.text = if (traveler.notAcceptedItems.isNotEmpty()) {
                traveler.notAcceptedItems.joinToString(", ")
            } else {
                "None specified"
            }

            // Terms checkbox listener
            cbTerms.setOnCheckedChangeListener { _, isChecked ->
                btnBookInDialog.isEnabled = isChecked
                btnBookInDialog.backgroundTintList = if (isChecked) {
                    ContextCompat.getColorStateList(itemView.context, R.color.green_500)
                } else {
                    ContextCompat.getColorStateList(itemView.context, R.color.grey_500)
                }
            }

            // Set initial state
            btnBookInDialog.isEnabled = false
            btnBookInDialog.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.grey_500)

            val dialog = AlertDialog.Builder(itemView.context)
                .setView(dialogView)
                .create()

            // Close button click
            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            // Book button click
            btnBookInDialog.setOnClickListener {
                dialog.dismiss()
                traveler.bookingStatus = "pending"
                notifyItemChanged(adapterPosition)
                startPayment(traveler)
                attachTravelerWithSender(traveler)
                attachSenderWithTraveler(traveler)
            }

            dialog.show()
        }
        private fun showConfirmationDialog(traveler: Traveler) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Confirm Booking")
                .setMessage("Are you sure you want to book with ${traveler.name}?")
                .setPositiveButton("Yes") { dialog, which ->
                    traveler.bookingStatus = "pending"
                    notifyItemChanged(adapterPosition)
                  //  placeBorzoOrder()
                    startPayment(traveler)
                    attachTravelerWithSender(traveler); // need to pass the traveler // these has to be matched after the payment is successful2
                    attachSenderWithTraveler(traveler)  //
                    // update the respected traveler in the db and attach this sender details lookup if possible

                    val intent = Intent(itemView.context, TravelerProfile::class.java).apply {
                        //putExtra("TRAVELER_DATA", traveler) // If Traveler is Parcelable
                    }
                    itemView.context.startActivity(intent)
                }
                // yaha per traveler ka record get hoga phone number se, and uska anyReq true ho jayega.
                .setNegativeButton("Cancel", null)
                .show()
        }

       /* will uncomment this later

        private fun placeBorzoOrder() {
            val sharedPref = itemView.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""

            val fromAddress = buildFullAddress(
                AddressHolder.fromHouseNumber,
                AddressHolder.fromStreet,
                AddressHolder.fromArea,
                AddressHolder.fromCity,
                AddressHolder.fromState,
                AddressHolder.fromPostalCode
            )

            val toAddress = buildFullAddress(
                AddressHolder.toHouseNumber,
                AddressHolder.toStreet,
                AddressHolder.toArea,
                AddressHolder.toCity,
                AddressHolder.toState,
                AddressHolder.toPostalCode
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
                            put("phone", AddressHolder.phoneNumber ?: "")
                            put("name", "Sender")
                        })
                    })
                    put(JSONObject().apply {
                        put("address", toAddress)
                        put("contact_person", JSONObject().apply {
                            put("phone", phoneNumber)
                            put("name", "Receiver")
                        })
                    })
                })
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://robotapitest-in.borzodelivery.com/api/business/1.6/create-order")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-DV-Auth-Token", "3F561C810EDAC4F9339582C4BCB9F1A1B3800B87")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("BORZO", "Request failed: ${e.message}")
                    updateUIOnFailure()
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        Log.d("BORZO", "Response: $responseBody")

                        if (response.isSuccessful) {
                            updateUIOnSuccess()
                        } else {
                            updateUIOnFailure()
                        }
                    } catch (e: Exception) {
                        Log.e("BORZO", "Error parsing response", e)
                        updateUIOnFailure()
                    }
                }

                private fun updateUIOnSuccess() {
                    itemView.post {
                        travelers[adapterPosition].bookingStatus = "booked"
                        notifyItemChanged(adapterPosition)
                        Toast.makeText(
                            itemView.context,
                            "Booking successful!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                private fun updateUIOnFailure() {
                    itemView.post {
                        travelers[adapterPosition].bookingStatus = "available"
                        notifyItemChanged(adapterPosition)
                        Toast.makeText(
                            itemView.context,
                            "Booking failed. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }
*/
// Function to compute SHA-256 hash with an optional prefix
       fun generateUniqueKey(senderPhone: String, travelerPhone: String, prefix: String = ""): String {
           // 1. Normalize phone numbers (remove non-digits)
           val cleanSender = senderPhone.replace("[^0-9]".toRegex(), "")
           val cleanTraveler = travelerPhone.replace("[^0-9]".toRegex(), "")

           // 2. Sort and combine to ensure consistency
           val combined = if (cleanSender < cleanTraveler) "$cleanSender|$cleanTraveler"
           else "$cleanTraveler|$cleanSender"

           // 3. Compute SHA-256 hash
           val bytes = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
           val hexHash = bytes.joinToString("") { "%02x".format(it) }

           // 4. Add prefix (e.g., "TX_") and return
           return prefix + hexHash
       }

        private fun attachTravelerWithSender(traveler: Traveler){
           // val sharedPref = itemView.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
         //   val senderPhoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""

            val uniqueKey = generateUniqueKey(senderPhoneNumber, traveler.phoneNumber, prefix = "TX_")

            var modeOfPickAndDrop = ""

            println("mode ===> $mode") // 750 means self, 1500 means auto

            if(mode == 750){
                modeOfPickAndDrop = "self"

            }else{

                modeOfPickAndDrop = "auto"
            }



            println("uniqueKey ===> $uniqueKey")

            val updates = hashMapOf<String, Any>(
                "SenderRequest" to true,
                "uniqueKey" to uniqueKey,
                "matchedAt" to System.currentTimeMillis(),
                "pickAndDropMode" to modeOfPickAndDrop
                //"mode" to "self" // for  self pickup & drop

            )
            // Query traveler document by phoneNumber // check how we can remove this query. need to remove this query
            /*db.collection("traveler")
                .whereEqualTo("phoneNumber", travelerPhoneNumber)
                .limit(1) // Since phone numbers should be unique
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) { */
                       //val travelerDoc = traveler //querySnapshot.documents[0]

            traveler.documentSnapshot?.reference?.update(updates)
                ?.addOnSuccessListener {
                    Log.d("Firestore", "Fields updated successfully!")
                }
                ?.addOnFailureListener { e ->
                    Log.e("Firestore", "Error updating fields", e)
                }


                }


        private fun attachSenderWithTraveler(traveler: Traveler) {
            // Get sender phone number from SharedPreferences
           // val sharedPref = itemView.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
           // val senderPhoneNumber = sharedPref.getString("PHONE_NUMBER", null)

            val uniqueKey = generateUniqueKey(senderPhoneNumber, traveler.phoneNumber, prefix = "TX_")


            if (senderPhoneNumber.isNullOrEmpty()) {
                Toast.makeText(itemView.context, "Sender phone number not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Fetch and update sender document
            db.collection("Sender")
                .whereEqualTo("phoneNumber", senderPhoneNumber)
                .limit(1)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val querySnapshot = task.result
                        if (!querySnapshot.isEmpty) {
                            val document = querySnapshot.documents[0]

                            val updates = hashMapOf<String, Any>(
                                "uniqueKey" to uniqueKey,
                               // "travelerPNR" to traveler.pnr,  // Adding PNR for reference
                                "matchedAt" to System.currentTimeMillis()
                              //  "travelerName" to traveler.name,
                             //   "travelerDestination" to traveler.destination,
                              //  "status" to "matched"  // Additional status field
                            )

                            document.reference.update(updates)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        itemView.context,
                                        "Successfully matched with traveler!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Log.d("Firestore", "Sender ${document.id} updated with traveler info")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("Firestore", "Update failed", e)
                                    Toast.makeText(
                                        itemView.context,
                                        "Failed to update your profile",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                itemView.context,
                                "Your sender profile not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.e("Firestore", "Query failed", task.exception)
                        Toast.makeText(
                            itemView.context,
                            "Failed to verify your profile: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
       /* private fun buildFullAddress(
            houseNumber: String?,
            street: String?,
            area: String?,
            city: String?,
            state: String?,
            postalCode: String?
        ): String {
            return listOfNotNull(houseNumber, street, area, city, state, postalCode)
                .joinToString(", ")
        } */
    }
}