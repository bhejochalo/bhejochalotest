package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class UserVerificationActivity : AppCompatActivity() {

    private lateinit var etPanNumber: EditText
    private lateinit var btnVerify: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPanName: TextView
    private lateinit var tvSuccessIcon: TextView
    private lateinit var btnNext: Button

    private val client = OkHttpClient()
    private val TAG = "PAN_VERIFICATION"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_verification)

        Log.d(TAG, "Activity created")

        // Initialize views
        etPanNumber = findViewById(R.id.etPanNumber)
        btnVerify = findViewById(R.id.btnVerify)
        progressBar = findViewById(R.id.progressBar)
        tvPanName = findViewById(R.id.tvPanName)
        tvSuccessIcon = findViewById(R.id.tvSuccessIcon)
        btnNext = findViewById(R.id.btnNext)

        btnVerify.setOnClickListener {
            val pan = etPanNumber.text.toString().trim()
            Log.d(TAG, "Verify button clicked with PAN: $pan")

            if (pan.isEmpty() || pan.length != 10) {
                etPanNumber.error = "Enter valid 10-character PAN"
                Log.e(TAG, "Invalid PAN format")
            } else {
                verifyPAN(pan)
            }
        }

        btnNext.setOnClickListener {
            val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
            val panNumber = etPanNumber.text.toString().trim()
            Log.d(TAG, "Next button clicked. Phone: $phoneNumber, PAN: $panNumber")

            val sharedPref = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("PAN_NUMBER", panNumber)
                putBoolean("IS_VERIFIED", true)
                apply()
            }
            Log.d(TAG, "Saved PAN and verification status to SharedPreferences")

            val intent = Intent(this, AutoCompleteAddressActivity::class.java)
            intent.putExtra("PHONE_NUMBER", phoneNumber)
            startActivity(intent)
            Log.d(TAG, "Navigating to AutoCompleteAddressActivity")
        }
    }

    private fun verifyPAN(pan: String) {
        Log.d(TAG, "Starting PAN verification for: $pan")

        // Reset UI
        progressBar.visibility = View.VISIBLE
        tvPanName.visibility = View.GONE
        tvSuccessIcon.visibility = View.GONE
        btnNext.visibility = View.GONE
        btnNext.isEnabled = false

        val json = JSONObject().apply {
            put("pan", pan)
            put("consent", "Y")
            put("reason", "User verification for logistics app")
        }
        Log.d(TAG, "Request JSON: ${json.toString()}")

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://dg-sandbox.setu.co/api/verify/pan")
            .addHeader("x-client-id", "292c6e76-dabf-49c4-8e48-90fba2916673")
            .addHeader("x-client-secret", "7IZMe9zvoBBuBukLiCP7n4KLwSOy11oP")
            .addHeader("x-product-instance-id", "439244ff-114e-41a8-ae74-a783f160622d")
            .post(requestBody)
            .build()

        Log.d(TAG, "Request prepared. Headers: ${request.headers}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API call failed", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@UserVerificationActivity, "API call failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseCode = response.code
                Log.d(TAG, "Response received. Code: $responseCode")

                val resStr = response.body?.string()
                Log.d(TAG, "Raw response: $resStr")

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (resStr.isNullOrEmpty()) {
                        Log.e(TAG, "Empty response body")
                        Toast.makeText(this@UserVerificationActivity, "Empty response from server", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    try {
                        val res = JSONObject(resStr)
                        val verification = res.optString("verification", "")
                        Log.d(TAG, "Verification status: $verification")

                        if (verification == "SUCCESS") {
                            val data = res.getJSONObject("data")
                            val fullName = data.optString("full_name", "Verified")
                            Log.d(TAG, "Verification successful. Name: $fullName")

                            tvPanName.text = "Verified Name: $fullName"
                            tvPanName.visibility = View.VISIBLE
                            tvSuccessIcon.visibility = View.VISIBLE
                            btnNext.visibility = View.VISIBLE
                            btnNext.isEnabled = true
                        } else {
                            val message = res.optString("message", "Verification failed")
                            Log.e(TAG, "Verification failed: $message")
                            Toast.makeText(this@UserVerificationActivity, message, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response", e)
                        Toast.makeText(this@UserVerificationActivity, "Error parsing response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}