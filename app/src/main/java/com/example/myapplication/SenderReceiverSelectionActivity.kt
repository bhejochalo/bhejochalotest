package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SenderReceiverSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender_receiver_selection)

        findViewById<Button>(R.id.senderButton).setOnClickListener {
            startActivity(Intent(this, pnrCheck::class.java))
        }

        findViewById<Button>(R.id.receiverButton).setOnClickListener {
            startActivity(Intent(this, pnrCheck::class.java))
        }
    }
}