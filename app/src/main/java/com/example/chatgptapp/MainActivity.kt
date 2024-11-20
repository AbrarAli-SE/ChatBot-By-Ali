package com.example.chatgptapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    // Declare UI elements
    lateinit var txtResponse: TextView
    lateinit var idTVQuestion: TextView
    lateinit var etQuestion: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        etQuestion = findViewById(R.id.etQuestion)
        idTVQuestion = findViewById(R.id.idTVQuestion)
        txtResponse = findViewById(R.id.txtResponse)

        // Set up listener for the question input
        etQuestion.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                txtResponse.text = "Please wait..."
                val question = etQuestion.text.toString().trim()

                if (question.isNotEmpty()) {
                    getResponse(question) { response ->
                        runOnUiThread {
                            txtResponse.text = response
                        }
                    }
                } else {
                    Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show()
                }
                return@OnEditorActionListener true
            }
            false
        })
    }

    private fun getResponse(question: String, callback: (String) -> Unit) {
        idTVQuestion.text = question
        etQuestion.setText("")

        val apiKey = "api_key" // Replace with your actual API key
        val url = "https://api.openai.com/v1/chat/completions"

        // Truncate question if too long
        val truncatedQuestion = if (question.length > 80) {
            question.take(80) + "..."
        } else question

        // Build JSON payload using JSONObject
        val jsonObject = JSONObject().apply {
            put("model", "gpt-4o-mini") // Replace with your specific model
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a helpful assistant.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", truncatedQuestion)
                })
            })
            put("max_tokens", 50) // Limit output tokens
            put("temperature", 0)
        }

        val requestBody = jsonObject.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    callback("Error: Failed to connect to the server. Please try again.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    val body = responseBody.string()

                    try {
                        val jsonObject = JSONObject(body)

                        // Check for error in response
                        if (jsonObject.has("error")) {
                            val errorMessage =
                                jsonObject.getJSONObject("error").getString("message")
                            runOnUiThread {
                                callback("Error: $errorMessage")
                            }
                            return
                        }

                        if (jsonObject.has("choices") && jsonObject.getJSONArray("choices")
                                .length() > 0
                        ) {
                            val textResult = jsonObject.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim()

                            runOnUiThread { callback(textResult) }
                        } else {
                            runOnUiThread {
                                callback("Error: Unexpected response format. Please check your API key and payload.")
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            callback("Error: Could not parse the response. Check the API response structure.")
                        }
                    }
                } ?: runOnUiThread {
                    callback("Error: Empty response from server.")
                }
            }
        })
    }
}
