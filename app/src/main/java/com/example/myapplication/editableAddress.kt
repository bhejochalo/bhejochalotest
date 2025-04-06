package com.example.myapplication

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

        // Initialize all TextViews (labels)
        val houseNumberLabel = findViewById<TextView>(R.id.houseNumberLabel)
        val streetLabel = findViewById<TextView>(R.id.streetLabel)
        val areaLabel = findViewById<TextView>(R.id.areaLabel)
        val postalCodeLabel = findViewById<TextView>(R.id.postalCodeLabel)
        val cityLabel = findViewById<TextView>(R.id.cityLabel)
        val stateLabel = findViewById<TextView>(R.id.stateLabel)

        // Set label texts
        houseNumberLabel.text = "House Number"
        streetLabel.text = "Street"
        areaLabel.text = "Area/Locality"
        postalCodeLabel.text = "Postal Code"
        cityLabel.text = "City"
        stateLabel.text = "State"

        // Initialize all EditText fields
        val houseNumberEditText = findViewById<EditText>(R.id.houseNumberEditText)
        val streetEditText = findViewById<EditText>(R.id.streetEditText)
        val areaEditText = findViewById<EditText>(R.id.areaEditText)
        val postalCodeEditText = findViewById<EditText>(R.id.postalCodeEditText)
        val cityEditText = findViewById<EditText>(R.id.cityEditText)
        val stateEditText = findViewById<EditText>(R.id.stateEditText)

        // Populate fields from intent extras
        intent?.extras?.let { bundle ->
            houseNumberEditText.setText(bundle.getString("HOUSE_NUMBER", ""))
            streetEditText.setText(bundle.getString("STREET", ""))
            areaEditText.setText(bundle.getString("AREA", ""))
            postalCodeEditText.setText(bundle.getString("POSTAL_CODE", ""))
            cityEditText.setText(bundle.getString("CITY", ""))
            stateEditText.setText(bundle.getString("STATE", ""))
        }
    }
}