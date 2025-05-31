package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class ItemDetailsActivity : AppCompatActivity() {

    private lateinit var itemNameEditText: EditText
    private lateinit var kgEditText: TextInputEditText
    private lateinit var gramEditText: TextInputEditText
    private lateinit var instructionsEditText: EditText
    private lateinit var nextButton: Button
    private lateinit var firestore: FirebaseFirestore

    private var itemWeightKg = 0
    private var itemWeightGram = 0
    private lateinit var phoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_details)

        firestore = FirebaseFirestore.getInstance()

        // Get phone number from SharedPreferences
        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: run {
            Toast.makeText(this, "Phone number not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Optional: Log the phone number for debugging
        println("Retrieved phone number from SharedPreferences: $phoneNumber")

        initializeViews()
        setupWeightInputs()
        setupNextButton()
    }

    private fun initializeViews() {
        itemNameEditText = findViewById(R.id.itemNameEditText)
        kgEditText = findViewById(R.id.kgEditText)
        gramEditText = findViewById(R.id.gramEditText)
        instructionsEditText = findViewById(R.id.instructionsEditText)
        nextButton = findViewById(R.id.nextButton)
    }

    private fun setupWeightInputs() {
        kgEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let {
                    itemWeightKg = if (it.isNotEmpty()) {
                        val kg = it.toIntOrNull() ?: 0
                        if (kg > 15) {
                            kgEditText.error = "Max 15kg allowed"
                            kgEditText.setText("15")
                            15
                        } else {
                            kg
                        }
                    } else {
                        0
                    }
                }
            }
        })

        gramEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let {
                    itemWeightGram = if (it.isNotEmpty()) {
                        val grams = it.toIntOrNull() ?: 0
                        if (grams > 999) {
                            gramEditText.error = "Max 999g allowed"
                            gramEditText.setText("999")
                            999
                        } else {
                            grams
                        }
                    } else {
                        0
                    }
                }
            }
        })
    }

    private fun setupNextButton() {
        nextButton.setOnClickListener {
            if (validateInputs()) {
                saveItemUnderPhoneNumber()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (itemNameEditText.text.toString().trim().isEmpty()) {
            itemNameEditText.error = "Item name required"
            isValid = false
        }

        if (itemWeightKg == 0 && itemWeightGram == 0) {
            Toast.makeText(this, "Please enter item weight", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun saveItemUnderPhoneNumber() {
        val itemName = itemNameEditText.text.toString().trim()
        val instructions = instructionsEditText.text.toString().trim()
        val totalWeightGrams = (itemWeightKg * 1000) + itemWeightGram
        val timestamp = System.currentTimeMillis()

        // Create a combined document with both addresses and item details
        val senderData = hashMapOf(
            // Basic info
            "phoneNumber" to phoneNumber,
            "timestamp" to timestamp,
            "status" to "Pending",
            // From Address details
            "fromAddress" to hashMapOf(
                "houseNumber" to AddressHolder.fromHouseNumber,
                "street" to AddressHolder.fromStreet,
                "area" to AddressHolder.fromArea,
                "postalCode" to AddressHolder.fromPostalCode,
                "city" to AddressHolder.fromCity,
                "state" to AddressHolder.fromState,
                "fullAddress" to AddressHolder.fromAddress
            ),

            // To Address details
            "toAddress" to hashMapOf(
                "houseNumber" to AddressHolder.toHouseNumber,
                "street" to AddressHolder.toStreet,
                "area" to AddressHolder.toArea,
                "postalCode" to AddressHolder.toPostalCode,
                "city" to AddressHolder.toCity,
                "state" to AddressHolder.toState,
                "fullAddress" to AddressHolder.toAddress
            ),

            // Item details
            "itemDetails" to hashMapOf(
                "itemName" to itemName,
                "weightKg" to itemWeightKg,
                "weightGram" to itemWeightGram,
                "totalWeight" to totalWeightGrams,
                "instructions" to instructions,

                "itemId" to "item_${System.currentTimeMillis()}" // Unique ID for each item
            )
        )

        // Save to Sender collection using phoneNumber as document ID
        firestore.collection("Sender")
            .document(phoneNumber)
            .set(senderData)
            .addOnSuccessListener {
                Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
                navigateToSenderDashboard()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToSenderDashboard() {
        val intent = Intent(this, SenderDashboardActivity::class.java).apply {
            // You can still pass the phone number if needed, or let the dashboard get it from SharedPreferences
            putExtra("PHONE_NUMBER", phoneNumber)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}