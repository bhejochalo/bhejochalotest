package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class SenderProfile : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPref: SharedPreferences
    private var uniqueKey: String? = null
    private var senderDoc: DocumentSnapshot? = null
    private var travelerDoc: DocumentSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_profile)

        db = FirebaseFirestore.getInstance()
        sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        //uniqueKey = sharedPref.getString("uniqueKey", null) ?: intent.getStringExtra("uniqueKey")
        uniqueKey = "asdf"

        setupTabSwitching()
        setupEditButtons()
        setupEditItemButton()

        loadSenderData {
            loadAddressData()
            loadItemDetails()

            // Check traveler acceptance and update UI/editing permissions
            refreshTravelerDoc { tDoc ->
                val travelerStatus = tDoc?.getString("status") ?: ""
                if (travelerStatus == "Request Accepted By Traveler") {
                    // show contact + location and disable edits
                    showTravelerContactAndLocation(tDoc)
                    setSenderEditingEnabled(false)
                    showTravelerContactAndLocation(tDoc)
                } else {
                    // hide sensitive details & enable edits
                    setSenderEditingEnabled(true)
                    hideTravelerContactAndLocation()
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
            val status = travelerDoc.getString("status") ?: ""
            runOnUiThread {
                findViewById<TextView>(R.id.subStatus).text = status
                val flightNumber =
                    travelerDoc.getString("flightNumber") ?: travelerDoc.getString("FlightNumber")
                    ?: "N/A"
                val trackingUrl = "https://www.flightaware.com/live/flight/$flightNumber"
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

    /**
     * Read current traveler doc from Firestore (by uniqueKey). Callback returns DocumentSnapshot? (null if not found)
     */
    private fun refreshTravelerDoc(callback: (DocumentSnapshot?) -> Unit) {
        val key = uniqueKey ?: sharedPref.getString("uniqueKey", null)
        if (key.isNullOrEmpty()) {
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
                    callback(doc)
                    val travelerStatus = doc.getString("status") ?: ""
                    if (travelerStatus == "Request Accepted By Traveler") {
                        setSenderEditingEnabled(false)
                    } else {
                        setSenderEditingEnabled(true)
                    }

                } else {
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
        }
    }

    private fun verifyFirstMileOtp(enteredOtp: String) {
        val doc = travelerDoc
        if (doc == null) {
            Toast.makeText(this, "No traveler loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val realOtp = doc.getString("FirstMileOTP") ?: ""
        val docRef = doc.reference

        if (enteredOtp == realOtp && realOtp.isNotEmpty()) {
            docRef.update("FirstMileStatus", "Completed")
                .addOnSuccessListener {
                    Toast.makeText(this, "Pickup OTP Verified. First mile completed.", Toast.LENGTH_SHORT).show()
                    refreshTravelerDoc { refreshed ->
                        refreshed?.let {
                            updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "Completed")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("SenderProfile", "Error updating first mile status", e)
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
        }
    }

    // Optional helper to initiate first mile (generates OTP & sets In Progress) — not auto-called by Refresh
    private fun initiateFirstMile() {
        val doc = travelerDoc ?: return
        val firstStatus = doc.getString("FirstMileStatus") ?: "Not Started"
        if (firstStatus == "Not Started") {
            val generated = generateOtp()
            doc.reference.update(mapOf(
                "FirstMileOTP" to generated,
                "FirstMileStatus" to "In Progress"
            )).addOnSuccessListener {
                Toast.makeText(this, "First mile started. OTP generated.", Toast.LENGTH_SHORT).show()
                refreshTravelerDoc { refreshed ->
                    refreshed?.let {
                        updateFirstMileSectionUI(it.getString("FirstMileOTP") ?: "", it.getString("FirstMileStatus") ?: "In Progress")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("SenderProfile", "Error initiating first mile", e)
            }
        } else {
            Toast.makeText(this, "First mile already in progress or completed", Toast.LENGTH_SHORT).show()
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

            when {
                now.before(dep) -> {
                    secondTv.text = "⏳ 2nd Stage - Not Started"
                    transitStatusTv.text = "Flight scheduled to depart at ${formatLocal(dep)}"
                }
                now.after(dep) && now.before(arr) -> {
                    secondTv.text = "⏳ 2nd Stage - In Transit"
                    transitStatusTv.text = "Flight is airborne"
                    if (doc.getString("SecondMileStatus") != "In Progress") {
                        doc.reference.update("SecondMileStatus", "In Progress")
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
                    if (doc.getString("SecondMileStatus") != "Completed") {
                        doc.reference.update("SecondMileStatus", "Completed")
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
        if (doc == null) return

        // Get phone number (fall back to phoneNumber / phone)
        val phone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: "N/A"

        // Try nested fromAddress or toAddress coordinates
        val fromAddress = doc.get("fromAddress") as? Map<*, *>
        val toAddress = doc.get("toAddress") as? Map<*, *>

        val lat = (fromAddress?.get("latitude") as? Number)?.toDouble()
            ?: (toAddress?.get("latitude") as? Number)?.toDouble()
        val lng = (fromAddress?.get("longitude") as? Number)?.toDouble()
            ?: (toAddress?.get("longitude") as? Number)?.toDouble()

        val tvPhone = findViewById<TextView>(R.id.tvTravelerPhone)
        val tvLocation = findViewById<TextView>(R.id.tvTravelerLocation)

        runOnUiThread {
            tvPhone?.text = if (phone.isNotBlank()) phone else "N/A"
            tvLocation?.text = if (lat != null && lng != null) {
                "Lat: %.6f, Lng: %.6f".format(lat, lng)
            } else {
                // fallback to any textual fullAddress if coords missing
                val fa = (fromAddress?.get("fullAddress") as? String)
                    ?: (toAddress?.get("fullAddress") as? String) ?: "Location not available"
                fa
            }
            // Make visible (in case you hide it by default)
            tvPhone?.visibility = View.VISIBLE
            tvLocation?.visibility = View.VISIBLE
        }
    }
    private fun hideTravelerContactAndLocation() {
        runOnUiThread {
            findViewById<TextView>(R.id.tvTravelerPhone)?.text = "N/A"
            findViewById<TextView>(R.id.tvTravelerLocation)?.text = "Lat: N/A, Lng: N/A"
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
            val fromAddress = travelerDoc.get("fromAddress") as? Map<String, Any>
            val toAddress = travelerDoc.get("toAddress") as? Map<String, Any>

            val fromCity = fromAddress?.get("city") as? String ?: "N/A"
            val toCity = toAddress?.get("city") as? String ?: "N/A"

            val airline = travelerDoc.getString("airline") ?: "N/A"
            val flightNumber = travelerDoc.getString("flightNumber") ?: travelerDoc.getString("FlightNumber") ?: "N/A"

            val departureTimeStr = travelerDoc.getString("departureTime") ?: "N/A"
            val formattedDepartureTime = formatDateTime(departureTimeStr)
            val formattedArrivalTime = calculateArrivalTime(departureTimeStr)

            val status = travelerDoc.getString("status") ?: "N/A"

            runOnUiThread {
                findViewById<TextView>(R.id.tvFromCity).text = fromCity
                findViewById<TextView>(R.id.tvToCity).text = toCity
                findViewById<TextView>(R.id.tvFlightStatus).text = "Airline: $airline"
                findViewById<TextView>(R.id.tvFromTime).text = "Departure: $formattedDepartureTime"
                findViewById<TextView>(R.id.tvToTime).text = "Arrival: $formattedArrivalTime"
                findViewById<TextView>(R.id.tvFlightNumber).text = "Flight: $flightNumber"

                val statusTextView = findViewById<TextView>(R.id.subStatus)
                statusTextView.text = "Flight Status: $status"

                when (status.lowercase()) {
                    "scheduled", "ontime", "request accepted by traveler" -> statusTextView.setTextColor(Color.GREEN)
                    "delayed", "in progress" -> statusTextView.setTextColor(Color.YELLOW)
                    "cancelled", "rejected" -> statusTextView.setTextColor(Color.RED)
                    else -> statusTextView.setTextColor(Color.GRAY)
                }

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

    private fun updateAddressInFirestore(addressType: String, newAddress: String) {
        val userId = sharedPref.getString("PHONE_NUMBER", null) ?: return
        val updates = hashMapOf<String, Any>(
            "$addressType.fullAddress" to newAddress
        )

        db.collection("Sender")
            .whereEqualTo("phoneNumber", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    document.reference.update(updates)
                        .addOnSuccessListener {
                            Log.d("SenderProfile", "Address updated successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("SenderProfile", "Error updating address", e)
                        }
                }
            }
    }
}
