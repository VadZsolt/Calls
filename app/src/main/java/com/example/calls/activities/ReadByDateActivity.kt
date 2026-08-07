package com.example.calls.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
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
import com.example.calls.adapters.ContactsAdapter
import com.example.calls.models.Contact
import com.example.calls.sync.VolleySingleton
import java.net.URLEncoder
import java.util.Calendar

class ReadByDateActivity : AppCompatActivity() {

    lateinit var btnPickDate: LinearLayout
    lateinit var tvSelectedDate: TextView
    lateinit var readProgressLayout: RelativeLayout
    lateinit var readProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView
    lateinit var emptyStateLayout: LinearLayout
    lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_read_by_date)

        btnPickDate = findViewById(R.id.btnPickDate)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        readProgressLayout = findViewById(R.id.readProgressLayoutDate)
        readProgressBar = findViewById(R.id.readProgressBarDate)
        recyclerView = findViewById(R.id.recyclerViewDate)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        recyclerView.layoutManager = LinearLayoutManager(this)

        btnPickDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, _, selectedMonth, selectedDay ->
            val monthStr = (selectedMonth + 1).toString().padStart(2, '0')
            val dayStr = selectedDay.toString().padStart(2, '0')
            val formattedDay = "$monthStr/$dayStr"

            tvSelectedDate.text = formattedDay
            fetchUniqueContactsForDay(formattedDay)
        }, year, month, day).show()
    }

    private fun fetchUniqueContactsForDay(day: String) {
        emptyStateLayout.visibility = View.GONE
        readProgressLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        val contacts = arrayListOf<Contact>()
        val queue = VolleySingleton.getInstance(this)

        val encodedDay = URLEncoder.encode(day, "UTF-8")
        val url = "${getString(R.string.script_url)}?action=uniqueNumbers&day=$encodedDay"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val contactsArray = response.getJSONArray("contacts")
                for (i in 0 until contactsArray.length()) {
                    val obj = contactsArray.getJSONObject(i)

                    val namesArray = obj.optJSONArray("Names")
                    val names = mutableListOf<String>()
                    if (namesArray != null) {
                        for (n in 0 until namesArray.length()) {
                            names.add(namesArray.getString(n))
                        }
                    }

                    contacts.add(
                        Contact(
                            Number = obj.getString("Number"),
                            Names = names
                        )
                    )
                }

                readProgressLayout.visibility = View.GONE

                if (contacts.isEmpty()) {
                    emptyStateLayout.visibility = View.VISIBLE
                    tvEmptyState.text = "No calls found for $day"
                } else {
                    recyclerView.adapter = ContactsAdapter(contacts)
                    recyclerView.visibility = View.VISIBLE
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                readProgressLayout.visibility = View.GONE
                emptyStateLayout.visibility = View.VISIBLE
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