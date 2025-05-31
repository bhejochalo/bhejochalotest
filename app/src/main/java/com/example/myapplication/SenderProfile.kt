package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.Sender // Replace with your actual package


class SenderProfile : AppCompatActivity() {
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

        // Display addresses (from AddressHolder)
        displayAddresses()

        // Display sender booking details
        displaySenderBookingDetails()
    }

    private fun displayAddresses() {
        val tvFromAddress = findViewById<TextView>(R.id.tvFromAddress)
        val tvToAddress = findViewById<TextView>(R.id.tvToAddress)

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

        tvFromAddress.text = fromAddress
        tvToAddress.text = toAddress
    }

    private fun displaySenderBookingDetails() {
        val tvStatus = findViewById<TextView>(R.id.bookingStatus)

        val document = Sender.senderRecord
        if (document != null && document.exists()) {
            // Extract data from Firestore document

            val status = document.getString("status") ?: "N/A"

            // Update UIx

            tvStatus.text = "Status: $status"

            Log.d("SenderProfile", "Loaded booking details:  $status")
        } else {
            tvStatus.text = "No booking data found!"
            Log.e("SenderProfile", "Sender document is null or doesn't exist")
        }
    }
}