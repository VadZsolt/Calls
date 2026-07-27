package com.example.calls.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Request.Priority
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.calls.R
import com.example.calls.adapters.CallsAdapter
import com.example.calls.models.Calls
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast

class ReadActivity : AppCompatActivity() {

    private val PAGE_SIZE = 20

    lateinit var readProgressLayout: RelativeLayout
    lateinit var readProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView

    private val calls = arrayListOf<Calls>()
    private lateinit var adapter: CallsAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var isLoading = false
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)

        readProgressLayout = findViewById(R.id.readProgressLayout)
        readProgressBar = findViewById(R.id.readProgressBar)
        recyclerView = findViewById(R.id.recyclerView)

        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        adapter = CallsAdapter(calls)
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0 || isLoading || !hasMore) return

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                // Trigger next page a bit before hitting the very bottom
                if (visibleItemCount + firstVisibleItem >= totalItemCount - 8) {
                    loadNextPage()
                }
            }
        })

        loadNextPage() // initial load
    }

    private fun loadNextPage() {
        if (isLoading || !hasMore) return
        isLoading = true

        if (calls.isEmpty()) {
            readProgressLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        val queue = Volley.newRequestQueue(this)
        val offset = calls.size
        val url = "${getString(R.string.script_url)}?action=page&offset=$offset&limit=$PAGE_SIZE"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                val data = response.getJSONArray("data")
                val newCalls = arrayListOf<Calls>()

                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    newCalls.add(
                        Calls(
                            obj.getString("Date"),
                            obj.getString("Number"),
                            obj.getString("Name"),
                            obj.getString("Type"),
                            obj.optString("Uploader", "")
                        )
                    )
                }

                val startPos = calls.size
                calls.addAll(newCalls)
                adapter.notifyItemRangeInserted(startPos, newCalls.size)

                hasMore = response.optBoolean("hasMore", false) && newCalls.isNotEmpty()
                isLoading = false

                readProgressLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                readProgressLayout.visibility = View.GONE
                isLoading = false
            }
        ) {
            override fun getPriority(): Priority = Priority.HIGH
        }

        queue.add(jsonObjectRequest)
    }
}