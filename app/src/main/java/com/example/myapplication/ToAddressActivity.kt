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
                val intent = Intent(this, SenderReceiverSelectionActivity::class.java)
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