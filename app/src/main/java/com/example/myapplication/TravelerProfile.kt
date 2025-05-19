package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore // Assuming you're using Firestore

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
        db.collection("travelers").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Populate the TextViews with data from Firestore
                  /*  val tvFromAddress = findViewById<TextView>(R.id.tvFromAddress)
                    val tvToAddress = findViewById<TextView>(R.id.tvToAddress)

                    val fromAddress = """
                        ${document.getString("fromHouseNumber")}, 
                        ${document.getString("fromStreet")}, 
                        ${document.getString("fromArea")}, 
                        ${document.getString("fromCity")}, 
                        ${document.getString("fromState")} - ${document.getString("fromPostalCode")}
                    """.trimIndent()

                    val toAddress = """
                        ${document.getString("toHouseNumber")}, 
                        ${document.getString("toStreet")}, 
                        ${document.getString("toArea")}, 
                        ${document.getString("toCity")}, 
                        ${document.getString("toState")} - ${document.getString("toPostalCode")}
                    """.trimIndent()

                    tvFromAddress.text = fromAddress
                    tvToAddress.text = toAddress */

                    println("document ===> $document")



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
}