package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class pnrCheck : AppCompatActivity() {

    private lateinit var airlineSpinner: Spinner
    private var selectedAirline: String = "SpiceJet" // default selection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pnr_check)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val etPnr = findViewById<EditText>(R.id.etPnr)
        val etSurname = findViewById<EditText>(R.id.etSurname)
        airlineSpinner = findViewById(R.id.airlineSpinner)

        val airlines = arrayOf("SpiceJet", "IndiGo")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, airlines)
        airlineSpinner.adapter = adapter

        airlineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                selectedAirline = airlines[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnVerify.setOnClickListener {
            val pnr = etPnr.text.toString().trim()
            val surname = etSurname.text.toString().trim()

            if (pnr.isEmpty() || surname.isEmpty()) {
                Toast.makeText(this, "Please enter both PNR and Surname", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, webviewPnr::class.java).apply {
                    putExtra("PNR", pnr)
                    putExtra("SURNAME", surname)
                    putExtra("AIRLINE", selectedAirline)
                }
                startActivity(intent)
            }
        }
    }
}
