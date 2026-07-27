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
import java.util.Locale

class ReadCallbacksActivity : AppCompatActivity() {

    // Change this to widen the range: 1 = today only, 7 = last week, etc.
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
        swipeRefresh.setOnRefreshListener {
            fetchAndComputeCallbacks()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchAndComputeCallbacks()
    }

    private fun fetchAndComputeCallbacks() {
        readProgressLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        val queue = Volley.newRequestQueue(this)
        val url = getString(R.string.script_url) // default doGet — full call list

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val allCalls = arrayListOf<Calls>()
                val data = response.getJSONArray("data")
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    allCalls.add(
                        Calls(
                            obj.getString("Date"),
                            obj.getString("Number"),
                            obj.getString("Name"),
                            obj.getString("Type"),
                            obj.optString("Uploader", "")
                        )
                    )
                }

                val callbacksNeeded = computeCallbacksNeeded(allCalls, DAYS_TO_CHECK)

                readProgressLayout.visibility = View.GONE
                swipeRefresh.isRefreshing = false

                if (callbacksNeeded.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                } else {
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

    /**
     * Finds numbers whose most recent Missed/Rejected call within the last [days] days
     * has no later Outgoing call after it (i.e. not called back yet).
     */
    private fun computeCallbacksNeeded(allCalls: List<Calls>, days: Int): List<Calls> {
        // Build the set of allowed "MM/dd" day-strings, e.g. today + the past (days-1) days
        //sort to ascending to find incoming or outgoing calls after missed

        val sortedCalls = allCalls.sortedBy { it.Date }
        val allowedDays = mutableSetOf<String>()
        val cal = Calendar.getInstance()
        repeat(days) {
            val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            allowedDays.add("$month/$day")
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        // Track per-number: last missed/rejected call info, and whether an outgoing call happened after it
        data class Tracker(
            var lastMissedDate: String? = null,
            var lastMissedType: String? = null,
            var name: String = "Unknown",
            var outgoingAfter: Boolean = false
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
                    tracker.outgoingAfter = false // reset — a newer missed call resets "called back" state
                }
            } else if (type == "Outgoing" || type =="Incoming") {
                if (tracker.lastMissedDate != null && date > tracker.lastMissedDate!!) {
                    tracker.outgoingAfter = true
                }
            }
        }

        val result = mutableListOf<Calls>()
        for ((number, tracker) in byNumber) {
            val missedDate = tracker.lastMissedDate ?: continue
            if (tracker.outgoingAfter) continue

            val missedDay = missedDate.split(" ")[0] // "MM/dd" part
            if (missedDay !in allowedDays) continue

            result.add(
                Calls(
                    Date = missedDate,
                    Number = number,
                    Name = tracker.name,
                    Type = tracker.lastMissedType,
                    Uploader = null
                )
            )
        }

        // Newest first
        return result.sortedByDescending { it.Date }
    }
}