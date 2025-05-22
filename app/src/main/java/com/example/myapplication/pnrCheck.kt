package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class pnrCheck : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var airlineSpinner: Spinner
    private var selectedAirline: String = "SpiceJet" // default selection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pnr_check)

        // Get intent extras
        val fromAddress = intent.getStringExtra("FROM_ADDRESS")
        val toAddress = intent.getStringExtra("TO_ADDRESS")
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val etPnr = findViewById<EditText>(R.id.etPnr)
        val etSurname = findViewById<EditText>(R.id.etSurname)
        airlineSpinner = findViewById(R.id.airlineSpinner)

        // Setup spinner
        val airlines = arrayOf("SpiceJet", "IndiGo")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, airlines)
        airlineSpinner.adapter = adapter

        airlineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedAirline = airlines[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Keep default selection
            }
        }

        btnVerify.setOnClickListener {
            val pnr = etPnr.text.toString().trim()
            val surname = etSurname.text.toString().trim()

            if (pnr.isEmpty() || surname.isEmpty()) {
                Toast.makeText(this, "Please enter both PNR and Surname", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

           /* val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: run {
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }*/

            //val phoneNumber = "8690999090" // need to remove this
            val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: ""
            // Create user data map
// Create user data map using AddressHolder instead of intent extras
            val userData = hashMapOf(
                "phoneNumber" to phoneNumber,
                "verified" to true,
                "fromAddress" to mapOf(
                    "houseNumber" to AddressHolder.fromHouseNumber,
                    "street" to AddressHolder.fromStreet,
                    "area" to AddressHolder.fromArea,
                    "postalCode" to AddressHolder.fromPostalCode,
                    "city" to AddressHolder.fromCity,
                    "state" to AddressHolder.fromState,
                    "fullAddress" to AddressHolder.fromAddress
                ),
                "toAddress" to mapOf(
                    "houseNumber" to AddressHolder.toHouseNumber,
                    "street" to AddressHolder.toStreet,
                    "area" to AddressHolder.toArea,
                    "postalCode" to AddressHolder.toPostalCode,
                    "city" to AddressHolder.toCity,
                    "state" to AddressHolder.toState,
                    "fullAddress" to AddressHolder.toAddress
                ),
                "pnr" to pnr,
                "lastName" to surname,
                "airline" to selectedAirline,
                "timestamp" to System.currentTimeMillis(),
                "SenderRequest" to false
            )

            // Save to Firestore
            db.collection("traveler").document(phoneNumber)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this, "Data saved successfully!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, webviewPnr::class.java).apply {
                        putExtra("PNR", pnr)
                        putExtra("SURNAME", surname)
                        putExtra("AIRLINE", selectedAirline)
                    }
                    startActivity(intent)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}