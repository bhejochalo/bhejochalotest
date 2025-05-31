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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_dashboard)

        // Initialize RecyclerView // Traveler adapter
        recyclerView = findViewById(R.id.travelersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TravelerAdapter(travelersList)
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
                  //  val from = doc.get("fromAddress") as? Map<*, *>
                 //   val to = doc.get("toAddress") as? Map<*, *>

                 //   val travelerFromCity = (from?.get("city") as? String)?.trim()?.lowercase()
                //    val travelerToCity = (to?.get("city") as? String)?.trim()?.lowercase()

               //     val isExactMatch = travelerFromCity == senderFromCity && travelerToCity == senderToCity

                   // Log.d("TravelerMatchLog", "DocID=${doc.id}, Match=$isExactMatch | " +
                       //     "Traveler From=$travelerFromCity, To=$travelerToCity | " +
                        //    "Sender From=$senderFromCity, To=$senderToCity")

                    if (doc != null) {
                        val name = doc.getString("lastName") ?: continue
                        val airline = doc.getString("airline") ?: continue
                       // val destination = to?.get("fullAddress") as? String ?: continue
                        val pnr = doc.getString("pnr") ?: continue
                        val phNumber = doc.getString("phoneNumber") ?: continue
                     //   Log.d("TravelerAddLog", "Adding traveler: $name, PNR: $pnr, To: $destination")
                        travelersList.add(
                            Traveler(
                                name = name,
                                airline = airline,
                               // destination = destination,
                                pnr = pnr,
                                bookingStatus = "available" ,
                                documentSnapshot = doc,
                                phoneNumber = phNumber
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
                                val name = doc.getString("lastName") ?: continue
                                val airline = doc.getString("airline") ?: continue
                                val pnr = doc.getString("pnr") ?: continue
                                val phNumber = doc.getString("phoneNumber") ?: continue

                                travelersList.add(
                                    Traveler(
                                        name = name,
                                        airline = airline,
                                        pnr = pnr,
                                        bookingStatus = "available",
                                        documentSnapshot = doc,
                                        phoneNumber = phNumber
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