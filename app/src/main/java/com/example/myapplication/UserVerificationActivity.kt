    package com.example.myapplication

    import android.content.Intent
    import android.os.Bundle
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

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_user_verification)
            // Initialize views
            etPanNumber = findViewById(R.id.etPanNumber)
            btnVerify = findViewById(R.id.btnVerify)
            progressBar = findViewById(R.id.progressBar)
            tvPanName = findViewById(R.id.tvPanName)
            tvSuccessIcon = findViewById(R.id.tvSuccessIcon)
            btnNext = findViewById(R.id.btnNext)

            // Click listener for Verify button
            btnVerify.setOnClickListener {
                val pan = etPanNumber.text.toString().trim()
                if (pan.isEmpty() || pan.length != 10) {
                    etPanNumber.error = "Enter valid 10-character PAN"
                } else {
                    verifyPAN(pan)
                }
            }

            // Click listener for Next button
            btnNext.setOnClickListener {
                val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
                val intent = Intent(this, AutoCompleteAddressActivity::class.java)
                intent.putExtra("PHONE_NUMBER", phoneNumber)
                startActivity(intent)
            }
        }

        private fun verifyPAN(pan: String) {
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

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://dg-sandbox.setu.co/api/verify/pan") // 🔁 Replace this with your real API
                .addHeader("x-client-id", "292c6e76-dabf-49c4-8e48-90fba2916673")
                .addHeader("x-client-secret", "7IZMe9zvoBBuBukLiCP7n4KLwSOy11oP")
                .addHeader("x-product-instance-id", "439244ff-114e-41a8-ae74-a783f160622d")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@UserVerificationActivity, "API call failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE

                        val resStr = response.body?.string()
                        if (resStr.isNullOrEmpty()) {
                            Toast.makeText(this@UserVerificationActivity, "Empty response from server", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }

                        try {
                            val res = JSONObject(resStr)
                            val verification = res.optString("verification", "")

                            if (verification == "SUCCESS") {
                                val data = res.getJSONObject("data")
                                val fullName = data.optString("full_name", "Verified")

                                tvPanName.text = "Verified Name: $fullName"
                                tvPanName.visibility = View.VISIBLE
                                tvSuccessIcon.visibility = View.VISIBLE
                                btnNext.visibility = View.VISIBLE
                                btnNext.isEnabled = true
                            } else {
                                val message = res.optString("message", "Verification failed")
                                Toast.makeText(this@UserVerificationActivity, message, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@UserVerificationActivity, "Error parsing response", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            })
        }
    }
