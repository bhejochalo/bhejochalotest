package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class SenderProfile : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences
    private var uniqueKey: String? = null
    private var senderDoc: DocumentSnapshot? = null
    private var travelerDoc: DocumentSnapshot? = null

    // Map-related fields (programmatic MapView)
    private var mapView: MapView? = null
    // Add these constants at the top of the class
    companion object {
        private const val PERMISSION_CALL_PHONE = 100
        private const val PERMISSION_LOCATION = 101
    }

    private var googleMap: com.google.android.gms.maps.GoogleMap? = null
    private var pendingLatLng: com.google.android.gms.maps.model.LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_profile)

        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        //uniqueKey = sharedPref.getString("uniqueKey", null) ?: intent.getStringExtra("uniqueKey")
        uniqueKey = "swadgkey"

        // Inject MapView programmatically into the existing layout (above tvTravelerLocation)
        injectMapViewProgrammatically(savedInstanceState)

        setupTabSwitching()
        setupEditButtons()
        setupEditItemButton()
        setupMileControls()
        setupFirstMileContactButtons()

        loadSenderData {
            loadAddressData()
            loadItemDetails()
            ensureFirstMileStarted()
            initializeMileStatusFields()

            // Check traveler acceptance and update UI/editing permissions
            refreshTravelerDoc { tDoc ->
                val travelerStatus = tDoc?.getString("status") ?: ""
                if (travelerStatus == "Request Accepted By Traveler") {
                    // show contact + location and disable edits
                    showTravelerContactAndLocation(tDoc)
                    setSenderEditingEnabled(false)
                    setFlightDetailsVisible(true)
                } else {
                    // hide sensitive details & enable edits
                    setSenderEditingEnabled(true)
                    hideTravelerContactAndLocation()
                    setFlightDetailsVisible(false)
                }
            }
        }

        loadTravelerDataOnce {
            travelerDoc?.let {
                updateFlightUI(it)
                updateTravelerUI(it)
                checkAndUpdateBookingStatus(it)
                loadStatusData()
            }
            setupMileControls()
        }

        findViewById<Button>(R.id.btnBookOtherTravelers).setOnClickListener {
            navigateToSenderDashboard()
        }
    }
    private fun initializeMileStatusFields() {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]

                    // Check if these fields already exist or need initialization
                    val secondMileStatus = document.getString("SecondMileStatus")
                    val lastMileStatus = document.getString("LastMileStatus")
                    val lastMileOtp = document.getString("LastMileOTP")

                    // Only initialize if fields don't exist or are null
                    val updates = hashMapOf<String, Any>()

                    if (secondMileStatus == null) {
                        updates["SecondMileStatus"] = "Not Started"
                    }

                    if (lastMileStatus == null) {
                        updates["LastMileStatus"] = "Not Started"
                    }

                    if (lastMileOtp == null) {
                        updates["LastMileOTP"] = ""
                    }

                    // Only update if there are fields to initialize
                    if (updates.isNotEmpty()) {
                        db.collection("Sender").document(userId)
                            .update(updates)
                            .addOnSuccessListener {
                                Log.d("SenderProfile", "Mile status fields initialized successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e("SenderProfile", "Error initializing mile status fields", e)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error checking sender document", e)
            }
    }
    // Programmatic injection of MapView into existing layout; no XML changes
    private fun injectMapViewProgrammatically(savedInstanceState: Bundle?) {
        try {
            // find the textual location view — we will insert the MapView just before it
            val tvTravelerLocation = findViewById<TextView>(R.id.tvTravelerLocation) ?: return
            val parent = tvTravelerLocation.parent as? ViewGroup ?: return

            // Avoid adding twice if activity recreated
            val existingMap = parent.findViewWithTag<View>("travelermap_tag")
            if (existingMap != null) {
                // Map already injected
                mapView = existingMap as? MapView
                mapView?.onCreate(savedInstanceState)
                mapView?.getMapAsync(this)
                return
            }

            // Create MapView programmatically
            mapView = MapView(this).apply {
                // store a tag so we can detect later
                tag = "travelermap_tag"
                // set reasonable size in code (convert 200dp to px)
                val dp200 = (200 * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp200
                ).apply {
                    (this as? ViewGroup.MarginLayoutParams)?.setMargins(0, dpToPx(8), 0, dpToPx(8))
                }
            }

            // Insert mapView before the tvTravelerLocation inside parent
            val index = parent.indexOfChild(tvTravelerLocation)
            parent.addView(mapView, index)

            // Initialize map view lifecycle
            mapView?.onCreate(savedInstanceState)
            mapView?.getMapAsync(this)
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error injecting MapView programmatically: ${e.message}", e)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // Map lifecycle forwarding
    override fun onResume() {
        super.onResume()
        try { mapView?.onResume() } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        try { mapView?.onStart() } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { mapView?.onStop() } catch (_: Exception) {}
    }

    override fun onPause() {
        try { mapView?.onPause() } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        try { mapView?.onDestroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try { mapView?.onLowMemory() } catch (_: Exception) {}
    }

    // OnMapReady -> keep reference and apply any pending marker
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true

        // If we had a pending lat/lng before map loaded, show it now
        pendingLatLng?.let {
            showTravelerOnMap(it.latitude, it.longitude)
            pendingLatLng = null
        }
    }

    private fun navigateToSenderDashboard() {
        val intent = Intent(this, SenderDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun loadSenderData(onLoaded: () -> Unit) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    senderDoc = querySnapshot.documents[0]
                    onLoaded()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading sender data", e)
            }
    }

    private fun loadTravelerDataOnce(onLoaded: () -> Unit) {
        val key = uniqueKey ?: return
        db.collection("traveler")
            .whereEqualTo("uniqueKey", key)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    travelerDoc = querySnapshot.documents[0]
                    onLoaded()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading traveler data", e)
            }
    }

    private fun setupTabSwitching() {
        val tabStatusContainer = findViewById<LinearLayout>(R.id.tabStatusContainer)
        val tabSenderContainer = findViewById<LinearLayout>(R.id.tabSenderContainer)
        val statusContent = findViewById<LinearLayout>(R.id.statusContent)
        val senderContent = findViewById<NestedScrollView>(R.id.senderContent)
        val underlineStatus = findViewById<View>(R.id.underlineStatus)
        val underlineSender = findViewById<View>(R.id.underlineSender)
        val tabStatus = findViewById<TextView>(R.id.tabStatus)
        val tabSender = findViewById<TextView>(R.id.tabSender)

        tabStatusContainer.setOnClickListener {
            statusContent.visibility = View.VISIBLE
            senderContent.visibility = View.GONE
            underlineStatus.visibility = View.VISIBLE
            underlineSender.visibility = View.GONE
            tabStatus.setTextColor(getColor(R.color.orange_primary))
            tabSender.setTextColor(getColor(R.color.gray_secondary))
        }

        tabSenderContainer.setOnClickListener {
            statusContent.visibility = View.GONE
            senderContent.visibility = View.VISIBLE
            underlineStatus.visibility = View.GONE
            underlineSender.visibility = View.VISIBLE
            tabStatus.setTextColor(getColor(R.color.gray_secondary))
            tabSender.setTextColor(getColor(R.color.orange_primary))

            // refresh traveler details when opening traveler tab
            refreshTravelerDoc { doc ->
                doc?.let {
                    updateFlightUI(it)
                    updateTravelerUI(it)
                    checkAndUpdateBookingStatus(it)
                    loadStatusData()
                }
            }
        }
    }

    private fun checkAndUpdateBookingStatus(travelerDoc: DocumentSnapshot) {
        try {
            // prefer the key variants that may exist
            val status = travelerDoc.getString("status")
                ?: travelerDoc.getString("orderStatus")
                ?: travelerDoc.getString("bookingStatus")
                ?: "N/A"

            // flight number variants
            val flightNumber = travelerDoc.getString("flightNumber")
                ?: travelerDoc.getString("FlightNumber")
                ?: travelerDoc.getString("FlightNo")
                ?: travelerDoc.getString("flight_no")
                ?: "N/A"

            val trackingUrl = if (flightNumber != "N/A" && flightNumber.isNotBlank()) {
                "https://www.flightaware.com/live/flight/$flightNumber"
            } else {
                "Flight tracking not available"
            }

            runOnUiThread {
                findViewById<TextView>(R.id.subStatus).text = status
                findViewById<TextView>(R.id.trackingUrl).text = "Flight Tracking: $trackingUrl"

                val bookOtherBtn = findViewById<Button>(R.id.btnBookOtherTravelers)
                bookOtherBtn.visibility = if (status == "Rejected By Traveler") View.VISIBLE else View.GONE

                reorganizeItemDetailsLayout(status)
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error checking booking status", e)
        }
    }


    private fun reorganizeItemDetailsLayout(status: String) {
        try {
            val itemDetailsCard = findViewById<CardView>(R.id.itemDetailsCard)
            val flightInfoCard = findViewById<CardView>(R.id.flightInfoCard)
            val addressCard = findViewById<CardView>(R.id.addressCard)
            val statusCard = findViewById<CardView>(R.id.statusCard)

            if (status == "Request Accepted By Traveler") {
                // Flight Info first, then Item Details, then Address, then Status
                val flightParams = flightInfoCard.layoutParams as ConstraintLayout.LayoutParams
                flightParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                flightParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
                flightInfoCard.layoutParams = flightParams

                val itemParams = itemDetailsCard.layoutParams as ConstraintLayout.LayoutParams
                itemParams.topToBottom = R.id.flightInfoCard
                itemParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
                itemDetailsCard.layoutParams = itemParams

                val addressParams = addressCard.layoutParams as ConstraintLayout.LayoutParams
                addressParams.topToBottom = R.id.itemDetailsCard
                addressCard.layoutParams = addressParams

                val statusParams = statusCard.layoutParams as ConstraintLayout.LayoutParams
                statusParams.topToBottom = R.id.addressCard
                statusCard.layoutParams = statusParams

            } else {
                // Flight Info first, then Address, then Status, then Item Details at bottom
                val flightParams = flightInfoCard.layoutParams as ConstraintLayout.LayoutParams
                flightParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                flightParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
                flightInfoCard.layoutParams = flightParams

                val addressParams = addressCard.layoutParams as ConstraintLayout.LayoutParams
                addressParams.topToBottom = R.id.flightInfoCard
                addressCard.layoutParams = addressParams

                val statusParams = statusCard.layoutParams as ConstraintLayout.LayoutParams
                statusParams.topToBottom = R.id.addressCard
                statusCard.layoutParams = statusParams

                val itemParams = itemDetailsCard.layoutParams as ConstraintLayout.LayoutParams
                itemParams.topToBottom = R.id.statusCard
                itemParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                itemDetailsCard.layoutParams = itemParams
            }

            // Request layout update
            (itemDetailsCard.parent as? ViewGroup)?.requestLayout()
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error reorganizing layout", e)
        }
    }

    private fun setupEditButtons() {
        val fromBtn = findViewById<Button>(R.id.btnEditFromAddress)
        val toBtn = findViewById<Button>(R.id.btnEditToAddress)

        fromBtn.setOnClickListener {
            if (!fromBtn.isEnabled) {
                Toast.makeText(this, "Editing disabled — traveler already accepted the request", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showEditAddressDialog("From") { newAddress ->
                findViewById<TextView>(R.id.tvFromAddress).text = newAddress
            }
        }

        toBtn.setOnClickListener {
            if (!toBtn.isEnabled) {
                Toast.makeText(this, "Editing disabled — traveler already accepted the request", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showEditAddressDialog("To") { newAddress ->
                findViewById<TextView>(R.id.tvToAddress).text = newAddress
            }
        }
    }

    private fun setupEditItemButton() {
        val btn = findViewById<Button>(R.id.btnEditItemDetails)
        btn.setOnClickListener {
            if (!btn.isEnabled) {
                Toast.makeText(this, "Editing disabled — traveler already accepted the request", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showEditItemDialog()
        }
    }

    // -------------------------
    // Mile controls and refresh
    // -------------------------
    private fun setupMileControls() {
        val btnRefreshAll = findViewById<Button>(R.id.btnRefreshAll)
        val btnVerifyPickupOtp = findViewById<Button>(R.id.btnVerifyPickupOtp)
        val btnVerifyLastOtp = findViewById<Button>(R.id.btnVerifyOtp)

        btnRefreshAll.setOnClickListener {
            refreshTravelerDoc { doc ->
                if (doc == null) {
                    Toast.makeText(this, "No traveler found", Toast.LENGTH_SHORT).show()
                    return@refreshTravelerDoc
                }

                // Update first mile section
                val firstStatus = doc.getString("FirstMileStatus") ?: "Not Started"
                val firstOtp = doc.getString("FirstMileOTP") ?: ""
                updateFirstMileSectionUI(firstOtp, firstStatus)

                // Update second mile (flight-based) and prepare last when landed
                refreshSecondMileAndMaybeStartLast(doc)

                // Update last mile UI
                val lastStatus = doc.getString("LastMileStatus") ?: "Not Started"
                val lastOtp = doc.getString("LastMileOTP") ?: ""
                updateLastMileSectionUI(lastOtp, lastStatus)

                // Also update contact & map if traveler accepted
                val travelerStatus = doc.getString("status") ?: ""
                if (travelerStatus == "Request Accepted By Traveler") {
                    showTravelerContactAndLocation(doc)
                    setFlightDetailsVisible(true)
                }
                else {
                    setFlightDetailsVisible(false)
                }

            }
        }

        btnVerifyPickupOtp.setOnClickListener {
            val entered = findViewById<EditText>(R.id.etPickupOtpInput).text.toString().trim()
            if (entered.isEmpty()) {
                Toast.makeText(this, "Enter Pickup OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyFirstMileOtp(entered)
        }

        btnVerifyLastOtp.setOnClickListener {
            val entered = findViewById<EditText>(R.id.etOtpInput).text.toString().trim()
            if (entered.isEmpty()) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyLastMileOtp(entered)
        }
    }

    private fun refreshTravelerDoc(callback: (DocumentSnapshot?) -> Unit) {
        val key = uniqueKey ?: sharedPref.getString("uniqueKey", null)
        if (key.isNullOrEmpty()) {
            Log.w("SenderProfile", "refreshTravelerDoc: uniqueKey is null/empty")
            callback(null)
            return
        }

        db.collection("traveler")
            .whereEqualTo("uniqueKey", key)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents[0]
                    travelerDoc = doc
                    Log.d("SenderProfile", "refreshTravelerDoc: traveler doc fetched: id=${doc.id}")
                    // Log a few fields to help debugging
                    try {
                        Log.d("SenderProfile", "traveler fields: phoneNumber=${doc.getString("phoneNumber")}, FlightNumber=${doc.getString("FlightNumber")}, flightNumber=${doc.getString("flightNumber")}, status=${doc.getString("status")}")
                        val fromAddr = doc.get("fromAddress") as? Map<*, *>
                        val toAddr = doc.get("toAddress") as? Map<*, *>
                        Log.d("SenderProfile", "fromAddress keys: ${fromAddr?.keys ?: "null"} toAddress keys: ${toAddr?.keys ?: "null"}")
                    } catch (e: Exception) {
                        Log.w("SenderProfile", "Error logging doc fields", e)
                    }

                    callback(doc)

                    val travelerStatus = doc.getString("status") ?: ""
                    if (travelerStatus == "Request Accepted By Traveler") {
                        setSenderEditingEnabled(false)
                    } else {
                        setSenderEditingEnabled(true)
                    }
                } else {
                    Log.w("SenderProfile", "refreshTravelerDoc: no traveler doc found for key=$key")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error refreshing traveler doc", e)
                callback(null)
            }
    }


    // -------------------------
    // First Mile
    // -------------------------
    private fun updateFirstMileSectionUI(otp: String, status: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.firstmilesender).text = when (status) {
                "In Progress" -> "✓ 1st Stage - In Progress"
                "Completed" -> "✓ 1st Stage - Completed"
                else -> "✓ 1st Stage - Not Started"
            }
            findViewById<TextView>(R.id.tvPickupOtp).text = if (otp.isNotEmpty()) "Pickup OTP: $otp" else ""

            // Show/hide contact buttons based on first mile status
            val btnCallTraveler = findViewById<Button>(R.id.btnCallTraveler)
            val btnGetDirections = findViewById<Button>(R.id.btnGetDirections)

            if (status == "In Progress" || status == "Completed") {
                btnCallTraveler?.visibility = View.VISIBLE
                btnGetDirections?.visibility = View.VISIBLE
            } else {
                btnCallTraveler?.visibility = View.GONE
                btnGetDirections?.visibility = View.GONE
            }
        }
    }


    // Add these methods to handle phone calls and directions
    private fun setupFirstMileContactButtons() {
        val btnCallTraveler = findViewById<Button>(R.id.btnCallTraveler)
        val btnGetDirections = findViewById<Button>(R.id.btnGetDirections)

        btnCallTraveler.setOnClickListener {
            makePhoneCallToTraveler()
        }

        btnGetDirections.setOnClickListener {
            openDirectionsToTraveler()
        }
    }

    private fun makePhoneCallToTraveler() {
        val travelerPhone = travelerDoc?.getString("phoneNumber") ?: travelerDoc?.getString("phone") ?: ""

        if (travelerPhone.isNotEmpty()) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$travelerPhone"))
                startActivity(intent)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CALL_PHONE), PERMISSION_CALL_PHONE)
            }
        } else {
            Toast.makeText(this, "Traveler phone number not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDirectionsToTraveler() {
        val travelerLocation = extractTravelerLocation()

        travelerLocation?.let { (lat, lng) ->
            val uri = "google.navigation:q=$lat,$lng"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback: Open in browser
                val webUri = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
                startActivity(webIntent)
            }
        } ?: run {
            Toast.makeText(this, "Traveler location not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractTravelerLocation(): Pair<Double, Double>? {
        val doc = travelerDoc ?: return null

        // Try to extract latitude and longitude from various field names
        val lat = extractDouble(doc.get("latitude"))
            ?: extractDouble((doc.get("fromAddress") as? Map<*, *>)?.get("latitude"))
            ?: extractDouble((doc.get("toAddress") as? Map<*, *>)?.get("latitude"))

        val lng = extractDouble(doc.get("longitude"))
            ?: extractDouble((doc.get("fromAddress") as? Map<*, *>)?.get("longitude"))
            ?: extractDouble((doc.get("toAddress") as? Map<*, *>)?.get("longitude"))

        return if (lat != null && lng != null) Pair(lat, lng) else null
    }

    // Handle permission results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_CALL_PHONE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    makePhoneCallToTraveler()
                } else {
                    Toast.makeText(this, "Phone call permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            PERMISSION_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openDirectionsToTraveler()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun verifyFirstMileOtp(enteredOtp: String) {
        val doc = travelerDoc
        val sender = senderDoc

        if (doc == null) {
            Toast.makeText(this, "No traveler loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val realOtp = doc.getString("FirstMileOTP") ?: ""
        val travelerRef = doc.reference

        if (enteredOtp == realOtp && realOtp.isNotEmpty()) {
            val travelerUpdates = mapOf<String, Any>(
                "FirstMileStatus" to "Completed",
                "SecondMileStatus" to "In Progress"  // Set second mile status in traveler document
            )
            val senderUpdates = mapOf<String, Any>(
                "FirstMileStatus" to "Completed",
                "SecondMileStatus" to "In Progress"  // Also update sender document
            )

            // Update traveler document first
            travelerRef.update(travelerUpdates)
                .addOnSuccessListener {
                    Log.d("SenderProfile", "Successfully updated traveler FirstMileStatus to Completed and SecondMileStatus to In Progress")

                    // Then update sender document
                    try {
                        val phoneNumber = sender?.getString("phoneNumber") ?: sharedPref.getString("PHONE_NUMBER", null)
                        if (!phoneNumber.isNullOrBlank()) {
                            db.collection("Sender").document(phoneNumber)
                                .update(senderUpdates)
                                .addOnSuccessListener {
                                    Log.d("SenderProfile", "Successfully updated sender FirstMileStatus to Completed and SecondMileStatus to In Progress")
                                    showSecondMileStartedMessage(doc)
                                    refreshTravelerDoc { refreshed ->
                                        refreshed?.let {
                                            updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "Completed")
                                            updateSecondMileUI(it)
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("SenderProfile", "Error updating sender document", e)
                                    // Even if sender update fails, show success message since traveler update succeeded
                                    showSecondMileStartedMessage(doc)
                                    refreshTravelerDoc { refreshed ->
                                        refreshed?.let {
                                            updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "Completed")
                                            updateSecondMileUI(it)
                                        }
                                    }
                                }
                        } else {
                            // No sender phone found - still proceed with traveler update
                            Log.w("SenderProfile", "No sender phone number found, but traveler updated successfully")
                            showSecondMileStartedMessage(doc)
                            refreshTravelerDoc { refreshed ->
                                refreshed?.let {
                                    updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "Completed")
                                    updateSecondMileUI(it)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SenderProfile", "Exception while updating sender", e)
                        showSecondMileStartedMessage(doc)
                        refreshTravelerDoc { refreshed ->
                            refreshed?.let {
                                updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "Completed")
                                updateSecondMileUI(it)
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("SenderProfile", "Error updating traveler document", e)
                    Toast.makeText(this, "Failed to update status in traveler record", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSecondMileStartedMessage(travelerDoc: DocumentSnapshot) {
        val flightNumber = travelerDoc.getString("flightNumber")
            ?: travelerDoc.getString("FlightNumber")
            ?: "N/A"

        val trackingUrl = if (flightNumber != "N/A") {
            "https://www.flightaware.com/live/flight/$flightNumber"
        } else {
            "Flight tracking not available"
        }

        val message = "✓ First stage completed! Traveler is flying with second stage.\n\nFlight Tracking: $trackingUrl"

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Second Stage Started")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()

            // Update UI to reflect second mile in progress
            findViewById<TextView>(R.id.secondmilesender)?.text = "⏳ 2nd Stage - In Progress"
            findViewById<TextView>(R.id.tvTransitStatus)?.text = "Traveler is flying with your package\nFlight: $flightNumber\nTracking: $trackingUrl"

            // Also update the tracking URL at the bottom
            findViewById<TextView>(R.id.trackingUrl)?.text = "Flight Tracking: $trackingUrl"
        }

        // Log the successful status update
        Log.d("SenderProfile", "Second mile started - Flight: $flightNumber, Tracking: $trackingUrl")
    }
    private fun verifySecondMileStatusUpdate() {
        refreshTravelerDoc { doc ->
            doc?.let {
                val secondMileStatus = it.getString("SecondMileStatus") ?: "Not Started"
                Log.d("SenderProfile", "Current SecondMileStatus in database: $secondMileStatus")

                if (secondMileStatus == "In Progress") {
                    // Successfully updated in database
                    Toast.makeText(this, "Second stage successfully started!", Toast.LENGTH_SHORT).show()
                } else {
                    // Something went wrong, try to update again
                    Log.w("SenderProfile", "SecondMileStatus not updated properly, attempting to fix...")
                    it.reference.update("SecondMileStatus", "In Progress")
                        .addOnSuccessListener {
                            Log.d("SenderProfile", "Fixed SecondMileStatus to In Progress")
                        }
                        .addOnFailureListener { e ->
                            Log.e("SenderProfile", "Failed to fix SecondMileStatus", e)
                        }
                }
            }
        }
    }
    private fun updateSecondMileUI(travelerDoc: DocumentSnapshot) {
        val flightNumber = travelerDoc.getString("flightNumber")
            ?: travelerDoc.getString("FlightNumber")
            ?: "N/A"

        val trackingUrl = if (flightNumber != "N/A") {
            "https://www.flightaware.com/live/flight/$flightNumber"
        } else {
            "Flight tracking not available"
        }

        runOnUiThread {
            findViewById<TextView>(R.id.secondmilesender)?.text = "⏳ 2nd Stage - In Progress"
            findViewById<TextView>(R.id.tvTransitStatus)?.text = "Traveler is flying with your package\nFlight: $flightNumber\nTracking: $trackingUrl"
        }
    }
    // Add this function somewhere in the class (e.g., near other helpers)
    private fun ensureFirstMileStarted() {
        // If travelerDoc already present, just call initiate
        if (travelerDoc != null) {
            initiateFirstMile()
            return
        }

        // Otherwise try to fetch traveler quickly by uniqueKey and then initiate
        val key = uniqueKey ?: sharedPref.getString("uniqueKey", null)
        if (key.isNullOrBlank()) {
            Log.w("SenderProfile", "ensureFirstMileStarted: uniqueKey missing, cannot start first mile")
            return
        }

        db.collection("traveler")
            .whereEqualTo("uniqueKey", key)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    travelerDoc = snap.documents[0]
                    initiateFirstMile()
                } else {
                    Log.w("SenderProfile", "ensureFirstMileStarted: no traveler doc found for key=$key")
                    // still initiate sender-side so UI shows In Progress
                    initiateFirstMile()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error fetching traveler to initiate first mile", e)
                // fallback: still try to set Sender doc to In Progress so UI reflects it
                initiateFirstMile()
            }
    }


    // Optional helper to initiate first mile (generates OTP & sets In Progress) — not auto-called by Refresh
    // Replace the existing initiateFirstMile() with this:
    private fun initiateFirstMile() {
        val tDoc = travelerDoc
        val sDoc = senderDoc

        if (tDoc == null && sDoc == null) {
            Log.w("SenderProfile", "initiateFirstMile: no traveler or sender docs available")
            return
        }

        // Check current state from traveler first (prefer traveler copy)
        val firstStatusTraveler = tDoc?.getString("FirstMileStatus")
        val firstStatusSender = sDoc?.getString("FirstMileStatus")
        val currentStatus = firstStatusTraveler ?: firstStatusSender ?: "Not Started"

        if (currentStatus == "Not Started" || currentStatus.isBlank()) {
            val generated = generateOtp()

            val updatesForTraveler = mapOf(
                "FirstMileOTP" to generated,
                "FirstMileStatus" to "In Progress"
            )

            val updatesForSender = mapOf(
                "FirstMileOTP" to generated,
                "FirstMileStatus" to "In Progress"
            )

            // Update traveler doc if available
            tDoc?.reference?.update(updatesForTraveler)
                ?.addOnSuccessListener {
                    Log.d("SenderProfile", "Traveler FirstMile initiated with OTP $generated")
                    // also try update sender doc
                    try {
                        val phoneNumber = sDoc?.getString("phoneNumber") ?: sharedPref.getString("PHONE_NUMBER", null)
                        if (!phoneNumber.isNullOrBlank()) {
                            db.collection("Sender").document(phoneNumber)
                                .update(updatesForSender)
                                .addOnSuccessListener {
                                    Log.d("SenderProfile", "Sender FirstMile fields updated to In Progress")
                                    // refresh local docs
                                    refreshTravelerDoc { refreshed ->
                                        // update UI
                                        refreshed?.let {
                                            updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "In Progress")
                                        }
                                    }
                                    loadAddressData()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("SenderProfile", "Error updating Sender FirstMile fields", e)
                                }
                        } else {
                            Log.w("SenderProfile", "initiateFirstMile: no sender phoneNumber to update")
                        }
                    } catch (e: Exception) {
                        Log.e("SenderProfile", "Error updating sender after traveler update", e)
                    }
                }
                ?.addOnFailureListener { e ->
                    Log.e("SenderProfile", "Error initiating first mile on traveler doc", e)
                }

            // If traveler doc not available, still attempt to update Sender doc so sender UI shows In Progress
            if (tDoc == null) {
                val phoneNumber = sDoc?.getString("phoneNumber") ?: sharedPref.getString("PHONE_NUMBER", null)
                if (!phoneNumber.isNullOrBlank()) {
                    db.collection("Sender").document(phoneNumber)
                        .update(updatesForSender)
                        .addOnSuccessListener {
                            Log.d("SenderProfile", "Sender FirstMile fields updated even though traveler missing")
                            // show UI update
                            runOnUiThread {
                                findViewById<TextView>(R.id.fileMileMainStatus)?.text = "✓ 1st Stage - In Progress"
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("SenderProfile", "Error updating Sender FirstMile when traveler missing", e)
                        }
                }
            }
        } else {
            Log.d("SenderProfile", "initiateFirstMile: already started or completed (status=$currentStatus)")
        }
    }

    /**
     * Toggle visibility of flight-related UI. When false, clears flight fields and hides the flight card.
     */
    private fun setFlightDetailsVisible(visible: Boolean) {
        runOnUiThread {
            val flightCard = findViewById<CardView>(R.id.flightInfoCard)
            val tvFromCity = findViewById<TextView>(R.id.tvFromCity)
            val tvToCity = findViewById<TextView>(R.id.tvToCity)
            val tvFlightStatus = findViewById<TextView>(R.id.tvFlightStatus)
            val tvFromTime = findViewById<TextView>(R.id.tvFromTime)
            val tvToTime = findViewById<TextView>(R.id.tvToTime)
            val tvFlightNumber = findViewById<TextView>(R.id.tvFlightNumber)
            val trackingUrl = findViewById<TextView>(R.id.trackingUrl)

            flightCard?.visibility = if (visible) View.VISIBLE else View.GONE

            if (!visible) {
                // clear fields to avoid leaking info
                tvFromCity?.text = "N/A"
                tvToCity?.text = "N/A"
                tvFlightStatus?.text = "Airline: N/A"
                tvFromTime?.text = "Departure: N/A"
                tvToTime?.text = "Arrival: N/A"
                tvFlightNumber?.text = "Flight: N/A"
                trackingUrl?.text = "Flight Tracking: N/A"
            }
        }
    }

    // -------------------------
    // Second Mile (flight-based)
    // -------------------------
    private fun refreshSecondMileAndMaybeStartLast(doc: DocumentSnapshot) {
        val departureStr = doc.getString("departureTime") ?: doc.getString("leavingDate") ?: ""
        val arrivalStr = doc.getString("arrivalTime") ?: ""
        val flightNumber = doc.getString("flightNumber") ?: ""
        val secondTv = findViewById<TextView>(R.id.secondmilesender)
        val transitStatusTv = findViewById<TextView>(R.id.tvTransitStatus)
        val lastUpdatedTv = findViewById<TextView>(R.id.tvLastUpdated)

        try {
            val now = Date()
            val parserCandidates = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            )

            fun parseFlexible(input: String): Date? {
                if (input.isBlank()) return null
                for (p in parserCandidates) {
                    try {
                        p.timeZone = TimeZone.getDefault()
                        return p.parse(input)
                    } catch (_: Exception) {
                    }
                }
                return null
            }

            val dep = parseFlexible(departureStr)
            val arr = parseFlexible(arrivalStr)

            if (dep == null || arr == null) {
                secondTv.text = "⏳ 2nd Stage - Unknown (flight info missing)"
                transitStatusTv.text = "Flight: $flightNumber"
                lastUpdatedTv.text = "Updated: ${SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault()).format(Date())}"
                if (flightNumber.isNotBlank()) {
                    fetchFlightStatusFromFlightAware(flightNumber) { statusText ->
                        runOnUiThread { transitStatusTv.text = statusText }
                    }
                }
                return
            }

            // Check current second mile status from database
            val currentSecondMileStatus = doc.getString("SecondMileStatus") ?: "Not Started"

            when {
                now.before(dep) -> {
                    secondTv.text = "⏳ 2nd Stage - Not Started"
                    transitStatusTv.text = "Flight scheduled to depart at ${formatLocal(dep)}"

                    // If somehow second mile was marked as in progress but flight hasn't departed, correct it
                    if (currentSecondMileStatus == "In Progress") {
                        doc.reference.update("SecondMileStatus", "Not Started")
                    }
                }
                now.after(dep) && now.before(arr) -> {
                    secondTv.text = "⏳ 2nd Stage - In Transit"
                    transitStatusTv.text = "Flight is airborne"

                    // Ensure second mile status is set to In Progress in database
                    if (currentSecondMileStatus != "In Progress") {
                        doc.reference.update("SecondMileStatus", "In Progress")
                            .addOnSuccessListener {
                                Log.d("SenderProfile", "Updated SecondMileStatus to In Progress based on flight timing")
                            }
                            .addOnFailureListener { e ->
                                Log.e("SenderProfile", "Failed to update SecondMileStatus to In Progress", e)
                            }
                    }

                    if (flightNumber.isNotBlank()) {
                        fetchFlightStatusFromFlightAware(flightNumber) { statusText ->
                            runOnUiThread { transitStatusTv.text = statusText }
                        }
                    }
                }
                now.after(arr) -> {
                    secondTv.text = "✓ 2nd Stage - Completed"
                    transitStatusTv.text = "Flight landed at ${formatLocal(arr)}"

                    // Update second mile status to Completed if not already
                    if (currentSecondMileStatus != "Completed") {
                        doc.reference.update("SecondMileStatus", "Completed")
                            .addOnSuccessListener {
                                Log.d("SenderProfile", "Updated SecondMileStatus to Completed based on flight arrival")
                            }
                            .addOnFailureListener { e ->
                                Log.e("SenderProfile", "Failed to update SecondMileStatus to Completed", e)
                            }
                    }

                    val lastStatus = doc.getString("LastMileStatus") ?: "Not Started"
                    if (lastStatus == "Not Started") {
                        val lastOtp = generateOtp()
                        doc.reference.update(
                            mapOf(
                                "LastMileOTP" to lastOtp,
                                "LastMileStatus" to "In Progress"
                            )
                        ).addOnSuccessListener {
                            Log.d("SenderProfile", "Initiated Last Mile with OTP")
                            refreshTravelerDoc { refreshed ->
                                refreshed?.let {
                                    updateLastMileSectionUI(it.getString("LastMileOTP") ?: "", it.getString("LastMileStatus") ?: "In Progress")
                                }
                            }
                        }.addOnFailureListener { e ->
                            Log.e("SenderProfile", "Error initiating last mile", e)
                        }
                    }
                }
            }

            lastUpdatedTv.text = "Updated: ${SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault()).format(Date())}"
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error processing second mile", e)
            findViewById<TextView>(R.id.secondmilesender).text = "⏳ 2nd Stage - Unknown"
        }
    }

    private fun formatLocal(d: Date): String {
        return SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault()).format(d)
    }

    // Placeholder for FlightAware integration (replace with real API)
    private fun fetchFlightStatusFromFlightAware(flightNumber: String, callback: (String) -> Unit) {
        callback("Flight $flightNumber — live status unavailable (add FlightAware integration)")
    }

    // -------------------------
    // Last Mile
    // -------------------------
    private fun updateLastMileSectionUI(otp: String, status: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.lastmilestatussender).text = when (status) {
                "In Progress" -> "📍 3rd Stage - In Progress"
                "Completed" -> "📍 3rd Stage - Completed"
                else -> "📍 3rd Stage - Not Started"
            }
            findViewById<TextView>(R.id.tvDeliveryOtp).text = if (otp.isNotEmpty()) "Delivery OTP: $otp" else ""
        }
    }

    private fun verifyLastMileOtp(enteredOtp: String) {
        val doc = travelerDoc
        if (doc == null) {
            Toast.makeText(this, "No traveler loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val realOtp = doc.getString("LastMileOTP") ?: ""
        val docRef = doc.reference

        if (enteredOtp == realOtp && realOtp.isNotEmpty()) {
            docRef.update(mapOf(
                "LastMileStatus" to "Completed",
                "status" to "Completed"
            )).addOnSuccessListener {
                Toast.makeText(this, "Delivery OTP Verified. Order completed.", Toast.LENGTH_SHORT).show()
                refreshTravelerDoc { refreshed ->
                    refreshed?.let {
                        updateLastMileSectionUI(it.getString("LastMileOTP") ?: "", it.getString("LastMileStatus") ?: "Completed")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("SenderProfile", "Error updating last mile status", e)
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------
    // Small helpers
    // -------------------------
    private fun generateOtp(): String {
        val number = Random.nextInt(100000, 999999)
        return number.toString()
    }

    private fun setSenderEditingEnabled(enabled: Boolean) {
        val btnEditFrom = findViewById<Button>(R.id.btnEditFromAddress)
        val btnEditTo = findViewById<Button>(R.id.btnEditToAddress)
        val btnEditItem = findViewById<Button>(R.id.btnEditItemDetails)

        btnEditFrom?.let {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.45f
        }
        btnEditTo?.let {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.45f
        }
        btnEditItem?.let {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun showTravelerContactAndLocation(doc: DocumentSnapshot?) {
        if (doc == null) {
            Log.w("SenderProfile", "showTravelerContactAndLocation: doc is null")
            return
        }

        // 1) Try phone from different possible fields
        val phoneCandidates = listOf("phoneNumber", "phone", "contact", "travelerPhone")
        var phone: String? = null
        for (field in phoneCandidates) {
            try {
                val v = doc.getString(field)
                if (!v.isNullOrBlank()) {
                    phone = v
                    break
                }
            } catch (_: Exception) { /* ignore */ }
        }

        // 2) Try lat/lng from multiple locations:
        // - top-level "latitude"/"longitude"
        // - nested fromAddress.latitude/fromAddress.longitude
        // - nested toAddress.latitude/toAddress.longitude
        // - nested coordinates as strings (try to parse)
        var lat: Double? = null
        var lng: Double? = null

        // helper to extract Number -> Double
        fun numToDouble(value: Any?): Double? {
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }

        // top-level
        lat = numToDouble(doc.get("latitude"))
        lng = numToDouble(doc.get("longitude"))

        // try fromAddress
        if (lat == null || lng == null) {
            val fromAddr = doc.get("fromAddress") as? Map<*, *>
            if (fromAddr != null) {
                if (lat == null) lat = numToDouble(fromAddr["latitude"])
                if (lng == null) lng = numToDouble(fromAddr["longitude"])
            }
        }

        // try toAddress
        if (lat == null || lng == null) {
            val toAddr = doc.get("toAddress") as? Map<*, *>
            if (toAddr != null) {
                if (lat == null) lat = numToDouble(toAddr["latitude"])
                if (lng == null) lng = numToDouble(toAddr["longitude"])
            }
        }

        // If still null, try nested keys with different names (lat, long)
        if (lat == null || lng == null) {
            val fromAddr = doc.get("fromAddress") as? Map<*, *>
            val toAddr = doc.get("toAddress") as? Map<*, *>
            val candidates = listOf(fromAddr, toAddr)
            for (map in candidates) {
                if (map == null) continue
                if (lat == null) lat = numToDouble(map["lat"]) ?: numToDouble(map["Lat"]) ?: numToDouble(map["latitude"]) ?: numToDouble(map["Latitude"])
                if (lng == null) lng = numToDouble(map["lng"]) ?: numToDouble(map["Lng"]) ?: numToDouble(map["longitude"]) ?: numToDouble(map["Longitude"])
            }
        }

        // Logging for debugging
        Log.d("SenderProfile", "showTravelerContactAndLocation: phone=$phone lat=$lat lng=$lng")

        // Update UI textviews
        val tvPhone = findViewById<TextView>(R.id.tvTravelerPhone)
        val tvLocation = findViewById<TextView>(R.id.tvTravelerLocation)

        runOnUiThread {
            if (!phone.isNullOrBlank()) {
                tvPhone?.text = phone
            } else {
                tvPhone?.text = "N/A"
            }

            if (lat != null && lng != null) {
                val coordText = "Lat: %.6f, Lng: %.6f".format(lat, lng)
                tvLocation?.text = coordText

                // show on Google Map: if map ready add marker, else keep pendingLatLng
                val latLng = com.google.android.gms.maps.model.LatLng(lat, lng)
                if (googleMap != null) {
                    try {
                        googleMap?.clear()
                        googleMap?.addMarker(
                            com.google.android.gms.maps.model.MarkerOptions()
                                .position(latLng)
                                .title(if (!phone.isNullOrBlank()) "Traveler: $phone" else "Traveler")
                        )
                        googleMap?.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                    } catch (e: Exception) {
                        Log.e("SenderProfile", "Error updating map marker", e)
                    }
                } else {
                    // store pending so onMapReady will apply it
                    pendingLatLng = latLng
                    Log.d("SenderProfile", "Map not ready — saved pendingLatLng")
                }
            } else {
                // fallback to textual address if available
                val fromAddrText = (doc.get("fromAddress") as? Map<*, *>)?.get("fullAddress") as? String
                val toAddrText = (doc.get("toAddress") as? Map<*, *>)?.get("fullAddress") as? String
                val fallback = fromAddrText ?: toAddrText ?: "Location not available"
                tvLocation?.text = fallback

                // clear marker if present
                googleMap?.clear()
            }
        }
    }

    private fun extractDouble(value: Any?): Double? {
        if (value == null) return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().takeIf { it.isNotEmpty() }?.let {
                // handle numbers with commas etc.
                try {
                    it.replace(",", "").toDouble()
                } catch (e: Exception) {
                    null
                }
            }
            is com.google.firebase.firestore.GeoPoint -> value.latitude // caller must also read longitude separately
            else -> null
        }
    }
    private fun extractGeoPointOrMapLatLng(obj: Any?): Pair<Double, Double>? {
        if (obj == null) return null
        try {
            when (obj) {
                is com.google.firebase.firestore.GeoPoint -> {
                    return Pair(obj.latitude, obj.longitude)
                }
                is Map<*, *> -> {
                    val lat = extractDouble(obj["latitude"]) ?: extractDouble(obj["lat"]) ?: extractDouble(obj["latValue"])
                    val lng = extractDouble(obj["longitude"]) ?: extractDouble(obj["lng"]) ?: extractDouble(obj["lon"]) ?: extractDouble(obj["lngValue"])
                    if (lat != null && lng != null) return Pair(lat, lng)
                }
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "extractGeoPointOrMapLatLng error: ${e.message}", e)
        }
        return null
    }
    private fun hideTravelerContactAndLocation() {
        runOnUiThread {
            findViewById<TextView>(R.id.tvTravelerPhone)?.text = "N/A"
            findViewById<TextView>(R.id.tvTravelerLocation)?.text = "Lat: N/A, Lng: N/A"
            googleMap?.clear()
        }
    }

    /**
     * Places marker and moves camera to given location on the map.
     * If map not ready yet, stores pending coords and will be applied once map ready.
     */
    private fun showTravelerOnMap(lat: Double, lng: Double) {
        val latLng = LatLng(lat, lng)
        if (googleMap == null) {
            pendingLatLng = latLng
            return
        }

        googleMap?.let { map ->
            try {
                map.clear()
                val markerOpt = MarkerOptions()
                    .position(latLng)
                    .title("Traveler")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))

                map.addMarker(markerOpt)
                val zoom = 15f
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
            } catch (e: Exception) {
                Log.e("SenderProfile", "Error showing traveler on map: ${e.message}", e)
            }
        }
    }

    // -------------------------
    // UI load functions (flight/item/address)
    // -------------------------
    private fun loadAddressData() {
        val doc = senderDoc ?: return

        val fromAddress = doc.getString("fromAddress.fullAddress") ?: ""
        val toAddress = doc.getString("toAddress.fullAddress") ?: ""

        val firstMileStatus = doc.getString("FirstMileStatus") ?: "Not Started"
        val secondMileStatus = doc.getString("SecondMileStatus") ?: "Not Started"
        val lastMileStatus = doc.getString("LastMileStatus") ?: "Not Started"

        findViewById<TextView>(R.id.fileMileMainStatus)?.text = "✓ 1st Stage - $firstMileStatus"
        findViewById<TextView>(R.id.tvFromAddress).text = fromAddress
        findViewById<TextView>(R.id.tvToAddress).text = toAddress

        if (firstMileStatus == "Completed") {
            findViewById<TextView>(R.id.secondmilesender)?.text = "✓ 2nd stage - Started"
        }
        if (secondMileStatus == "Completed") {
            findViewById<TextView>(R.id.secondmilesender)?.text = "✓ 2nd stage - Completed"
            findViewById<TextView>(R.id.lastmilestatussender)?.text = "✓ 3rd stage - Started"
        }
    }

    private fun loadItemDetails() {
        val doc = senderDoc ?: return

        val itemDetails = doc.get("itemDetails") as? Map<*, *>
        itemDetails?.let {
            val name = it["itemName"] as? String ?: "N/A"
            val kg = (it["weightKg"] as? Number)?.toInt() ?: 0
            val gram = (it["weightGram"] as? Number)?.toInt() ?: 0
            val instructions = it["instructions"] as? String ?: "N/A"

            val price = doc.getLong("deliveryOptionPrice")?.toInt() ?: 750
            val deliveryOption = if (price == 750) "Self Pickup" else "Auto Pickup"

            findViewById<TextView>(R.id.tvItemName).text = "Item: $name"
            findViewById<TextView>(R.id.tvItemWeight).text = "Weight: $kg kg $gram g"
            findViewById<TextView>(R.id.tvItemInstructions).text = "Instructions: $instructions"
            findViewById<TextView>(R.id.tvDeliveryOption).text = "Delivery Option: $deliveryOption"
        }
    }

    private fun loadStatusData() {
        if (uniqueKey.isNullOrEmpty()) return

        db.collection("borzo_orders")
            .whereEqualTo("uniqueKey", uniqueKey)
            .limit(1)
            .get()
            .addOnSuccessListener { orderQuery ->
                if (!orderQuery.isEmpty) {
                    val document = orderQuery.documents[0]
                    val status = document.getString("order.status") ?: "N/A"
                    findViewById<TextView>(R.id.subStatus).text = status
                }
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error loading status data", e)
            }
    }

    private fun updateFlightUI(travelerDoc: DocumentSnapshot) {
        try {
            // For debugging: log full doc data (remove in production)
            Log.d("SenderProfile", "updateFlightUI travelerDoc: ${travelerDoc.data}")

            // determine status early
            val status = travelerDoc.getString("status") ?: travelerDoc.getString("flightStatus") ?: ""
            val accepted = status == "Request Accepted By Traveler"

            // If traveler not accepted, hide flight details and exit early
            if (!accepted) {
                setFlightDetailsVisible(false)
                return
            }

            // Get city names from nested addresses if present
            val fromAddress = travelerDoc.get("fromAddress") as? Map<*, *>
            val toAddress = travelerDoc.get("toAddress") as? Map<*, *>

            val fromCity = (fromAddress?.get("city") as? String)
                ?: travelerDoc.getString("fromCity")
                ?: "N/A"
            val toCity = (toAddress?.get("city") as? String)
                ?: travelerDoc.getString("toCity")
                ?: "N/A"

            // airline / flight number — try multiple variants
            val airline = travelerDoc.getString("airline")
                ?: travelerDoc.getString("Airline")
                ?: "N/A"

            val flightNumber = travelerDoc.getString("flightNumber")
                ?: travelerDoc.getString("FlightNumber")
                ?: travelerDoc.getString("FlightNo")
                ?: travelerDoc.getString("flight_no")
                ?: "N/A"

            // departure / arrival times (strings)
            val departureTimeStr = travelerDoc.getString("departureTime")
                ?: travelerDoc.getString("leavingDate") // fallback
                ?: "N/A"

            val formattedDepartureTime = formatDateTime(departureTimeStr)
            val formattedArrivalTime = calculateArrivalTime(departureTimeStr)

            runOnUiThread {
                findViewById<TextView>(R.id.tvFromCity).text = fromCity
                findViewById<TextView>(R.id.tvToCity).text = toCity
                findViewById<TextView>(R.id.tvFlightStatus).text = "Airline: $airline"
                findViewById<TextView>(R.id.tvFromTime).text = "Departure: $formattedDepartureTime"
                findViewById<TextView>(R.id.tvToTime).text = "Arrival: $formattedArrivalTime"
                findViewById<TextView>(R.id.tvFlightNumber).text = "Flight: $flightNumber"

                val statusTextView = findViewById<TextView>(R.id.subStatus)
                statusTextView.text = "Flight Status: $status"

                when (status.lowercase(Locale.getDefault())) {
                    "scheduled", "ontime", "request accepted by traveler", "scheduled" -> statusTextView.setTextColor(Color.GREEN)
                    "delayed", "in progress", "in-air", "airborne" -> statusTextView.setTextColor(Color.YELLOW)
                    "cancelled", "rejected" -> statusTextView.setTextColor(Color.RED)
                    else -> statusTextView.setTextColor(Color.GRAY)
                }

                // also update tracking URL using the chosen flightNumber
                val trackingUrl = if (flightNumber != "N/A" && flightNumber.isNotBlank()) {
                    "https://www.flightaware.com/live/flight/$flightNumber"
                } else {
                    "Flight tracking not available"
                }
                findViewById<TextView>(R.id.trackingUrl).text = "Flight Tracking: $trackingUrl"

                // Update traveler panel fields as well
                updateTravelerUI(travelerDoc)
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error updating flight UI", e)
        }
    }



    private fun formatDateTime(dateTimeStr: String): String {
        return try {
            if (dateTimeStr.contains("T")) {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateTimeStr)
                outputFormat.format(date)
            } else {
                dateTimeStr
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error formatting date", e)
            dateTimeStr
        }
    }

    private fun calculateArrivalTime(departureTimeStr: String): String {
        return try {
            if (departureTimeStr.contains("T")) {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault())
                val date = inputFormat.parse(departureTimeStr)
                val arrivalDate = Date(date.time + (2 * 60 * 60 * 1000))
                outputFormat.format(arrivalDate)
            } else {
                "N/A"
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error calculating arrival time", e)
            "N/A"
        }
    }

    private fun updateTravelerUI(travelerDoc: DocumentSnapshot) {
        try {
            val lastName = travelerDoc.getString("lastName") ?: "N/A"
            val airline = travelerDoc.getString("airline") ?: "N/A"
            val flightNumber = travelerDoc.getString("flightNumber") ?: "N/A"
            val departureTime = travelerDoc.getString("departureTime") ?: "N/A"
            val arrivalTime = travelerDoc.getString("arrivalTime") ?: "N/A"
            val weightUpto = travelerDoc.getString("weightUpto") ?: "0"
            val destination = travelerDoc.getString("toPlace") ?: "N/A"

            runOnUiThread {
                findViewById<TextView>(R.id.tvTravelerName)?.text = lastName
                findViewById<TextView>(R.id.tvTravelerAirline)?.text = airline
                findViewById<TextView>(R.id.tvTravelerFlightNumber)?.text = flightNumber
                findViewById<TextView>(R.id.tvTravelerDeparture)?.text = departureTime
                findViewById<TextView>(R.id.tvTravelerArrival)?.text = arrivalTime
                findViewById<TextView>(R.id.tvTravelerDestination)?.text = destination
                findViewById<TextView>(R.id.tvTravelerWeight)?.text = "$weightUpto kg"
            }
        } catch (e: Exception) {
            Log.e("SenderProfile", "Error updating traveler UI", e)
            Toast.makeText(this, "Error displaying traveler details", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------
    // Address / Item edit dialogs and Firestore updates
    // -------------------------
    private fun showEditAddressDialog(currentAddressField: String, onSave: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_address, null)
        val etStreet = dialogView.findViewById<EditText>(R.id.etStreet)
        val etHouseNumber = dialogView.findViewById<EditText>(R.id.etHouseNumber)
        val etArea = dialogView.findViewById<EditText>(R.id.etArea)
        val etCity = dialogView.findViewById<EditText>(R.id.etCity)
        val etState = dialogView.findViewById<EditText>(R.id.etState)
        val etPostalCode = dialogView.findViewById<EditText>(R.id.etPostalCode)

        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        // Fetch current address details
        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val address = document.get(currentAddressField) as? Map<String, Any>

                    etStreet.setText(address?.get("street") as? String ?: "")
                    etHouseNumber.setText(address?.get("houseNumber") as? String ?: "")
                    etArea.setText(address?.get("area") as? String ?: "")
                    etCity.setText(address?.get("city") as? String ?: "")
                    etState.setText(address?.get("state") as? String ?: "")
                    etPostalCode.setText(address?.get("postalCode") as? String ?: "")
                }
            }

        AlertDialog.Builder(this)
            .setTitle("Edit Address")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val street = etStreet.text.toString().trim()
                val houseNumber = etHouseNumber.text.toString().trim()
                val area = etArea.text.toString().trim()
                val city = etCity.text.toString().trim()
                val state = etState.text.toString().trim()
                val postalCode = etPostalCode.text.toString().trim()

                val fullAddress = buildString {
                    if (street.isNotEmpty()) append(street)
                    if (houseNumber.isNotEmpty()) append(", $houseNumber")
                    if (area.isNotEmpty()) append(", $area")
                    if (city.isNotEmpty()) append(", $city")
                    if (state.isNotEmpty()) append(", $state")
                    if (postalCode.isNotEmpty()) append(" - $postalCode")
                }

                updateCompleteAddressInFirestore(currentAddressField, street, houseNumber, area, city, state, postalCode)
                onSave(fullAddress)
                Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCompleteAddressInFirestore(
        addressType: String,
        street: String,
        houseNumber: String,
        area: String,
        city: String,
        state: String,
        postalCode: String
    ) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return

        val addressMap = hashMapOf(
            "street" to street,
            "houseNumber" to houseNumber,
            "area" to area,
            "city" to city,
            "state" to state,
            "postalCode" to postalCode,
            "fullAddress" to "$street, $houseNumber, $area, $city, $state - $postalCode"
        )

        val updates = hashMapOf<String, Any>(
            addressType to addressMap
        )

        db.collection("Sender").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("SenderProfile", "Complete address updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("SenderProfile", "Error updating complete address", e)
            }
    }

    private fun showEditItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_item_details, null)

        val currentName = findViewById<TextView>(R.id.tvItemName).text.toString().replace("Item: ", "")
        val currentWeight = findViewById<TextView>(R.id.tvItemWeight).text.toString().replace("Weight: ", "")
        val currentInstructions = findViewById<TextView>(R.id.tvItemInstructions).text.toString().replace("Instructions: ", "")
        val currentDeliveryOption = findViewById<TextView>(R.id.tvDeliveryOption).text.toString().replace("Delivery Option: ", "")

        val etItemName = dialogView.findViewById<EditText>(R.id.etItemName)
        val etKg = dialogView.findViewById<EditText>(R.id.etKg)
        val etGram = dialogView.findViewById<EditText>(R.id.etGram)
        val etInstructions = dialogView.findViewById<EditText>(R.id.etInstructions)
        val rgDeliveryOption = dialogView.findViewById<RadioGroup>(R.id.rgDeliveryOption)

        etItemName.setText(currentName)
        etInstructions.setText(currentInstructions)

        val weightParts = currentWeight.split(" ")
        if (weightParts.size >= 2) {
            val kg = weightParts[0].toIntOrNull() ?: 0
            etKg.setText(kg.toString())
        }
        if (weightParts.size >= 4) {
            val gram = weightParts[2].toIntOrNull() ?: 0
            etGram.setText(gram.toString())
        }

        when (currentDeliveryOption) {
            "Self Pickup" -> rgDeliveryOption.check(R.id.rbSelfPickup)
            "Auto Pickup" -> rgDeliveryOption.check(R.id.rbAutoPickup)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Item Details")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etItemName.text.toString().trim()
                val kg = etKg.text.toString().toIntOrNull() ?: 0
                val gram = etGram.text.toString().toIntOrNull() ?: 0
                val instructions = etInstructions.text.toString().trim()

                val deliveryOption = when (rgDeliveryOption.checkedRadioButtonId) {
                    R.id.rbSelfPickup -> "Self Pickup"
                    R.id.rbAutoPickup -> "Auto Pickup"
                    else -> "Self Pickup"
                }

                findViewById<TextView>(R.id.tvItemName).text = "Item: $newName"
                findViewById<TextView>(R.id.tvItemWeight).text = "Weight: $kg kg $gram g"
                findViewById<TextView>(R.id.tvItemInstructions).text = "Instructions: $instructions"
                findViewById<TextView>(R.id.tvDeliveryOption).text = "Delivery Option: $deliveryOption"

                updateItemDetailsInFirestore(newName, kg, gram, instructions, deliveryOption)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateItemDetailsInFirestore(
        name: String,
        kg: Int,
        gram: Int,
        instructions: String,
        deliveryOption: String
    ) {
        val phoneNumber = sharedPref.getString("PHONE_NUMBER", null) ?: return

        val totalWeight = (kg * 1000) + gram
        val price = when (deliveryOption) {
            "Self Pickup" -> 750
            "Auto Pickup" -> 1500
            else -> 750
        }

        val updates = hashMapOf<String, Any>(
            "itemDetails.itemName" to name,
            "itemDetails.weightKg" to kg,
            "itemDetails.weightGram" to gram,
            "itemDetails.totalWeight" to totalWeight,
            "itemDetails.instructions" to instructions,
            "deliveryOptionPrice" to price
        )

        db.collection("Sender").document(phoneNumber)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Item details updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update item details", Toast.LENGTH_SHORT).show()
                Log.e("SenderProfile", "Error updating item details", e)
            }
    }
}
