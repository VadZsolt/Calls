package com.example.calls.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Request.Priority
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

sealed class SyncResult {
    data class Success(val count: Int) : SyncResult()
    object NoSimSelected : SyncResult()
    object NoNewCalls : SyncResult()
    data class PartialFailure(val uploaded: Int) : SyncResult() // some succeeded, then one failed
    object NetworkFailure : SyncResult() // failed on the very first call, nothing uploaded
    object AlreadySyncing : SyncResult()
}

class CallUploader(private val context: Context) {

    private val requestQueue: RequestQueue = VolleySingleton.getInstance(context)
    private val INITIAL_FALLBACK_DAYS = 1
    private val url = context.getString(R.string.script_url)

    companion object {

        private val isSyncing = AtomicBoolean(false)
    }

    data class CallLogEntry(
        val name: String,
        val number: String,
        val type: String,
        val date: String,
        val rawMillis: Long
    )

    suspend fun syncNow(): SyncResult {
        if (!isSyncing.compareAndSet(false, true)) {
            return SyncResult.AlreadySyncing
        }

        try {
            val simAccountId = SyncPreferences.getSimAccountId(context).first()
            if (simAccountId.isNullOrBlank()) return SyncResult.NoSimSelected

            val uploaderName = SyncPreferences.getUploaderName(context).first() ?: "Unknown"
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(context).first()

            val currentMillis = System.currentTimeMillis()

            if((currentMillis-lastSyncMillis) > 3L * 24 * 60 * 60 * 1000) //3 day 24 hours 60 minutes 60 seconds 1000 milliseconds
                SyncPreferences.setLastSyncMillis(context, currentMillis)

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
        } finally {
            isSyncing.set(false)
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
                override fun getPriority(): Priority = Priority.IMMEDIATE
            }
            stringRequest.retryPolicy = DefaultRetryPolicy(6000, 3, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
            stringRequest.setShouldCache(false)
            requestQueue.add(stringRequest)
        }
    }

    private fun readCallLogsSince(cutoffMillis: Long, simAccountId: String): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return entries // empty list — syncNow() will treat this as NoNewCalls
        }

        val parts = simAccountId.split("|")
        val iccId = parts.getOrElse(0) { "" }
        val subId = parts.getOrElse(1) { "" }
        val slotIndex = parts.getOrElse(2) { "" }

        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.DURATION
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
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val callPhoneAccountId = it.getString(accountIdIdx) ?: continue
                val matches = (iccId.isNotBlank() && callPhoneAccountId.contains(iccId)) ||
                        (subId.isNotBlank() && callPhoneAccountId.contains(subId)) ||
                        (slotIndex.isNotBlank() && callPhoneAccountId == slotIndex)
                if (!matches) continue

                val millis = it.getLong(dateIdx)
                val duration = it.getInt(durationIdx)
                entries.add(
                    CallLogEntry(
                        name = it.getString(nameIdx) ?: "Unknown",
                        number = normalizePhoneNumber(it.getString(numberIdx) ?: ""),
                        type = callTypeToString(it.getInt(typeIdx),duration),
                        date = formatMillis(millis),
                        rawMillis = millis
                    )
                )
            }
        }

        return entries
    }

    private fun callTypeToString(type: Int, durationSeconds: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> {
                if (durationSeconds <= 0) "Missed" else "Outgoing" // unanswered = missed
            }
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
    private fun normalizePhoneNumber(rawNumber: String): String {
        var number = rawNumber.trim()

        // Remove spaces, dashes, parentheses if present
        number = number.replace(Regex("[\\s\\-()]"), "")

        return when {
            number.startsWith("0040") -> "0" + number.removePrefix("0040")
            number.startsWith("+40") -> "0" + number.removePrefix("+40")
            number.startsWith("40") && number.length == 11 -> "0" + number.removePrefix("40")
            else -> number
        }
    }

    suspend fun verifyAndRepair(): Int {
        // Same lock as syncNow(), so the two can never run at the same time
        if (!isSyncing.compareAndSet(false, true)) return 0

        try {
            val simAccountId = SyncPreferences.getSimAccountId(context).first()
            if (simAccountId.isNullOrBlank()) return 0

            val uploaderName = SyncPreferences.getUploaderName(context).first() ?: "Unknown"
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(context).first()
            if (lastSyncMillis <= 0L) return 0

            val oneDaysAgo = System.currentTimeMillis() - 1L * 24 * 60 * 60 * 1000
            val candidates = readCallLogsSince(oneDaysAgo, simAccountId)
                .filter { it.rawMillis <= lastSyncMillis } // newer ones belong to the normal sync
                .takeLast(30)
            if (candidates.isEmpty()) return 0

            // If the check itself fails, do nothing. Never upload on uncertainty.
            val uploadedKeys = fetchUploadedKeys(uploaderName) ?: return 0

            val missing = candidates.filter { verifyKey(it) !in uploadedKeys }

            var repaired = 0
            for (entry in missing) {
                if (sendSingleCallSuspend(entry, uploaderName)) repaired++ else break
            }
            return repaired
        } finally {
            isSyncing.set(false)
        }
    }

    private fun verifyKey(entry: CallLogEntry): String =
        entry.date + "|" + entry.number.filter { it.isDigit() }.takeLast(9)

    private suspend fun fetchUploadedKeys(uploader: String): Set<String>? =
        suspendCancellableCoroutine { cont ->
            val encoded = URLEncoder.encode(uploader, "UTF-8")
            val request = JsonObjectRequest(
                Request.Method.GET, "$url?action=verify&uploader=$encoded", null,
                { response ->
                    val set = HashSet<String>()
                    val arr = response.optJSONArray("keys")
                    if (arr != null) for (i in 0 until arr.length()) set.add(arr.getString(i))
                    if (cont.isActive) cont.resume(set)
                },
                { if (cont.isActive) cont.resume(null) }
            )
            request.retryPolicy = DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
            request.setShouldCache(false)
            requestQueue.add(request)
        }
}