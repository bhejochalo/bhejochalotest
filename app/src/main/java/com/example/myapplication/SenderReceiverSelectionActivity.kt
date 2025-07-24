package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
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
                        val document = querySnapshot.documents[0]
                        // Get the travelerID field
                        val key = document.getString("uniqueKey")
                        val statusOnTraveler = document.getString("status")
                        println("travelerID: $key")
                        navigateToTravelerProfile(phone,key.toString(),statusOnTraveler.toString())
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
                        val key = document.getString("uniqueKey")
                        println("travelerID: $key")


                        updateToAddressOfAddressHolder(document) // sender to address
                        updateToFromAddressOfAddressHolder(document) // sender from address

                        if (key.isNullOrEmpty()) {
                            println("travelerID === >")
                           navigateToTravelerList(phone,document) // passing the sender
                        }else{
                            navigateToSenderProfile(phone)
                          //  val sender = Sender() // put the sender document in sender class
                            Sender.senderRecord = document;
                        }


                    } else {
                        navigateToUserVerificationActivity(phone)
                    }
                }
                .addOnFailureListener { e ->
                    showError("Failed to check sender: ${e.message}")
                }
        } ?: showError("Phone number is null")
    }

    private fun navigateToTravelerProfile(phone: String,key: String,status: String) {

        Intent(this, TravelerProfile::class.java).apply {
            putExtra("PHONE_NUMBER", phone)
            putExtra("KEY",key)
            putExtra("StatusOnTraveler",status)
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

    private fun navigateToUserVerificationActivity(phone: String) {
        Intent(this, UserVerificationActivity::class.java).apply {
            putExtra("PHONE_NUMBER", phone)
            startActivity(this)
        }
    }
    /**
     * Navigates to SenderDashboardActivity with the sender's phone number
     * @param phone The authenticated sender's phone number (must be non-empty)
     */
    private fun navigateToTravelerList(phone: String,doc: DocumentSnapshot) {


        // Validate input
        if (phone.isBlank()) {
            Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show()
            return
        }

        Intent(this, SenderDashboardActivity::class.java).apply {
            putExtra("PHONE_NUMBER", phone) // passing the sender number
           // putExtra("SenderObject",doc)

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

    private fun updateToAddressOfAddressHolder(doc: DocumentSnapshot){


        val toCity = doc.getString("toAddress.city")
        val toArea = doc.getString("toAddress.area")
        val toStreet = doc.getString("toAddress.street")
        val toHouseNumber = doc.getString("toAddress.houseNumber")
        val toPincode = doc.getString("toAddress.postalCode")
        val toState = doc.getString("toAddress.state")

        toCity?.let { AddressHolder.toCity = it }
        toArea?.let { AddressHolder.toArea = it }

        toStreet?.let { AddressHolder.toStreet = it }
        toHouseNumber?.let { AddressHolder.toHouseNumber = it }

        toPincode?.let { AddressHolder.toPostalCode = it }
        toState?.let { AddressHolder.toState = it }

    }

    private fun updateToFromAddressOfAddressHolder(doc: DocumentSnapshot){

        val fromCity = doc.getString("fromAddress.city")
        val fromArea = doc.getString("fromAddress.area")
        val fromStreet = doc.getString("fromAddress.street")
        val fromHouseNumber = doc.getString("fromAddress.houseNumber")
        val fromPincode = doc.getString("fromAddress.postalCode")
        val fromState = doc.getString("fromAddress.state")

        fromCity?.let { AddressHolder.fromCity = it }
        fromArea?.let { AddressHolder.fromArea = it }

        fromStreet?.let { AddressHolder.fromStreet = it }
        fromHouseNumber?.let { AddressHolder.fromHouseNumber = it }

        fromPincode?.let { AddressHolder.fromPostalCode = it }
        fromState?.let { AddressHolder.fromState = it }

    }
}