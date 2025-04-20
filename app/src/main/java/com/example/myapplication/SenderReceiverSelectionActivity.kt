package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

//new code
class SenderReceiverSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_receiver_selection)

        // Store basic info in AddressHolder if coming from previous activity
        if (intent.hasExtra("PHONE_NUMBER")) {
            AddressHolder.phoneNumber = intent.getStringExtra("PHONE_NUMBER")
        }

        findViewById<Button>(R.id.senderButton).setOnClickListener {
            // No need to pass extras - next activity will use AddressHolder
            startActivity(Intent(this, ItemDetailsActivity::class.java))
        }

        findViewById<Button>(R.id.receiverButton).setOnClickListener {
            // No need to pass extras - next activity will use AddressHolder
            startActivity(Intent(this, pnrCheck::class.java))
        }
    }
}