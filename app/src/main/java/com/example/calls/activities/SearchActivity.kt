package com.example.calls.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.example.calls.R
import com.example.calls.adapters.CallsAdapter
import com.example.calls.models.Calls
import com.example.calls.sync.VolleySingleton
import java.net.URLEncoder

class SearchActivity : AppCompatActivity() {

    private val PAGE_SIZE = 200

    lateinit var searchProgressLayout: RelativeLayout
    lateinit var searchProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView
    lateinit var etSearch: EditText
    lateinit var btnSearch: Button
    lateinit var searchEmptyState: LinearLayout
    lateinit var tvSearchEmptyState: TextView

    private lateinit var adapter: CallsAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private val calls = arrayListOf<Calls>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        searchProgressLayout = findViewById(R.id.searchProgressLayout)
        searchProgressBar = findViewById(R.id.searchProgressBar)
        recyclerView = findViewById(R.id.recyclerView)
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        searchEmptyState = findViewById(R.id.searchEmptyState)
        tvSearchEmptyState = findViewById(R.id.tvSearchEmptyState)

        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        adapter = CallsAdapter(calls)
        recyclerView.adapter = adapter

        searchProgressLayout.visibility = View.GONE

        btnSearch.setOnClickListener {
            performSearch()
        }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }
    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter something to search", Toast.LENGTH_SHORT).show()
            return
        }
        searchProgressLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val queue = VolleySingleton.getInstance(this)
        val url = "${getString(R.string.script_url)}?action=search&query=$encodedQuery&limit=$PAGE_SIZE"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val data = response.getJSONArray("data")
                val results = arrayListOf<Calls>()

                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)

                    val namesArray = obj.optJSONArray("Names")
                    val names = mutableListOf<String>()
                    if (namesArray != null) {
                        for (n in 0 until namesArray.length()) {
                            names.add(namesArray.getString(n))
                        }
                    }

                    results.add(
                        Calls(
                            Id = obj.optString("Id"),
                            Date = obj.getString("Date"),
                            Number = obj.getString("Number"),
                            Name = obj.getString("Name"),
                            Type = obj.getString("Type"),
                            Uploader = obj.optString("Uploader", ""),
                            Names = names,
                            Observation = obj.optString("Observation", "")
                        )
                    )
                }

                calls.clear()
                calls.addAll(results)
                adapter.notifyDataSetChanged()

                searchProgressLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                if (results.isEmpty()) {
                    searchEmptyState.visibility = View.VISIBLE
                    tvSearchEmptyState.text = "No calls found for \"$query\""
                    recyclerView.visibility = View.GONE
                } else {
                    searchEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                searchProgressLayout.visibility = View.GONE
            }
        ) {
            override fun getPriority(): Priority = Priority.HIGH
        }

        queue.add(jsonObjectRequest)
    }
}