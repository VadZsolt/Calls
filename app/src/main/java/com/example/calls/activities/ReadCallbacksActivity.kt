package com.example.calls.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.Request.Priority
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.calls.R
import com.example.calls.adapters.CallsAdapter
import com.example.calls.models.Calls
import java.util.Calendar

class ReadCallbacksActivity : AppCompatActivity() {

    private val DAYS_TO_CHECK = 3

    lateinit var readProgressLayout: RelativeLayout
    lateinit var readProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView
    lateinit var tvEmptyState: TextView
    lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_read_callbacks)

        readProgressLayout = findViewById(R.id.readProgressLayoutCallbacks)
        readProgressBar = findViewById(R.id.readProgressBarCallbacks)
        recyclerView = findViewById(R.id.recyclerViewCallbacks)
        tvEmptyState = findViewById(R.id.tvEmptyStateCallbacks)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        recyclerView.layoutManager = LinearLayoutManager(this)

        readProgressLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        fetchAndComputeCallbacks(showFullOverlay = true)

        swipeRefresh.setOnRefreshListener {
            fetchAndComputeCallbacks(showFullOverlay = false)
        }
    }

    private fun fetchAndComputeCallbacks(showFullOverlay: Boolean) {
        if (showFullOverlay) {
            readProgressLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
        }

        val queue = Volley.newRequestQueue(this)
        val fetchDays = DAYS_TO_CHECK + 2
        val url = "${getString(R.string.script_url)}?action=recent&days=$fetchDays"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val allCalls = arrayListOf<Calls>()
                val data = response.getJSONArray("data")
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)

                    val namesArray = obj.optJSONArray("Names")
                    val names = mutableListOf<String>()
                    if (namesArray != null) {
                        for (n in 0 until namesArray.length()) {
                            names.add(namesArray.getString(n))
                        }
                    }

                    allCalls.add(
                        Calls(
                            obj.getString("Date"),
                            obj.getString("Number"),
                            obj.getString("Name"),
                            obj.getString("Type"),
                            obj.optString("Uploader", ""),
                            Names = names
                        )
                    )
                }

                val callbacksNeeded = computeCallbacksNeeded(allCalls, DAYS_TO_CHECK)

                readProgressLayout.visibility = View.GONE
                swipeRefresh.isRefreshing = false

                if (callbacksNeeded.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.adapter = CallsAdapter(callbacksNeeded)
                    recyclerView.visibility = View.VISIBLE
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                readProgressLayout.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        ) {
            override fun getPriority(): Priority = Priority.HIGH
        }

        queue.add(jsonObjectRequest)
    }

    private fun computeCallbacksNeeded(allCalls: List<Calls>, days: Int): List<Calls> {
        val sortedCalls = allCalls.sortedBy { it.Date }

        val allowedDays = mutableSetOf<String>()
        val cal = Calendar.getInstance()
        repeat(days) {
            val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            allowedDays.add("$month/$day")
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        data class Tracker(
            var lastMissedDate: String? = null,
            var lastMissedType: String? = null,
            var name: String = "Unknown",
            var names: List<String> = emptyList(),
            var resolved: Boolean = false
        )

        val byNumber = mutableMapOf<String, Tracker>()

        for (call in sortedCalls) {
            val number = call.Number ?: continue
            val date = call.Date ?: continue
            val type = call.Type ?: continue

            val tracker = byNumber.getOrPut(number) { Tracker() }

            if (type == "Missed" || type == "Rejected") {
                if (tracker.lastMissedDate == null || date > tracker.lastMissedDate!!) {
                    tracker.lastMissedDate = date
                    tracker.lastMissedType = type
                    tracker.name = call.Name ?: tracker.name
                    tracker.names = call.Names.ifEmpty { tracker.names }
                    tracker.resolved = false
                }
            } else if (type == "Outgoing" || type == "Incoming") {
                if (tracker.lastMissedDate != null && date > tracker.lastMissedDate!!) {
                    tracker.resolved = true
                }
            }
        }

        val result = mutableListOf<Calls>()
        for ((number, tracker) in byNumber) {
            val missedDate = tracker.lastMissedDate ?: continue
            if (tracker.resolved) continue

            val missedDay = missedDate.split(" ")[0]
            if (missedDay !in allowedDays) continue

            result.add(
                Calls(
                    Date = missedDate,
                    Number = number,
                    Name = tracker.name,
                    Type = tracker.lastMissedType,
                    Uploader = null,
                    Names = tracker.names
                )
            )
        }

        return result.sortedByDescending { it.Date }
    }
}