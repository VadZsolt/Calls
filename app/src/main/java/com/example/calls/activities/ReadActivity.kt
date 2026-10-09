package com.example.calls.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.Request.Priority
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.example.calls.R
import com.example.calls.adapters.CallsAdapter
import com.example.calls.models.Calls
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.calls.models.CallListItem
import com.example.calls.sync.VolleySingleton
import com.example.calls.utils.groupCallsByDay

class ReadActivity : AppCompatActivity() {

    private val PAGE_SIZE = 40

    lateinit var readProgressLayout: RelativeLayout
    lateinit var readProgressBar: ProgressBar
    lateinit var recyclerView: RecyclerView
    lateinit var swipeRefresh: SwipeRefreshLayout

    private val calls = arrayListOf<Calls>()              // the flat, ungrouped source of truth
    private val displayedItems = mutableListOf<CallListItem>() // what the adapter actually shows (calls + headers)
    private lateinit var adapter: CallsAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var isLoading = false
    private var hasMore = true
    private var hasLoadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_read)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        readProgressLayout = findViewById(R.id.readProgressLayout)
        readProgressBar = findViewById(R.id.readProgressBar)
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        adapter = CallsAdapter(displayedItems)
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0 || isLoading || !hasMore) return

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                if (visibleItemCount + firstVisibleItem >= totalItemCount - 20) {
                    loadNextPage()
                }
            }
        })

        swipeRefresh.setOnRefreshListener {
            refreshList()
        }
        loadNextPage()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent.getBooleanExtra("from_notification", false)) {
                    // Came from the notification → go to MainActivity with the right fragment
                    startActivity(Intent(this@ReadActivity, MainActivity::class.java).apply {
                        putExtra("open_fragment", "calls")   // change to your real tag
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                } else {
                    isEnabled = false          // temporarily disable this callback
                    onBackPressedDispatcher.onBackPressed()  // let the system finish the Activity
                }
            }
        })
    }

    private fun refreshList() {
        calls.clear()
        rebuildDisplayedItems()
        hasMore = true
        isLoading = false
        loadNextPage()
    }

    /** Rebuilds the grouped (header + call) list from the flat `calls` list, and refreshes the adapter. */
    private fun rebuildDisplayedItems() {
        displayedItems.clear()
        displayedItems.addAll(groupCallsByDay(calls))
        adapter.notifyDataSetChanged()
    }

    private fun loadNextPage() {
        if (isLoading || !hasMore) return
        isLoading = true

        if (!hasLoadedOnce) {
            readProgressLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }

        val queue = VolleySingleton.getInstance(this)
        val offset = calls.size
        val url = "${getString(R.string.script_url)}?action=page&offset=$offset&limit=$PAGE_SIZE"

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                try {
                    val data = response.getJSONArray("data")
                    val newCalls = arrayListOf<Calls>()

                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)

                        val namesArray = obj.optJSONArray("Names")
                        val names = mutableListOf<String>()
                        if (namesArray != null) {
                            for (n in 0 until namesArray.length()) {
                                names.add(namesArray.getString(n))
                            }
                        }

                        newCalls.add(
                            Calls(
                                obj.getString("Id"),
                                obj.getString("Date"),
                                obj.getString("Number"),
                                obj.getString("Name"),
                                obj.getString("Type"),
                                obj.optString("Uploader", ""),
                                Names = names,
                                obj.optString("Observation", "")
                            )
                        )
                    }

                    calls.addAll(newCalls)
                    calls.sortByDescending { it.Date }
                    rebuildDisplayedItems() // NEW — regroup with headers after every page load

                    hasMore = response.optBoolean("hasMore", false) && newCalls.isNotEmpty()
                } catch (e: Exception) {
                    Toast.makeText(this, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                    hasLoadedOnce = true
                    readProgressLayout.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    swipeRefresh.isRefreshing = false
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, error.toString(), Toast.LENGTH_SHORT).show()
                isLoading = false
                hasLoadedOnce = true
                readProgressLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = false
            }
        ) {
            override fun getPriority(): Priority = Priority.HIGH
        }
        jsonObjectRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
            8000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(jsonObjectRequest)
    }
}