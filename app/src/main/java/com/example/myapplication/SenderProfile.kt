package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class SenderProfile : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var senderDocument: DocumentSnapshot

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

        // Get phone number from intent
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: run {
            Toast.makeText(this, "Phone number not received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Fetch sender data
        fetchSenderData(phoneNumber)
    }

    private fun fetchSenderData(phoneNumber: String) {
        db.collection("Sender")
            .whereEqualTo("phoneNumber", phoneNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    senderDocument = querySnapshot.documents[0]
                    displayAddresses()
                    setupEditButtons()
                } else {
                    Toast.makeText(this, "Sender data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching sender data: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayAddresses() {
        val tvFromAddress = findViewById<TextView>(R.id.tvFromAddress)
        val tvToAddress = findViewById<TextView>(R.id.tvToAddress)

        // Get from address
        val fromAddressMap = senderDocument.get("fromAddress") as? Map<*, *> ?: mapOf<String, String>()
        val fromAddress = """
            ${fromAddressMap["houseNumber"]}, 
            ${fromAddressMap["street"]}, 
            ${fromAddressMap["area"]}, 
            ${fromAddressMap["city"]}, 
            ${fromAddressMap["state"]} - ${fromAddressMap["postalCode"]}
        """.trimIndent()

        // Get to address
        val toAddressMap = senderDocument.get("toAddress") as? Map<*, *> ?: mapOf<String, String>()
        val toAddress = """
            ${toAddressMap["houseNumber"]}, 
            ${toAddressMap["street"]}, 
            ${toAddressMap["area"]}, 
            ${toAddressMap["city"]}, 
            ${toAddressMap["state"]} - ${toAddressMap["postalCode"]}
        """.trimIndent()

        tvFromAddress.text = fromAddress
        tvToAddress.text = toAddress
    }

    private fun setupEditButtons() {
        findViewById<Button>(R.id.btnEditFromAddress).setOnClickListener {
            showAddressEditDialog(senderDocument, "fromAddress") { newAddress ->
                findViewById<TextView>(R.id.tvFromAddress).text = newAddress
            }
        }

        findViewById<Button>(R.id.btnEditToAddress).setOnClickListener {
            showAddressEditDialog(senderDocument, "toAddress") { newAddress ->
                findViewById<TextView>(R.id.tvToAddress).text = newAddress
            }
        }
    }

    private fun showAddressEditDialog(
        document: DocumentSnapshot,
        addressType: String,
        onSave: (String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_traveler_address, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Get current address data
        val currentAddress = document.get(addressType) as? Map<String, String> ?: mapOf()

        // Initialize views
        val etStreet = dialogView.findViewById<EditText>(R.id.etStreet)
        val etHouseNumber = dialogView.findViewById<EditText>(R.id.etHouseNumber)
        val etArea = dialogView.findViewById<EditText>(R.id.etArea)
        val etCity = dialogView.findViewById<EditText>(R.id.etCity)
        val etState = dialogView.findViewById<EditText>(R.id.etState)
        val etPostalCode = dialogView.findViewById<EditText>(R.id.etPostalCode)

        // Auto-populate all fields including state
        etStreet.setText(currentAddress["street"] ?: "")
        etHouseNumber.setText(currentAddress["houseNumber"] ?: "")
        etArea.setText(currentAddress["area"] ?: "")
        etCity.setText(currentAddress["city"] ?: "")
        etState.setText(currentAddress["state"] ?: "")
        etPostalCode.setText(currentAddress["postalCode"] ?: "")

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val newStreet = etStreet.text.toString().trim()
            val newHouseNumber = etHouseNumber.text.toString().trim()
            val newArea = etArea.text.toString().trim()
            val newCity = etCity.text.toString().trim()
            val newState = etState.text.toString().trim()
            val newPostalCode = etPostalCode.text.toString().trim()

            // Check if any field has changed
            val hasChanges = newStreet != currentAddress["street"] ||
                    newHouseNumber != currentAddress["houseNumber"] ||
                    newArea != currentAddress["area"] ||
                    newCity != currentAddress["city"] ||
                    newState != currentAddress["state"] ||
                    newPostalCode != currentAddress["postalCode"]

            if (!hasChanges) {
                Toast.makeText(this, "No changes made", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            // Create formatted address with state
            val fullAddress = buildString {
                append(newStreet)
                if (newHouseNumber.isNotEmpty()) append(" $newHouseNumber")
                if (newArea.isNotEmpty()) append(", $newArea")
                if (newCity.isNotEmpty()) append(", $newCity")
                if (newState.isNotEmpty()) append(", $newState")
                if (newPostalCode.isNotEmpty()) append(" $newPostalCode")
            }

            // Prepare updates only for changed fields
            val updates = hashMapOf<String, Any>(
                "$addressType.fullAddress" to fullAddress
            )

            // Add individual fields only if they've changed
            if (newStreet != currentAddress["street"]) {
                updates["$addressType.street"] = newStreet
            }
            if (newHouseNumber != currentAddress["houseNumber"]) {
                updates["$addressType.houseNumber"] = newHouseNumber
            }
            if (newArea != currentAddress["area"]) {
                updates["$addressType.area"] = newArea
            }
            if (newCity != currentAddress["city"]) {
                updates["$addressType.city"] = newCity
            }
            if (newState != currentAddress["state"]) {
                updates["$addressType.state"] = newState
            }
            if (newPostalCode != currentAddress["postalCode"]) {
                updates["$addressType.postalCode"] = newPostalCode
            }

            // Update Firestore
            db.collection("Sender").document(document.id)
                .update(updates)
                .addOnSuccessListener {
                    onSave(fullAddress)
                    Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()

        // Adjust dialog window size
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}