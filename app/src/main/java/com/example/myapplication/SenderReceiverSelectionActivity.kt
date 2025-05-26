package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class SenderReceiverSelectionActivity : AppCompatActivity() {
    private var phoneNumber: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val sharedPref by lazy { getSharedPreferences("UserPrefs", MODE_PRIVATE) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_receiver_selection)

        phoneNumber = intent.getStringExtra("PHONE_NUMBER")?.also {
            Log.d("SenderReceiver", "Received phone: $it")
        } ?: run {
            Toast.makeText(this, "Phone number not received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.senderButton).setOnClickListener {
            handleSender()
        }

        findViewById<Button>(R.id.travelerButton).setOnClickListener {
            handleTraveler()
        }
    }

    private fun handleTraveler() {
        sharedPref.edit().putString("USER_TYPE", "TRAVELER").apply()

        phoneNumber?.let { phone ->
            db.collection("traveler")
                .whereEqualTo("phoneNumber", phone)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        println("existing traveler ")
                        navigateToTravelerProfile(phone)
                    } else {
                        println("new traveler ")
                        navigateToAutoComplete(phone)
                    }
                }
                .addOnFailureListener { e ->
                    showError("Failed to check traveler: ${e.message}")
                }
        } ?: showError("Phone number is null")
    }

    private fun handleSender() {
        sharedPref.edit().putString("USER_TYPE", "SENDER").apply()
        phoneNumber?.let { phone ->
            db.collection("Sender")
                .whereEqualTo("phoneNumber", phone)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {

                        val document = querySnapshot.documents[0]
                        // Get the travelerID field
                        val travelerID = document.getString("travelerID")
                        println("travelerID: $travelerID")

                        if (travelerID.isNullOrEmpty()) {
                            println("travelerID === >")
                           navigateToTravelerList(phone)
                        }


                        navigateToSenderProfile(phone)
                    } else {
                        navigateToAutoComplete(phone)
                    }
                }
                .addOnFailureListener { e ->
                    showError("Failed to check sender: ${e.message}")
                }
        } ?: showError("Phone number is null")
    }

    private fun navigateToTravelerProfile(phone: String) {
        Intent(this, TravelerProfile::class.java).apply {
            putExtra("PHONE_NUMBER", phone)
            startActivity(this)
        }
    }

    private fun navigateToSenderProfile(phone: String) {
        Intent(this, SenderProfile::class.java).apply {
            putExtra("PHONE_NUMBER", phone)
            startActivity(this)
        }
    }

    private fun navigateToAutoComplete(phone: String) {
        Intent(this, AutoCompleteAddressActivity::class.java).apply {
            putExtra("PHONE_NUMBER", phone)
            startActivity(this)
        }
    }
    /**
     * Navigates to SenderDashboardActivity with the sender's phone number
     * @param phone The authenticated sender's phone number (must be non-empty)
     */
    private fun navigateToTravelerList(phone: String) {
        // Validate input
        if (phone.isBlank()) {
            Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show()
            return
        }

        Intent(this, SenderDashboardActivity::class.java).apply {
            putExtra("PHONE_NUMBER", phone)

            // Optional flags to control navigation behavior:
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP // Prevents multiple instances

            // For fresh start (uncomment if needed):
            // flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(this)

            // Optional transition animation
           // overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.e("SenderReceiver", message)
    }
}