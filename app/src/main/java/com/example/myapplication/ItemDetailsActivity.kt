package com.example.myapplication

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ItemDetailsActivity : AppCompatActivity() {
    private lateinit var progressDialog: ProgressDialog
    private lateinit var itemNameEditText: EditText
    private lateinit var kgEditText: TextInputEditText
    private lateinit var gramEditText: TextInputEditText
    private lateinit var instructionsEditText: EditText
    private lateinit var nextButton: Button
    private lateinit var uploadImageButton: Button
    private lateinit var itemImageView: ImageView

    private lateinit var firestore: FirebaseFirestore
    private val storageRef = FirebaseStorage.getInstance().reference

    private var itemWeightKg = 0
    private var itemWeightGram = 0
    private lateinit var phoneNumber: String
    private var imageUri: Uri? = null

    private val PICK_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_details)
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Please wait...")
        progressDialog.setCancelable(false)
        firestore = FirebaseFirestore.getInstance()

        val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        phoneNumber = sharedPref.getString("PHONE_NUMBER", "") ?: run {
            Toast.makeText(this, "Phone number not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupWeightInputs()
        setupNextButton()

        uploadImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
    }

    private fun initializeViews() {
        itemNameEditText = findViewById(R.id.itemNameEditText)
        kgEditText = findViewById(R.id.kgEditText)
        gramEditText = findViewById(R.id.gramEditText)
        instructionsEditText = findViewById(R.id.instructionsEditText)
        nextButton = findViewById(R.id.nextButton)
        uploadImageButton = findViewById(R.id.uploadImageButton)
        itemImageView = findViewById(R.id.itemImageView)
    }

    private fun setupWeightInputs() {
        kgEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let {
                    itemWeightKg = if (it.isNotEmpty()) {
                        val kg = it.toIntOrNull() ?: 0
                        if (kg > 15) {
                            kgEditText.error = "Max 15kg allowed"
                            kgEditText.setText("15")
                            15
                        } else {
                            kg
                        }
                    } else {
                        0
                    }
                }
            }
        })

        gramEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let {
                    itemWeightGram = if (it.isNotEmpty()) {
                        val grams = it.toIntOrNull() ?: 0
                        if (grams > 999) {
                            gramEditText.error = "Max 999g allowed"
                            gramEditText.setText("999")
                            999
                        } else {
                            grams
                        }
                    } else {
                        0
                    }
                }
            }
        })
    }

    private fun setupNextButton() {
        nextButton.setOnClickListener {
            if (validateInputs()) {
                if (imageUri == null) {
                    Toast.makeText(this, "Please choose an image", Toast.LENGTH_SHORT).show()
                } else {
                    uploadImageAndSaveData()
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (itemNameEditText.text.toString().trim().isEmpty()) {
            itemNameEditText.error = "Item name required"
            isValid = false
        }

        if (itemWeightKg == 0 && itemWeightGram == 0) {
            Toast.makeText(this, "Please enter item weight", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun uploadImageAndSaveData() {
        progressDialog.show()  // Show loader here

        val imageRef = storageRef.child("item_images/${System.currentTimeMillis()}.png")
        imageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveDataToFirestore(uri.toString())
                }
            }
            .addOnFailureListener {
                progressDialog.dismiss() // Hide loader on failure
                Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
            }
    }


    private fun saveDataToFirestore(imageUrl: String) {
        val itemName = itemNameEditText.text.toString().trim()
        val instructions = instructionsEditText.text.toString().trim()
        val totalWeightGrams = (itemWeightKg * 1000) + itemWeightGram
        val timestamp = System.currentTimeMillis()

        val senderData = hashMapOf(
            "phoneNumber" to phoneNumber,
            "timestamp" to timestamp,
            "status" to "Pending",
            "imageUrl" to imageUrl,
            "fromAddress" to hashMapOf(
                "houseNumber" to AddressHolder.fromHouseNumber,
                "street" to AddressHolder.fromStreet,
                "area" to AddressHolder.fromArea,
                "postalCode" to AddressHolder.fromPostalCode,
                "city" to AddressHolder.fromCity,
                "state" to AddressHolder.fromState,
                "fullAddress" to AddressHolder.fromAddress
            ),
            "toAddress" to hashMapOf(
                "houseNumber" to AddressHolder.toHouseNumber,
                "street" to AddressHolder.toStreet,
                "area" to AddressHolder.toArea,
                "postalCode" to AddressHolder.toPostalCode,
                "city" to AddressHolder.toCity,
                "state" to AddressHolder.toState,
                "fullAddress" to AddressHolder.toAddress
            ),
            "itemDetails" to hashMapOf(
                "itemName" to itemName,
                "weightKg" to itemWeightKg,
                "weightGram" to itemWeightGram,
                "totalWeight" to totalWeightGrams,
                "instructions" to instructions,
                "itemId" to "item_$timestamp"
            )
        )

        firestore.collection("Sender")
            .document(phoneNumber)
            .set(senderData)
            .addOnSuccessListener {
                progressDialog.dismiss() // Hide loader on success
                Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
                navigateToSenderDashboard()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss() // Hide loader on failure
                Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            imageUri = data.data
            itemImageView.setImageURI(imageUri)
        }
    }

    private fun navigateToSenderDashboard() {
        val intent = Intent(this, SenderDashboardActivity::class.java).apply {
            putExtra("PHONE_NUMBER", phoneNumber)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
