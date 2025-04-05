package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.util.*

class autoCompleteAddress : AppCompatActivity() {
// for autocomplete address
    //code sync
    private val FROM_ADDRESS_REQUEST = 1
    private val TO_ADDRESS_REQUEST = 2
    private val LOCATION_PERMISSION_REQUEST = 100
    private var fromAddress: String? = null
    private var toAddress: String? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_auto_complete_address)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCDmMtuO7w9uBecNRCtf5vndLUAsZVPUHI")
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val etFromAddress = findViewById<EditText>(R.id.et_from_address)
        val etToAddress = findViewById<EditText>(R.id.et_to_address)

        if (checkLocationPermission()) {
            getCurrentLocation()
        } else {
            requestLocationPermission()
        }

        etFromAddress.setOnClickListener {
            launchAutocomplete(FROM_ADDRESS_REQUEST)
        }

        etToAddress.setOnClickListener {
            launchAutocomplete(TO_ADDRESS_REQUEST)
        }

        val nextButton = findViewById<Button>(R.id.NextButton)
        nextButton.setOnClickListener {
            if (fromAddress != null) {
                val addressComponents = parseAddress(fromAddress!!)
                val intent = Intent(this@autoCompleteAddress, editableAddress::class.java).apply {
                    putExtra("HOUSE_NUMBER", addressComponents["houseNumber"])
                    putExtra("STREET", addressComponents["street"])
                    putExtra("AREA", addressComponents["area"])
                    putExtra("POSTAL_CODE", addressComponents["postalCode"])
                    putExtra("CITY", addressComponents["city"])
                    putExtra("STATE", addressComponents["state"])
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please select a from address first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchAutocomplete(requestCode: Int) {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountry("IN")
            .build(this)
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FROM_ADDRESS_REQUEST || requestCode == TO_ADDRESS_REQUEST) {
            when (resultCode) {
                RESULT_OK -> {
                    data?.let {
                        val place = Autocomplete.getPlaceFromIntent(data)
                        val address = place.address ?: ""

                        if (requestCode == FROM_ADDRESS_REQUEST) {
                            fromAddress = address
                            findViewById<EditText>(R.id.et_from_address).setText(address)
                        } else if (requestCode == TO_ADDRESS_REQUEST) {
                            toAddress = address
                            findViewById<EditText>(R.id.et_to_address).setText(address)
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    data?.let {
                        val status = Autocomplete.getStatusFromIntent(data)
                        Toast.makeText(this, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
                RESULT_CANCELED -> {
                    Toast.makeText(this, "Search canceled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseAddress(fullAddress: String): Map<String, String> {
        val components = mutableMapOf<String, String>()
        val parts = fullAddress.split(",").map { it.trim() }

        // Simple parsing - adjust based on your actual address format
        if (parts.isNotEmpty()) {
            // First part is typically house number and street
            val streetParts = parts[0].split(" ")
            if (streetParts.isNotEmpty()) {
                components["houseNumber"] = streetParts.firstOrNull() ?: ""
                components["street"] = streetParts.drop(1).joinToString(" ")
            }

            // Subsequent parts
            if (parts.size > 1) components["area"] = parts[1]
            if (parts.size > 2) components["city"] = parts[2]
            if (parts.size > 3) components["state"] = parts[3]
            if (parts.size > 4) components["postalCode"] = parts[4]
        }

        return components
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
    }

    private fun getCurrentLocation() {
        try {
            if (checkLocationPermission()) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        addresses?.firstOrNull()?.let { address ->
                            fromAddress = address.getAddressLine(0)
                            findViewById<EditText>(R.id.et_from_address).setText(fromAddress)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        }
    }
}