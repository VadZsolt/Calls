package com.example.calls.activities

import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WriteActivity : AppCompatActivity() {

    lateinit var writeProgressLayout: RelativeLayout
    lateinit var writeProgressBar: ProgressBar
    lateinit var btnSaveDrive: Button

    lateinit var lastUpload: TextView

    private lateinit var requestQueue: RequestQueue

    private val CALL_LOG_PERMISSION_CODE = 100
    private val INITIAL_FALLBACK_DAYS = 3 //first upload date

    private var uploaderName: String? = null

    data class CallLogEntry(
        val name: String,
        val number: String,
        val type: String,
        val date: String,
        val rawMillis: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_write)

        writeProgressLayout = findViewById(R.id.writeProgressLayout)
        writeProgressBar = findViewById(R.id.writeProgressBar)
        btnSaveDrive = findViewById(R.id.btnSaveDrive)

        lastUpload = findViewById(R.id.lastUpload)
        lifecycleScope.launch {
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(this@WriteActivity).first()

            lastUpload.text = if (lastSyncMillis > 0L) {
                "Last Upload: ${formatMillis(lastSyncMillis)}"
            } else {
                "Last Upload: Never"
            }
        }


        writeProgressLayout.visibility = View.GONE
        writeProgressBar.visibility = View.GONE

        requestQueue = Volley.newRequestQueue(this)

        btnSaveDrive.setOnClickListener {
            checkPermissionAndProceed()
        }
        btnSaveDrive.setOnLongClickListener {
            lifecycleScope.launch {
                SyncPreferences.setLastSyncMillis(this@WriteActivity, 0L) // reset to force full re-sync
                Toast.makeText(this@WriteActivity, "Sync point reset", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
    //engedelyek megnezese
    private fun checkPermissionAndProceed() {
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            fetchAndSendCallLogs()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.READ_CALL_LOG), CALL_LOG_PERMISSION_CODE
            )
        }
    }
    //engedelyek kerese/megnezese hivashoz
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_LOG_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchAndSendCallLogs()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //hivaslista letrehozaza
    private fun fetchAndSendCallLogs() {
        lifecycleScope.launch {
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(this@WriteActivity).first()
            val simAccountId = SyncPreferences.getSimAccountId(this@WriteActivity).first()

            uploaderName = SyncPreferences.getUploaderName(this@WriteActivity).first()

            if (simAccountId.isNullOrBlank()) {
                Toast.makeText(this@WriteActivity, "No SIM selected — please set it up first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val cutoffMillis = if (lastSyncMillis > 0L) {
                lastSyncMillis
            } else {
                // first run ever: fall back to last N days
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -INITIAL_FALLBACK_DAYS)
                }.timeInMillis
            }

            val entries = readCallLogsSince(cutoffMillis,simAccountId?:"")

            if (entries.isEmpty()) {
                Toast.makeText(this@WriteActivity, "No new calls to upload", Toast.LENGTH_SHORT).show()
                return@launch
            }

            writeProgressLayout.visibility = View.VISIBLE
            writeProgressBar.visibility = View.VISIBLE
            btnSaveDrive.visibility = View.GONE


            sendCallsSequentially(entries, 0)
        }
    }
    //hhivasok feltoltese egyesevel
    private fun sendCallsSequentially(entries: List<CallLogEntry>, index: Int) {
        if (index >= entries.size) {
            // All calls uploaded successfully — save the newest call's timestamp
            val newestMillis = entries.maxOf { it.rawMillis }

            lifecycleScope.launch {
                SyncPreferences.setLastSyncMillis(this@WriteActivity, newestMillis)

                writeProgressLayout.visibility = View.GONE
                writeProgressBar.visibility = View.GONE
                btnSaveDrive.visibility = View.VISIBLE
                Toast.makeText(
                    this@WriteActivity,
                    "Uploaded ${entries.size} new calls",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val entry = entries[index]

        sendSingleCall(entry) {
            // Only move to the next call once this one is confirmed done
            sendCallsSequentially(entries, index + 1)
        }
    }
    private fun sendSingleCall(entry: CallLogEntry, onDone: () -> Unit) {
        val url = "https://script.google.com/macros/s/AKfycbyyorwcDipj7qLs72YLZnpsRpinfSCJlpHAQUKlLLuHM_ChY71sVjsYLsq86ZDsJM3P/exec"

        var callbackFired = false

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener {
                if (!callbackFired) {
                    callbackFired = true
                    onDone()
                }
            },
            Response.ErrorListener {
                Toast.makeText(this, it.toString(), Toast.LENGTH_SHORT).show()
                if (!callbackFired) {
                    callbackFired = true
                    onDone()
                }
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Date"] = entry.date
                params["Number"] = "'" + entry.number
                params["Name"] = entry.name
                params["Type"] = entry.type
                params["Uploader"] = uploaderName?:"Unknown"
                return params
            }
            override fun getPriority(): Priority {
                return Priority.HIGH
            }
        }

        // Disable automatic retries, and give Apps Script enough time to respond
        stringRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
            5000, // 15 second timeout (Apps Script can be slow)
            0,     // 0 retries — do NOT resend automatically
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        stringRequest.setShouldCache(false)
        requestQueue.add(stringRequest)
    }
    //kiolvasni a hivast a telefonbol
    private fun readCallLogsSince(cutoffMillis: Long, simAccountId: String): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()

        val parts = simAccountId.split("|")
        val iccId = parts.getOrElse(0) { "" }
        val subId = parts.getOrElse(1) { "" }
        val slotIndex = parts.getOrElse(2) { "" }

        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        // Strictly greater-than, so the last-synced call itself isn't re-sent
        val selection = "${CallLog.Calls.DATE} > ? "
        val selectionArgs = arrayOf(cutoffMillis.toString())


        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs,
            "${CallLog.Calls.DATE} ASC" // ascending, so upload order matches call order
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val accountIdIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

            while (it.moveToNext()) {
                val callPhoneAccountId = it.getString(accountIdIdx) ?: continue

                val matches = (iccId.isNotBlank() && callPhoneAccountId.contains(iccId)) ||
                        (subId.isNotBlank() && callPhoneAccountId.contains(subId) ||
                                (slotIndex.isNotBlank() && callPhoneAccountId == slotIndex))

                if (!matches) continue

                val millis = it.getLong(dateIdx)
                entries.add(
                    CallLogEntry(
                        name = it.getString(nameIdx) ?: "Unknown",
                        number = it.getString(numberIdx) ?: "",
                        type = callTypeToString(it.getInt(typeIdx)),
                        date = formatMillis(millis),
                        rawMillis = millis
                    )
                )
            }
        }

        return entries
    }
    //tipus alakitasa szovegbe
    private fun callTypeToString(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.REJECTED_TYPE -> "Rejected"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
            else -> "Unknown"
        }
    }

    private fun formatMillis(millis: Long): String {
        val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}