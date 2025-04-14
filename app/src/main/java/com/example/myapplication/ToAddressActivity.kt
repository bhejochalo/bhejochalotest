package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.places.api.model.Place

class ToAddressActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val fromAddress = intent.getStringExtra("FROM_ADDRESS")
        val toAddress = intent.getStringExtra("TO_ADDRESS")
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editable_address)
        findViewById<TextView>(R.id.addressHeading).text = "To Address"
        val place = intent.getParcelableExtra<Place>("TO_PLACE") ?: run {
            Toast.makeText(this, "Error: No address data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        val houseNumber = findViewById<EditText>(R.id.houseNumberEditText)
        val street = findViewById<EditText>(R.id.streetEditText)
        val area = findViewById<EditText>(R.id.areaEditText)
        val postalCode = findViewById<EditText>(R.id.postalCodeEditText)
        val city = findViewById<EditText>(R.id.cityEditText)
        val state = findViewById<EditText>(R.id.stateEditText)
        val nextButton = findViewById<Button>(R.id.saveButton).apply {
            text = "Next"
        }

        // Parse and populate fields
        parsePlace(place).apply {
            houseNumber.setText(getString("houseNumber"))
            street.setText(getString("street"))
            area.setText(getString("area"))
            postalCode.setText(getString("postalCode"))
            city.setText(getString("city"))
            state.setText(getString("state"))
        }

        nextButton.setOnClickListener {
            if (validateFields()) {
                val intent = Intent(this, SenderReceiverSelectionActivity::class.java).apply {
                    putExtra("PHONE_NUMBER", phoneNumber)
                    // From address components
                    putExtra("FROM_HOUSE_NUMBER", intent.getStringExtra("FROM_HOUSE_NUMBER"))
                    putExtra("FROM_STREET", intent.getStringExtra("FROM_STREET"))
                    putExtra("FROM_AREA", intent.getStringExtra("FROM_AREA"))
                    putExtra("FROM_POSTAL_CODE", intent.getStringExtra("FROM_POSTAL_CODE"))
                    putExtra("FROM_CITY", intent.getStringExtra("FROM_CITY"))
                    putExtra("FROM_STATE", intent.getStringExtra("FROM_STATE"))
                    // To address components
                    putExtra("FROM_ADDRESS", intent.getStringExtra("FROM_ADDRESS"))
                    putExtra("TO_ADDRESS", intent.getStringExtra("TO_ADDRESS"))
                    putExtra("TO_HOUSE_NUMBER", houseNumber.text.toString())
                    putExtra("TO_STREET", street.text.toString())
                    putExtra("TO_AREA", area.text.toString())
                    putExtra("TO_POSTAL_CODE", postalCode.text.toString())
                    putExtra("TO_CITY", city.text.toString())
                    putExtra("TO_STATE", state.text.toString())
                }
                startActivity(intent)
            }
        }
    }

    private fun validateFields(): Boolean {
        var isValid = true
        listOf(
            R.id.houseNumberEditText,
            R.id.streetEditText,
            R.id.areaEditText,
            R.id.postalCodeEditText,
            R.id.cityEditText,
            R.id.stateEditText
        ).forEach { id ->
            findViewById<EditText>(id).apply {
                if (text.isNullOrBlank()) {
                    error = "This field is required"
                    isValid = false
                }
            }
        }
        return isValid
    }

    private fun parsePlace(place: Place): Bundle {
        // Same implementation as in FromAddressActivity
        return FromAddressActivity().parsePlace(place)
    }
}