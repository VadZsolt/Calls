package com.example.calls.sync

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

sealed class SyncResult {
    data class Success(val count: Int) : SyncResult()
    object NoSimSelected : SyncResult()
    object NoNewCalls : SyncResult()
    data class PartialFailure(val uploaded: Int) : SyncResult() // some succeeded, then one failed
    object NetworkFailure : SyncResult() // failed on the very first call, nothing uploaded
}

class CallUploader(private val context: Context) {

    private val requestQueue: RequestQueue = Volley.newRequestQueue(context)
    private val INITIAL_FALLBACK_DAYS = 3
    private val url = "https://script.google.com/macros/s/AKfycbyyorwcDipj7qLs72YLZnpsRpinfSCJlpHAQUKlLLuHM_ChY71sVjsYLsq86ZDsJM3P/exec"

    data class CallLogEntry(
        val name: String,
        val number: String,
        val type: String,
        val date: String,
        val rawMillis: Long
    )

    suspend fun syncNow(): SyncResult {
        val simAccountId = SyncPreferences.getSimAccountId(context).first()
        if (simAccountId.isNullOrBlank()) return SyncResult.NoSimSelected

        val uploaderName = SyncPreferences.getUploaderName(context).first() ?: "Unknown"
        val lastSyncMillis = SyncPreferences.getLastSyncMillis(context).first()

        val cutoffMillis = if (lastSyncMillis > 0L) {
            lastSyncMillis
        } else {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -INITIAL_FALLBACK_DAYS) }.timeInMillis
        }

        val entries = readCallLogsSince(cutoffMillis, simAccountId)
        if (entries.isEmpty()) return SyncResult.NoNewCalls

        var successCount = 0
        var lastSuccessfulMillis = lastSyncMillis

        for (entry in entries) {
            val success = sendSingleCallSuspend(entry, uploaderName)
            if (success) {
                successCount++
                lastSuccessfulMillis = entry.rawMillis
            } else {
                break
            }
        }

        if (successCount > 0) {
            SyncPreferences.setLastSyncMillis(context, lastSuccessfulMillis)
        }

        return when {
            successCount == entries.size -> SyncResult.Success(successCount)
            successCount == 0 -> SyncResult.NetworkFailure
            else -> SyncResult.PartialFailure(successCount)
        }
    }

    private suspend fun sendSingleCallSuspend(entry: CallLogEntry, uploaderName: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            var callbackFired = false

            val stringRequest = object : StringRequest(
                Request.Method.POST, url,
                Response.Listener {
                    if (!callbackFired) {
                        callbackFired = true
                        if (continuation.isActive) continuation.resume(true)
                    }
                },
                Response.ErrorListener {
                    if (!callbackFired) {
                        callbackFired = true
                        if (continuation.isActive) continuation.resume(false)
                    }
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["Date"] = entry.date
                    params["Number"] = "'" + entry.number
                    params["Name"] = entry.name
                    params["Type"] = entry.type
                    params["Uploader"] = uploaderName
                    return params
                }
            }

            stringRequest.retryPolicy = DefaultRetryPolicy(5000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
            stringRequest.setShouldCache(false)
            requestQueue.add(stringRequest)
        }
    }

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
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(cutoffMillis.toString())

        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs,
            "${CallLog.Calls.DATE} ASC"
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
                        (subId.isNotBlank() && callPhoneAccountId.contains(subId)) ||
                        (slotIndex.isNotBlank() && callPhoneAccountId == slotIndex)
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