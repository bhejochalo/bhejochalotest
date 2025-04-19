package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SenderDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TravelerAdapter
    private val db = FirebaseFirestore.getInstance()
    private var lastVisibleDocument: DocumentSnapshot? = null
    private val travelersList = mutableListOf<Traveler>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_dashboard)

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.travelersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TravelerAdapter(travelersList)
        recyclerView.adapter = adapter

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
        val query = db.collection("users")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)

        query.get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                lastVisibleDocument = querySnapshot.documents[querySnapshot.size() - 1]
                travelersList.clear()
                processDocuments(querySnapshot.documents) // Pass the documents list
                adapter.notifyDataSetChanged()
            }
        }.addOnFailureListener { e ->
            Log.e("FirestoreError", "Error loading travelers: ${e.message}")
        }
    }

    private fun loadMoreTravelers() {
        lastVisibleDocument?.let { lastDoc ->
            val query = db.collection("users")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .startAfter(lastDoc)
                .limit(10)

            query.get().addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    lastVisibleDocument = querySnapshot.documents[querySnapshot.size() - 1]
                    processDocuments(querySnapshot.documents) // Pass the documents list
                    adapter.notifyDataSetChanged()
                }
            }.addOnFailureListener { e ->
                Log.e("FirestoreError", "Error loading more travelers: ${e.message}")
            }
        }
    }

    private fun processDocuments(documents: List<DocumentSnapshot>) {
        for (doc in documents) {
            val name = doc.getString("lastName") ?: continue
            val airline = doc.getString("airline") ?: continue
            val destination = doc.getString("toAddress.fullAddress") ?: continue
            val pnr = doc.getString("pnr") ?: continue

            travelersList.add(
                Traveler(
                    name = name,
                    airline = airline,
                    destination = destination,
                    pnr = pnr,
                    bookingStatus = "available"
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear AddressHolder when leaving this activity if needed
        // AddressHolder.clear()
    }
}