package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class pnrCheck : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pnr_check)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val etPnr = findViewById<EditText>(R.id.etPnr)
        val etSurname = findViewById<EditText>(R.id.etSurname)

        btnVerify.setOnClickListener {
            val pnr = etPnr.text.toString().trim()
            val surname = etSurname.text.toString().trim()

            if (pnr.isEmpty() || surname.isEmpty()) {
                Toast.makeText(this, "Please enter both PNR and Surname", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, webviewPnr::class.java).apply {
                    putExtra("PNR", pnr)
                    putExtra("SURNAME", surname)
                }
                startActivity(intent)
            }
        }
    }
}