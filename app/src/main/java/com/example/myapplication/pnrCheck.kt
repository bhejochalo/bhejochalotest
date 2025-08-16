package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.*

class pnrCheck : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var airlineSpinner: Spinner
    private lateinit var spaceAvailableSpinner: Spinner
    private lateinit var itemsCarryAutoComplete: AutoCompleteTextView
    private lateinit var checkboxTerms: CheckBox
    private lateinit var etRemarks: EditText

    private var selectedAirline: String = "SpiceJet"
    private var selectedSpaceAvailable: String = ""

    private lateinit var etLeavingDate: EditText
    private lateinit var etLeavingTime: EditText
    private lateinit var etWeightUpto: EditText
    private lateinit var etPnr: EditText
    private lateinit var etSurname: EditText
    private lateinit var btnVerify: Button
    private lateinit var etFirstName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pnr_check)

        val fromAddress = intent.getStringExtra("FROM_ADDRESS")
        val toAddress = intent.getStringExtra("TO_ADDRESS")
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // View bindings
        etPnr = findViewById(R.id.etPnr)
        etSurname = findViewById(R.id.etSurname)
        etLeavingDate = findViewById(R.id.etLeavingDate)
        etLeavingTime = findViewById(R.id.etLeavingTime)
        etWeightUpto = findViewById(R.id.etWeightUpto)
        btnVerify = findViewById(R.id.btnVerify)
        airlineSpinner = findViewById(R.id.airlineSpinner)
        spaceAvailableSpinner = findViewById(R.id.spaceAvailableSpinner)
        itemsCarryAutoComplete = findViewById(R.id.itemsCarryAutoComplete)
        checkboxTerms = findViewById(R.id.checkboxTerms)
        etRemarks = findViewById(R.id.etRemarks)
        etFirstName = findViewById(R.id.etFirstName)

        // Airline spinner setup
        val airlines = arrayOf("None", "SpiceJet", "IndiGo", "Air India", "Vistara", "GoAir", "Akasa Air")
        val airlineAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, airlines)
        airlineSpinner.adapter = airlineAdapter
        airlineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedAirline = airlines[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Space available spinner setup
        val spaceOptions = arrayOf("None", "Cabin", "Luggage", "Personal Bag", "All")
        val spaceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spaceOptions)
        spaceAvailableSpinner.adapter = spaceAdapter
        spaceAvailableSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedSpaceAvailable = spaceOptions[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Multi-select dialog for items user can carry
        val items = arrayOf("Documents", "Laptop", "Medicines", "Electronics", "Clothes", "Books", "Gifts", "Alcohol")
        val checkedItems = BooleanArray(items.size)
        val selectedItems = mutableListOf<String>()

        itemsCarryAutoComplete.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select items you can carry")

            val items = arrayOf("Documents", "Laptop", "Medicines", "Electronics", "Clothes", "Books", "Gifts", "Alcohol")
            val checkedItems = BooleanArray(items.size) { i -> selectedItems.contains(items[i]) }
            val restrictedItems = listOf("Laptop", "Electronics", "Documents")

            builder.setMultiChoiceItems(items, checkedItems) { dialog, which, isChecked ->
                val currentItem = items[which]

                // Restriction check
                if (isChecked && selectedSpaceAvailable == "Luggage" && restrictedItems.contains(currentItem)) {
                    Toast.makeText(this, "$currentItem not allowed in Luggage space", Toast.LENGTH_SHORT).show()
                    (dialog as AlertDialog).listView.setItemChecked(which, false) // Uncheck programmatically
                } else {
                    if (isChecked) {
                        selectedItems.add(currentItem)
                    } else {
                        selectedItems.remove(currentItem)
                    }
                }
            }

            builder.setPositiveButton("OK") { _, _ ->
                itemsCarryAutoComplete.setText(selectedItems.joinToString(", "))
            }

            builder.setNegativeButton("Cancel", null)
            builder.show()
        }

        // Date picker
        etLeavingDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val dateStr = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                etLeavingDate.setText(dateStr)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        // Time picker
        etLeavingTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(this, { _, hourOfDay, minute ->
                val timeStr = String.format("%02d:%02d", hourOfDay, minute)
                etLeavingTime.setText(timeStr)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }

        // Verify button
        btnVerify.setOnClickListener {
            val pnr = etPnr.text.toString().trim()
            val surname = etSurname.text.toString().trim()
            val leavingDate = etLeavingDate.text.toString().trim()
            val leavingTime = etLeavingTime.text.toString().trim()
            val weightUpto = etWeightUpto.text.toString().trim()
            val itemsCarrying = itemsCarryAutoComplete.text.toString().trim()
            val remarks = etRemarks.text.toString().trim()

            if (pnr.isEmpty() || surname.isEmpty() || leavingDate.isEmpty() || leavingTime.isEmpty() || weightUpto.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!checkboxTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validation: Some items not allowed in luggage
            val restrictedItems = listOf("Laptop", "Electronics", "Documents")
            val selectedItemsList = itemsCarrying.split(",").map { it.trim() }
            val invalidItems = mutableListOf<String>()

            if (selectedSpaceAvailable == "Luggage") {
                for (item in selectedItemsList) {
                    if (restrictedItems.contains(item)) {
                        invalidItems.add(item)
                    }
                }

                if (invalidItems.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "These items are not allowed in Luggage: ${invalidItems.joinToString(", ")}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
            }

            val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val phone = sharedPref.getString("PHONE_NUMBER", "") ?: ""

            val userData = hashMapOf(
                "pickAndDropMode" to null,
                "phoneNumber" to phone,
                "verified" to true,
                "pnr" to pnr,
                "lastName" to surname,
                "airline" to selectedAirline,
                "leavingDate" to leavingDate,
                "leavingTime" to leavingTime,
                "weightUpto" to weightUpto,
                "spaceAvailable" to selectedSpaceAvailable,
                "itemsUserCanCarry" to itemsCarrying,
                "remarks" to remarks,
                "timestamp" to System.currentTimeMillis(),
                "SenderRequest" to false,
                "FirstMileStatus" to "Not Started",
                "SecondMileStatus" to "Not Started",
                "LastMileStatus" to "Not Started",
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
                    startActivity(Intent(this, TravelerProfile::class.java))
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
