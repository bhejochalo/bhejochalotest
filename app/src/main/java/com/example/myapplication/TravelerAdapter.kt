package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TravelerAdapter(private val travelers: MutableList<Traveler>) :
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

        fun bind(traveler: Traveler) {
            tvName.text = traveler.name
            tvAirline.text = "Airline: ${traveler.airline}"
            tvDestination.text = "To: ${traveler.destination}"
            tvPnr.text = "PNR: ${traveler.pnr}"

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

            btnBook.setOnClickListener {
                if (traveler.bookingStatus == "available") {
                    showConfirmationDialog(traveler)
                }
            }
        }

        private fun showConfirmationDialog(traveler: Traveler) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Confirm Booking")
                .setMessage("Are you sure you want to book with ${traveler.name}?")
                .setPositiveButton("Yes") { dialog, which ->
                    traveler.bookingStatus = "pending"
                    notifyItemChanged(adapterPosition)
                    placeBorzoOrder()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun placeBorzoOrder() {
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
                            put("phone", AddressHolder.phoneNumber ?: "")
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

        private fun buildFullAddress(
            houseNumber: String?,
            street: String?,
            area: String?,
            city: String?,
            state: String?,
            postalCode: String?
        ): String {
            return listOfNotNull(houseNumber, street, area, city, state, postalCode)
                .joinToString(", ")
        }
    }
}