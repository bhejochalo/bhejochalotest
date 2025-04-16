package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ItemDetailsActivity : AppCompatActivity() {

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

        itemNameEditText = findViewById(R.id.itemNameEditText)
        weightSeekBar = findViewById(R.id.itemWeightSeekBar)
        weightValueText = findViewById(R.id.weightValueText)
        instructionsEditText = findViewById(R.id.instructionsEditText)
        nextButton = findViewById(R.id.nextButton)

        phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""

        weightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                itemWeight = progress
                weightValueText.text = "Weight: $itemWeight kg"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        nextButton.setOnClickListener {
            saveItemToFirestore()
        }
    }

    private fun saveItemToFirestore() {
        val itemName = itemNameEditText.text.toString().trim()
        val instructions = instructionsEditText.text.toString().trim()

        if (itemName.isEmpty()) {
            itemNameEditText.error = "Item name required"
            return
        }

        val itemData = hashMapOf(
            "itemName" to itemName,
            "itemWeight" to itemWeight,
            "instructions" to instructions
        )

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(phoneNumber)
            .collection("Sender")
            .add(itemData)
            .addOnSuccessListener {
                Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SenderDashboardActivity::class.java)
                intent.putExtra("PHONE_NUMBER", phoneNumber)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
