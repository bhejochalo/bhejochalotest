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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.util.*

class AutoCompleteAddressActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var fromPlace: Place? = null
    private var toPlace: Place? = null
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_complete_address)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCDmMtuO7w9uBecNRCtf5vndLUAsZVPUHI")
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val etFromAddress = findViewById<EditText>(R.id.et_from_address)
        val etToAddress = findViewById<EditText>(R.id.et_to_address)
        val nextButton = findViewById<Button>(R.id.NextButton)

        if (checkLocationPermission()) {
            getCurrentLocation()
        } else {
            requestLocationPermission()
        }

        etFromAddress.setOnClickListener { launchAutocomplete(FROM_ADDRESS_REQUEST) }
        etToAddress.setOnClickListener { launchAutocomplete(TO_ADDRESS_REQUEST) }

        nextButton.setOnClickListener {
            if (fromPlace == null) {
                Toast.makeText(this, "Please select From Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (toPlace == null) {
                Toast.makeText(this, "Please select To Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, FromAddressActivity::class.java).apply {
                putExtra("FROM_PLACE", fromPlace)
                putExtra("TO_PLACE", toPlace)
                putExtra("PHONE_NUMBER", intent.getStringExtra("PHONE_NUMBER"))
            }
            startActivity(intent)
        }
    }

    private fun launchAutocomplete(requestCode: Int) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS_COMPONENTS
        )
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountry("IN")
            .build(this)
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            data?.let {
                val place = Autocomplete.getPlaceFromIntent(data)
                when (requestCode) {
                    FROM_ADDRESS_REQUEST -> {
                        fromPlace = place
                        findViewById<EditText>(R.id.et_from_address).setText(place.address)
                    }
                    TO_ADDRESS_REQUEST -> {
                        toPlace = place
                        findViewById<EditText>(R.id.et_to_address).setText(place.address)
                    }
                }
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun getCurrentLocation() {
        try {
            if (checkLocationPermission()) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                        addresses?.firstOrNull()?.let { address ->
                            fromPlace = Place.builder()
                                .setAddress(address.getAddressLine(0))
                                .build()
                            findViewById<EditText>(R.id.et_from_address).setText(address.getAddressLine(0))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val FROM_ADDRESS_REQUEST = 1
        private const val TO_ADDRESS_REQUEST = 2
    }
}