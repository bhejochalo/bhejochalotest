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
            val newIntent = Intent(this, ItemDetailsActivity::class.java).apply {
                putExtra("FROM_HOUSE_NUMBER", intent.getStringExtra("FROM_HOUSE_NUMBER"))
                putExtra("FROM_STREET", intent.getStringExtra("FROM_STREET"))
                putExtra("FROM_ADDRESS", intent.getStringExtra("FROM_ADDRESS"))
                putExtra("TO_ADDRESS", intent.getStringExtra("TO_ADDRESS"))
                putExtra("FROM_AREA", intent.getStringExtra("FROM_AREA"))
                putExtra("FROM_POSTAL_CODE", intent.getStringExtra("FROM_POSTAL_CODE"))
                putExtra("FROM_CITY", intent.getStringExtra("FROM_CITY"))
                putExtra("FROM_STATE", intent.getStringExtra("FROM_STATE"))
                putExtra("TO_HOUSE_NUMBER", intent.getStringExtra("TO_HOUSE_NUMBER"))
                putExtra("TO_STREET", intent.getStringExtra("TO_STREET"))
                putExtra("TO_AREA", intent.getStringExtra("TO_AREA"))
                putExtra("TO_POSTAL_CODE", intent.getStringExtra("TO_POSTAL_CODE"))
                putExtra("TO_CITY", intent.getStringExtra("TO_CITY"))
                putExtra("TO_STATE", intent.getStringExtra("TO_STATE"))
                putExtra("FROM_ADDRESS", fromAddress)
                putExtra("TO_ADDRESS", toAddress)
                putExtra("PHONE_NUMBER", phoneNumber)
            }
            startActivity(newIntent)
        }


        findViewById<Button>(R.id.receiverButton).setOnClickListener {
            val newIntent = Intent(this, pnrCheck::class.java).apply {
                putExtra("FROM_HOUSE_NUMBER", intent.getStringExtra("FROM_HOUSE_NUMBER"))
                putExtra("FROM_STREET", intent.getStringExtra("FROM_STREET"))
                putExtra("FROM_ADDRESS", intent.getStringExtra("FROM_ADDRESS"))
                putExtra("TO_ADDRESS", intent.getStringExtra("TO_ADDRESS"))
                putExtra("FROM_AREA", intent.getStringExtra("FROM_AREA"))
                putExtra("FROM_POSTAL_CODE", intent.getStringExtra("FROM_POSTAL_CODE"))
                putExtra("FROM_CITY", intent.getStringExtra("FROM_CITY"))
                putExtra("FROM_STATE", intent.getStringExtra("FROM_STATE"))
                putExtra("TO_HOUSE_NUMBER", intent.getStringExtra("TO_HOUSE_NUMBER"))
                putExtra("TO_STREET", intent.getStringExtra("TO_STREET"))
                putExtra("TO_AREA", intent.getStringExtra("TO_AREA"))
                putExtra("TO_POSTAL_CODE", intent.getStringExtra("TO_POSTAL_CODE"))
                putExtra("TO_CITY", intent.getStringExtra("TO_CITY"))
                putExtra("TO_STATE", intent.getStringExtra("TO_STATE"))
                putExtra("FROM_ADDRESS", fromAddress)
                putExtra("TO_ADDRESS", toAddress)
                putExtra("PHONE_NUMBER", phoneNumber)
            }
            startActivity(newIntent)
        }
    }
}