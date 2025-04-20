package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ItemDetailsActivity : AppCompatActivity() {
//New code
    private lateinit var itemNameEditText: EditText
    private lateinit var weightSeekBar: SeekBar
    private lateinit var weightValueText: TextView
    private lateinit var instructionsEditText: EditText
    private lateinit var nextButton: Button

    private var itemWeight = 0
    private lateinit var phoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_details)

        initializeViews()
        phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""

        setupWeightSeekBar()
        setupNextButton()
    }

    private fun initializeViews() {
        itemNameEditText = findViewById(R.id.itemNameEditText)
        weightSeekBar = findViewById(R.id.itemWeightSeekBar)
        weightValueText = findViewById(R.id.weightValueText)
        instructionsEditText = findViewById(R.id.instructionsEditText)
        nextButton = findViewById(R.id.nextButton)
    }

    private fun setupWeightSeekBar() {
        weightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                itemWeight = progress
                weightValueText.text = "Weight: $itemWeight kg"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupNextButton() {
        nextButton.setOnClickListener {
            if (validateInputs()) {
               // saveItemToFirestore()
                navigateToSenderDashboard()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (itemNameEditText.text.toString().trim().isEmpty()) {
            itemNameEditText.error = "Item name required"
            return false
        }
        return true
    }

    private fun saveItemToFirestore() {
        val itemName = itemNameEditText.text.toString().trim()
        val instructions = instructionsEditText.text.toString().trim()

        val itemData = hashMapOf(
            "itemName" to itemName,
            "itemWeight" to itemWeight,
            "instructions" to instructions,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance().collection("SenderItems")
            .document(phoneNumber)
            .collection("Items")
            .add(itemData)
            .addOnSuccessListener {
                //navigateToSenderDashboard()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToSenderDashboard() {
        val intent = Intent(this, SenderDashboardActivity::class.java).apply {
            putExtra("PHONE_NUMBER", phoneNumber)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}