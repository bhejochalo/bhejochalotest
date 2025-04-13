package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SenderReceiverSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val fromAddress = intent.getStringExtra("FROM_ADDRESS")
        val toAddress = intent.getStringExtra("TO_ADDRESS")
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_receiver_selection)

        findViewById<Button>(R.id.senderButton).setOnClickListener {
            val newIntent = Intent(this, pnrCheck::class.java).apply {
                putExtra("FROM_ADDRESS", fromAddress)
                putExtra("TO_ADDRESS", toAddress)
                putExtra("PHONE_NUMBER", phoneNumber)
            }
            startActivity(newIntent)
        }

        findViewById<Button>(R.id.receiverButton).setOnClickListener {
            val newIntent = Intent(this, pnrCheck::class.java).apply {
                putExtra("FROM_ADDRESS", fromAddress)
                putExtra("TO_ADDRESS", toAddress)
                putExtra("PHONE_NUMBER", phoneNumber)
            }
            startActivity(newIntent)
        }
    }
}