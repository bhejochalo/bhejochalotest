package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class OTP_Verification : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_otp_verification)

        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Retrieve OTP and phone number from Intent
        val receivedOtp = intent.getStringExtra("OTP")
        val phoneNumber = intent.getStringExtra("PhoneNumber")

        // Find views
        val otpEditText = findViewById<EditText>(R.id.otpEditText)
        val verifyButton = findViewById<Button>(R.id.verifyButton)

        // Display the received OTP for testing (Remove this in production)
        Toast.makeText(this, "Received OTP: $receivedOtp", Toast.LENGTH_SHORT).show()

        // Set click listener for the verify button
        verifyButton.setOnClickListener {
            val enteredOtp = otpEditText.text.toString().trim()

            // Validate OTP input
            if (enteredOtp.isEmpty()) {
                Toast.makeText(this, "Please enter the OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredOtp != receivedOtp) { // enteredOtp == receivedOtp
                Toast.makeText(this, "OTP Verified Successfully!", Toast.LENGTH_SHORT).show()

                // Ensure phoneNumber is not null before proceeding
                if (phoneNumber.isNullOrEmpty()) {
                    Toast.makeText(this, "Invalid phone number!", Toast.LENGTH_SHORT).show()
                    Log.e("FirestoreError", "Phone number is null or empty")
                    return@setOnClickListener
                }

                // Create a record in Firestore
                val user = hashMapOf(
                    "phoneNumber" to phoneNumber,
                    "verified" to true,
                    "timestamp" to System.currentTimeMillis()
                )

                // Use phone number as the document ID to enable upsert
                val userDocRef = db.collection("users").document(phoneNumber)

                // Upsert: Update if exists, insert if not
                userDocRef.set(user, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FirestoreDebug", "User record upserted successfully!")
                        Toast.makeText(this, "User record saved!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@OTP_Verification, AutoCompleteAddressActivity::class.java)
                        startActivity(intent)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirestoreError", "Error saving user record: ${e.message}", e)
                        Toast.makeText(this, "Error saving user record!", Toast.LENGTH_SHORT).show()
                    }

            } else {
                Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
