package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import java.util.*

class pnrCheck : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var airlineSpinner: Spinner
    private var selectedAirline: String = "SpiceJet" // default selection

    private lateinit var etLeavingDate: EditText
    private lateinit var etLeavingTime: EditText
    private lateinit var etWeightUpto: EditText
    private lateinit var etPnr: EditText
    private lateinit var etSurname: EditText
    private lateinit var btnVerify: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pnr_check)

        // Get intent extras (if any)
        val fromAddress = intent.getStringExtra("FROM_ADDRESS")
        val toAddress = intent.getStringExtra("TO_ADDRESS")
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Bind views
        etPnr = findViewById(R.id.etPnr)
        etSurname = findViewById(R.id.etSurname)
        etLeavingDate = findViewById(R.id.etLeavingDate)
        etLeavingTime = findViewById(R.id.etLeavingTime)
        etWeightUpto = findViewById(R.id.etWeightUpto)
        btnVerify = findViewById(R.id.btnVerify)
        airlineSpinner = findViewById(R.id.airlineSpinner)

        // Spinner setup
        val airlines = arrayOf("SpiceJet", "IndiGo")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, airlines)
        airlineSpinner.adapter = adapter

        airlineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedAirline = airlines[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Date Picker
        etLeavingDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val dateStr = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                etLeavingDate.setText(dateStr)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        // Time Picker
        etLeavingTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(this, { _, hourOfDay, minute ->
                val timeStr = String.format("%02d:%02d", hourOfDay, minute)
                etLeavingTime.setText(timeStr)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }

        // Verify button action
        btnVerify.setOnClickListener {
            val pnr = etPnr.text.toString().trim()
            val surname = etSurname.text.toString().trim()
            val leavingDate = etLeavingDate.text.toString().trim()
            val leavingTime = etLeavingTime.text.toString().trim()
            val weightUpto = etWeightUpto.text.toString().trim()

            if (pnr.isEmpty() || surname.isEmpty() || leavingDate.isEmpty() || leavingTime.isEmpty() || weightUpto.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val phone = sharedPref.getString("PHONE_NUMBER", "") ?: ""

            val userData = hashMapOf(
                "phoneNumber" to phone,
                "verified" to true,
                "pnr" to pnr,
                "lastName" to surname,
                "airline" to selectedAirline,
                "leavingDate" to leavingDate,
                "leavingTime" to leavingTime,
                "weightUpto" to weightUpto,
                "timestamp" to System.currentTimeMillis(),
                "SenderRequest" to false,
                "fromAddress" to mapOf(
                    "houseNumber" to AddressHolder.fromHouseNumber,
                    "street" to AddressHolder.fromStreet,
                    "area" to AddressHolder.fromArea,
                    "postalCode" to AddressHolder.fromPostalCode,
                    "city" to AddressHolder.fromCity,
                    "state" to AddressHolder.fromState,
                    "fullAddress" to AddressHolder.fromAddress,
                    "latitude" to AddressHolder.fromLatitude,
                    "longitude" to AddressHolder.fromLongitude
                ),
                "toAddress" to mapOf(
                    "houseNumber" to AddressHolder.toHouseNumber,
                    "street" to AddressHolder.toStreet,
                    "area" to AddressHolder.toArea,
                    "postalCode" to AddressHolder.toPostalCode,
                    "city" to AddressHolder.toCity,
                    "state" to AddressHolder.toState,
                    "fullAddress" to AddressHolder.toAddress,
                    "latitude" to AddressHolder.toLatitude,
                    "longitude" to AddressHolder.toLongitude
                )
            )

            db.collection("traveler").document(phone)
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
