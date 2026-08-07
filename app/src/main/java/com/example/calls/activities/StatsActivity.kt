package com.example.calls.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Request.Priority
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.calls.R
import com.example.calls.adapters.UploaderStatsAdapter
import com.example.calls.models.UploaderStat
import com.example.calls.sync.VolleySingleton

class StatsActivity : AppCompatActivity() {

    lateinit var readProgressLayout: RelativeLayout
    lateinit var readProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_stats)

        readProgressLayout = findViewById(R.id.readProgressLayoutStats)
        readProgressBar = findViewById(R.id.readProgressBarStats)
        recyclerView = findViewById(R.id.recyclerViewStats)

        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchStats()
    }

    private fun fetchStats() {
        readProgressLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        val queue = VolleySingleton.getInstance(this)
        val url = "${getString(R.string.script_url)}?action=uploaderStats"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val statsArray = response.getJSONArray("stats")
                val stats = mutableListOf<UploaderStat>()

                for (i in 0 until statsArray.length()) {
                    val obj = statsArray.getJSONObject(i)
                    stats.add(
                        UploaderStat(
                            uploader = obj.getString("Uploader"),
                            total = obj.getInt("Total"),
                            lastUpload = obj.optString("LastUpload", "")
                        )
                    )
                }

                readProgressLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = UploaderStatsAdapter(stats)
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                readProgressLayout.visibility = View.GONE
            }
        ) {
            override fun getPriority(): Priority = Priority.HIGH
        }
        jsonObjectRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
            8000, 1, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(jsonObjectRequest)
    }
}