package com.example.calls.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.calls.R
import com.example.calls.adapters.CallsAdapter
import com.example.calls.models.Calls
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReadActivity : AppCompatActivity() {

    lateinit var readProgressLayout: RelativeLayout
    lateinit var readProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)

        readProgressLayout = findViewById(R.id.readProgressLayout)
        readProgressBar = findViewById(R.id.readProgressBar)
        recyclerView = findViewById(R.id.recyclerView)

        readProgressLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        recyclerView.layoutManager = LinearLayoutManager(this)

        val calls = arrayListOf<Calls>()

        val queue = Volley.newRequestQueue(this)
        val url =
            "https://script.google.com/macros/s/AKfycbyyorwcDipj7qLs72YLZnpsRpinfSCJlpHAQUKlLLuHM_ChY71sVjsYLsq86ZDsJM3P/exec"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val data = response.getJSONArray("data")
                for (i in 0 until data.length()) {
                    val callJsonObject = data.getJSONObject(i)
                    val callObject = Calls(
                        formatDate(callJsonObject.getString("Date")),
                        callJsonObject.getString("Number"),
                        callJsonObject.getString("Name"),
                        callJsonObject.getString("Type"),
                        callJsonObject.getString("Uploader")
                    )
                    calls.add(callObject)
                }

                recyclerView.adapter = CallsAdapter(calls)

                readProgressLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                readProgressLayout.visibility = View.GONE
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return super.getHeaders()
            }
            override fun getPriority(): Priority {
                return Priority.HIGH
            }
        }

        queue.add(jsonObjectRequest)
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

            val date = inputFormat.parse(isoDate)
            if (date != null) outputFormat.format(date) else isoDate
        } catch (e: Exception) {
            isoDate
        }
    }
}