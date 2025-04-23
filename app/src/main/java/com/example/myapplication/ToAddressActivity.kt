package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.places.api.model.Place
object AddressHolder {
    // Basic info
    var phoneNumber: String? = null
    var fromAddress: String? = null
    var toAddress: String? = null

    // From address components
    var fromHouseNumber: String? = null
    var fromStreet: String? = null
    var fromArea: String? = null
    var fromPostalCode: String? = null
    var fromCity: String? = null
    var fromState: String? = null

    // To address components
    var toHouseNumber: String? = null
    var toStreet: String? = null
    var toArea: String? = null
    var toPostalCode: String? = null
    var toCity: String? = null
    var toState: String? = null
}

class ToAddressActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editable_address)

        // Initialize views
        findViewById<TextView>(R.id.addressHeading).text = "To Address"
        val houseNumber = findViewById<EditText>(R.id.houseNumberEditText)
        val street = findViewById<EditText>(R.id.streetEditText)
        val area = findViewById<EditText>(R.id.areaEditText)
        val postalCode = findViewById<EditText>(R.id.postalCodeEditText)
        val city = findViewById<EditText>(R.id.cityEditText)
        val state = findViewById<EditText>(R.id.stateEditText)
        val nextButton = findViewById<Button>(R.id.saveButton).apply {
            text = "Next"
        }

        // Get place from intent
        val place = intent.getParcelableExtra<Place>("TO_PLACE") ?: run {
            Toast.makeText(this, "Error: No address data", Toast.LENGTH_SHORT).show()
            finish()
            return
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

            val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val phoneNumber = sharedPref.getString("PHONE_NUMBER", "")

            if (validateFields()) {
                // Store all data in AddressHolder
                AddressHolder.apply {
                    // From address components (carried over from previous activity)
                   // fromHouseNumber = intent.getStringExtra("FROM_HOUSE_NUMBER")
                   // fromStreet = intent.getStringExtra("FROM_STREET")
                   // fromArea = intent.getStringExtra("FROM_AREA")
                   // fromPostalCode = intent.getStringExtra("FROM_POSTAL_CODE")
                   // fromCity = intent.getStringExtra("FROM_CITY")
                  //  fromState = intent.getStringExtra("FROM_STATE")

                    //println("FROM_HOUSE_NUMBER ===> $fromHouseNumber")
                    //println("fromStreet===> $fromStreet")
                  //  println("fromArea ===> $fromArea")
                    // To address components (from current activity)
                    toHouseNumber = houseNumber.text.toString()
                    toStreet = street.text.toString()
                    toArea = area.text.toString()
                    toPostalCode = postalCode.text.toString()
                    toCity = city.text.toString()
                    toState = state.text.toString()

                    println("toHouseNumber ===> $toHouseNumber")
                    println("toStreet===> $toStreet")
                    println("toPostalCode ===> $toPostalCode")
                    // Basic address strings
                    fromAddress = intent.getStringExtra("FROM_ADDRESS")
                    toAddress = intent.getStringExtra("TO_ADDRESS")
                    //phoneNumber = intent.getStringExtra("PHONE_NUMBER")
                    println("phone number ===> $phoneNumber")
                    println("fromAddress===> $fromAddress")
                    println("toAddress ===> $toAddress")

                   // println("AddressHolder ===> $AddressHolder)

                    Log.d("AddressDebug", """
            House: $fromHouseNumber
            Street: $fromStreet
            Area: $fromArea
            Postal: $fromPostalCode
            City: $fromCity
            State: $fromState
        """.trimIndent())

                }

                // Start next activity - no need to pass extras now
                startActivity(Intent(this, SenderReceiverSelectionActivity::class.java))
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