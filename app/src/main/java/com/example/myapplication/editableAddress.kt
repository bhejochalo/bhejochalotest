package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.text.HtmlCompat

class editableAddress : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editable_address)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Label TextViews
        val houseNumberLabel = findViewById<TextView>(R.id.houseNumberLabel)
        val streetLabel = findViewById<TextView>(R.id.streetLabel)
        val areaLabel = findViewById<TextView>(R.id.areaLabel)
        val postalCodeLabel = findViewById<TextView>(R.id.postalCodeLabel)
        val cityLabel = findViewById<TextView>(R.id.cityLabel)
        val stateLabel = findViewById<TextView>(R.id.stateLabel)

        // Add asterisk to each label
        houseNumberLabel.text = withAsterisk("House Number")
        streetLabel.text = withAsterisk("Street")
        areaLabel.text = withAsterisk("Area/Locality")
        postalCodeLabel.text = withAsterisk("Postal Code")
        cityLabel.text = withAsterisk("City")
        stateLabel.text = withAsterisk("State")

        // EditTexts
        val houseNumberEditText = findViewById<EditText>(R.id.houseNumberEditText)
        val streetEditText = findViewById<EditText>(R.id.streetEditText)
        val areaEditText = findViewById<EditText>(R.id.areaEditText)
        val postalCodeEditText = findViewById<EditText>(R.id.postalCodeEditText)
        val cityEditText = findViewById<EditText>(R.id.cityEditText)
        val stateEditText = findViewById<EditText>(R.id.stateEditText)

        // Save button
        val saveButton = findViewById<Button>(R.id.saveButton)

        saveButton.setOnClickListener {
            var isValid = true

            if (houseNumberEditText.text.isNullOrBlank()) {
                houseNumberEditText.error = "This field is required"
                isValid = false
            }
            if (streetEditText.text.isNullOrBlank()) {
                streetEditText.error = "This field is required"
                isValid = false
            }
            if (areaEditText.text.isNullOrBlank()) {
                areaEditText.error = "This field is required"
                isValid = false
            }
            if (postalCodeEditText.text.isNullOrBlank()) {
                postalCodeEditText.error = "This field is required"
                isValid = false
            }
            if (cityEditText.text.isNullOrBlank()) {
                cityEditText.error = "This field is required"
                isValid = false
            }
            if (stateEditText.text.isNullOrBlank()) {
                stateEditText.error = "This field is required"
                isValid = false
            }

            if (isValid) {
                val intent = Intent(this, pnrCheck::class.java).apply {
                    // Add any extras if needed
                    // putExtra("KEY", value)
                }
                startActivity(intent)
                Toast.makeText(this, "Address saved successfully", Toast.LENGTH_SHORT).show()
            }
        }

        // Populate fields from intent (optional)
        intent?.extras?.let { bundle ->
            houseNumberEditText.setText(bundle.getString("HOUSE_NUMBER", ""))
            streetEditText.setText(bundle.getString("STREET", ""))
            areaEditText.setText(bundle.getString("AREA", ""))
            postalCodeEditText.setText(bundle.getString("POSTAL_CODE", ""))
            cityEditText.setText(bundle.getString("CITY", ""))
            stateEditText.setText(bundle.getString("STATE", ""))
        }
    }

    private fun withAsterisk(label: String): CharSequence {
        return HtmlCompat.fromHtml(
            "$label <font color='#FF0000'>*</font>",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }
}