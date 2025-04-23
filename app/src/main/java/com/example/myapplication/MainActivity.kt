package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Date
import kotlin.math.abs
import com.google.android.libraries.places.api.Places


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

      //  Places.initialize(applicationContext, "AIzaSyCDmMtuO7w9uBecNRCtf5vndLUAsZVPUHI")
        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find views by their IDs
        val phoneNumberEditText = findViewById<EditText>(R.id.phoneNumber)
        val verifyButton = findViewById<Button>(R.id.button)

        // Set a click listener for the button
        verifyButton.setOnClickListener {
            val phoneNumber = phoneNumberEditText.text.toString().trim() // Get the input text
            if (phoneNumber.isNotEmpty()) {

                val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("PHONE_NUMBER", phoneNumber)
                    apply()
                }

                println("Phone number entered: $phoneNumber")
                Toast.makeText(this, "Sending OTP to: $phoneNumber", Toast.LENGTH_SHORT).show()
                sendOtpToPhoneNumber(phoneNumber)
            } else {
                println("No phone number entered")
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendOtpToPhoneNumber(phoneNumber: String) {
        val accountSID = "AC678b26cf91cfc4b1c680daf3cc1db763" // Change the Twilio Creds here only
        val authToken = "1ab8b1ed63716c01977bfac0ceaa5cb1"
        val fromPhoneNumber = "+18313184672"

        val otp = abs((phoneNumber.hashCode() + Date().time).toInt()).toString().padStart(4, '0')
            .substring(0, 4)
        val endPoint = "https://api.twilio.com/2010-04-01/Accounts/$accountSID/Messages.json"
        val messageBody = "Your OTP for STD is $otp"

        // val credential = Base64.getEncoder().encodeToString("$accountSID:$authToken".toByteArray())
        val credential =
            Base64.encodeToString("$accountSID:$authToken".toByteArray(), Base64.NO_WRAP)

        val client = OkHttpClient()
        val body = FormBody.Builder()
            .add("Body", messageBody)
            .add("From", fromPhoneNumber)
            .add("To", phoneNumber)
            .build()

        val request = Request.Builder()
            .url(endPoint)
            .post(body)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Authorization", "Basic $credential")
            .build()

        Toast.makeText(this@MainActivity, "OTP sent successfully!" + otp, Toast.LENGTH_SHORT).show()
        val intent = Intent(this@MainActivity, OTP_Verification::class.java)
        intent.putExtra("OTP", otp)
        intent.putExtra("PhoneNumber", phoneNumber)
        startActivity(intent)
    }

        /*client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to send OTP", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (true) { // response.isSuccessful
                        println("Response: ${response.body?.string()}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "OTP sent successfully!" + otp, Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@MainActivity, OTP_Verification::class.java)
                            intent.putExtra("OTP", otp)
                            intent.putExtra("PhoneNumber", phoneNumber)
                            startActivity(intent)
                        }
                    } else {
                        println("Error:===>  ${response.body?.string()}")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Failed to send OTP 97", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "An error occurred", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }*/
}
