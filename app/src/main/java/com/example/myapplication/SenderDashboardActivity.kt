package com.example.myapplication
//New Code
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SenderDashboardActivity : AppCompatActivity() {
    private var senderPhoneNumber: String? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TravelerAdapter
    private val db = FirebaseFirestore.getInstance()
    private var lastVisibleDocument: DocumentSnapshot? = null
    private val travelersList = mutableListOf<Traveler>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val senderStreet = AddressHolder.fromStreet?.trim()?.lowercase()
        val senderArea = AddressHolder.fromArea?.trim()?.lowercase()
        val senderPostalCode = AddressHolder.fromPostalCode?.trim()
        val selectedPrice = intent.getIntExtra("SELECTED_PRICE", 1500) // Default to 1500 if not found
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_dashboard)

        // Initialize RecyclerView // Traveler adapter
        recyclerView = findViewById(R.id.travelersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TravelerAdapter(travelersList, selectedPrice)
        recyclerView.adapter = adapter

        senderPhoneNumber = intent.getStringExtra("PHONE_NUMBER")?.also {
            Log.d("SenderReceiver", "Received phone: $it")
        } ?: run {
            Toast.makeText(this, "Phone number not received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }


        // Load initial data
        loadTravelers()

        // Infinite scrolling
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) { // Reached bottom
                    loadMoreTravelers()
                }
            }
        })
    }
    private fun loadTravelers() {
        val senderFromLat = AddressHolder.fromLatitude ?: return
        val senderFromLng = AddressHolder.fromLongitude ?: return

        // Add validation for sender coordinates
        if (senderFromLat == 0.0 || senderFromLng == 0.0) {
            Log.e("InvalidCoords", "Sender coordinates are (0,0)")
            return
        }
        Log.d("SenderCoords", "Sender Location: (${senderFromLat}, ${senderFromLng})")
       // val senderFromCity = AddressHolder.fromCity?.trim()?.lowercase()
       // val senderToCity = AddressHolder.toCity?.trim()?.lowercase()
        val senderToPincode = AddressHolder.toPostalCode?.trim()?.lowercase()
        val senderFromPincode = AddressHolder.fromPostalCode?.trim()?.lowercase()

        if (senderToPincode == null || senderFromPincode == null) {
            Log.e("LoadTravelers", "Sender fromCity or toCity is null")
            return
        }

        db.collection("traveler")
            .whereEqualTo("fromAddress.postalCode", senderFromPincode)
            .whereEqualTo("toAddress.postalCode", senderToPincode)
            .limit(100)
            .get()
            .addOnSuccessListener { querySnapshot ->
                travelersList.clear()
                lastVisibleDocument = querySnapshot.documents.lastOrNull()

                for (doc in querySnapshot.documents) {
                    val travelerLat = doc.getDouble("fromAddress.latitude") ?: 0.0
                    val travelerLng = doc.getDouble("fromAddress.longitude") ?: 0.0
                    Log.d("TravelerCoords", "Traveler ${doc.id}: (${travelerLat}, ${travelerLng})")
                    val distance = calculateDistance(
                        senderFromLat, senderFromLng,
                        travelerLat, travelerLng
                    )
                  //  val from = doc.get("fromAddress") as? Map<*, *>
                 //   val to = doc.get("toAddress") as? Map<*, *>

                 //   val travelerFromCity = (from?.get("city") as? String)?.trim()?.lowercase()
                //    val travelerToCity = (to?.get("city") as? String)?.trim()?.lowercase()

               //     val isExactMatch = travelerFromCity == senderFromCity && travelerToCity == senderToCity

                   // Log.d("TravelerMatchLog", "DocID=${doc.id}, Match=$isExactMatch | " +
                       //     "Traveler From=$travelerFromCity, To=$travelerToCity | " +
                        //    "Sender From=$senderFromCity, To=$senderToCity")

                    if (doc != null) {
                        val name = doc.getString("lastName") ?: ""
                        val airline = doc.getString("airline") ?: "Unknown Airline"
                        val pnr = doc.getString("pnr") ?: "N/A"
                        val phNumber = doc.getString("phoneNumber") ?: ""
                        val leavingTime = doc.getString("leavingTime") ?: "Not specified"
                        val weightUpto = try {
                            doc.getLong("weightUpto")?.toInt() ?: 0
                        } catch (e: Exception) {
                            try {
                                doc.getString("weightUpto")?.toIntOrNull() ?: 0
                            } catch (e: Exception) {
                                0
                            }
                        }

                        val flightNumber = doc.getString("flightNumber") ?: "N/A"
                        val arrivalTime = doc.getString("arrivalTime") ?: "Not specified"
                        val destination = doc.getString("destination") ?: "Not specified"
                        val flightDuration = doc.getString("flightDuration") ?: "Not specified"
                        val price = doc.getLong("price")?.toInt() ?: 0

                        val notAcceptedItems = try {
                            doc.get("notAcceptedItems") as? List<String> ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }

                        travelersList.add(
                            Traveler(
                                name = name,
                                airline = airline,
                                destination = destination,
                                pnr = pnr,
                                bookingStatus = "available",
                                documentSnapshot = doc,
                                phoneNumber = phNumber,
                                leavingTime = leavingTime,
                                weightUpto = weightUpto,
                                flightNumber = flightNumber,
                                arrivalTime = arrivalTime,
                                flightDuration = flightDuration,
                                price = price,
                                notAcceptedItems = notAcceptedItems,
                                distance = distance
                            )
                        )
                    }
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Error loading travelers: ${e.message}")
            }
    }
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371 // Earth's radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
    private fun loadMoreTravelers() {
        val senderToPincode = AddressHolder.toPostalCode?.trim()?.lowercase()
        val senderFromPincode = AddressHolder.fromPostalCode?.trim()?.lowercase()

        if (senderToPincode == null || senderFromPincode == null) {
            Log.e("LoadMoreTravelers", "Sender fromCity or toCity is null")
            return
        }

        lastVisibleDocument?.let { lastDoc ->
            db.collection("traveler")
                .whereEqualTo("fromAddress.postalCode", senderFromPincode)
                .whereEqualTo("toAddress.postalCode", senderToPincode)
                .orderBy("timestamp", Query.Direction.DESCENDING) // Keep your existing ordering
                .startAfter(lastDoc)
                .limit(10)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        lastVisibleDocument = querySnapshot.documents.lastOrNull()

                        for (doc in querySnapshot.documents) {
                            if (doc != null) {
                                val name = doc.getString("lastName") ?: ""
                                val airline = doc.getString("airline") ?: "Unknown Airline"
                                val pnr = doc.getString("pnr") ?: "N/A"
                                val phNumber = doc.getString("phoneNumber") ?: ""
                                val leavingTime = doc.getString("leavingTime") ?: "Not specified"
                                val weightUpto = try {
                                    doc.getLong("weightUpto")?.toInt() ?: 0
                                } catch (e: Exception) {
                                    try {
                                        doc.getString("weightUpto")?.toIntOrNull() ?: 0
                                    } catch (e: Exception) {
                                        0
                                    }
                                }

                                val flightNumber = doc.getString("flightNumber") ?: "N/A"
                                val arrivalTime = doc.getString("arrivalTime") ?: "Not specified"
                                val destination = doc.getString("destination") ?: "Not specified"
                                val flightDuration = doc.getString("flightDuration") ?: "Not specified"
                                val price = doc.getLong("price")?.toInt() ?: 0

                                val notAcceptedItems = try {
                                    doc.get("notAcceptedItems") as? List<String> ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }

                                travelersList.add(
                                    Traveler(
                                        name = name,
                                        airline = airline,
                                        destination = destination,
                                        pnr = pnr,
                                        bookingStatus = "available",
                                        documentSnapshot = doc,
                                        phoneNumber = phNumber,
                                        leavingTime = leavingTime,
                                        weightUpto = weightUpto,
                                        flightNumber = flightNumber,
                                        arrivalTime = arrivalTime,
                                        flightDuration = flightDuration,
                                        price = price,
                                        notAcceptedItems = notAcceptedItems
                                    )
                                )
                            }
                        }

                        adapter.notifyDataSetChanged()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreError", "Error loading more travelers: ${e.message}")
                }
        } ?: run {
            Log.d("LoadMoreTravelers", "No more documents to load")
        }
    }
   /* private fun processDocuments(documents: List<DocumentSnapshot>) {

        for (doc in documents) {
            val name = doc.getString("lastName") ?: continue
            val airline = doc.getString("airline") ?: continue
            val destination = (doc.get("toAddress") as? Map<*, *>)?.get("fullAddress") as? String ?: continue
            val pnr = doc.getString("pnr") ?: continue
            val phNumber = doc.getString("phoneNumber") ?: continue
                travelersList.add(
                    Traveler(
                        name = name,
                        airline = airline,
                        destination = destination,
                        pnr = pnr,
                        bookingStatus = "available",
                        documentSnapshot = doc,
                        phoneNumber = phNumber
                    )
                )
            }
        }*/

    override fun onDestroy() {
        super.onDestroy()
        // Clear AddressHolder when leaving this activity if needed
        // AddressHolder.clear()
    }
}