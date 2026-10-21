package com.example.calls.sync

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NoteUploader(private val context: Context) {

    private val url = context.getString(R.string.script_url)

    fun uploadNote(callId: String, note: String, onResult: (Boolean) -> Unit) {
        val queue = VolleySingleton.getInstance(context)

        val request = object : StringRequest(
            Request.Method.POST, "$url?action=addNote",
            Response.Listener { onResult(true) },
            Response.ErrorListener { onResult(false) }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["id"] = callId
                params["note"] = note
                params["modified"] = runBlocking { SyncPreferences.getUploaderName(context).first() } ?: " "
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        queue.add(request)
    }
    fun deleteCall(callId: String, onResult: (Boolean) -> Unit) {
        val queue = VolleySingleton.getInstance(context)

        val request = object : StringRequest(
            Request.Method.POST, "$url?action=deleteCall",
            Response.Listener { onResult(true) },
            Response.ErrorListener { onResult(false) }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["id"] = callId
                params["modified"] = runBlocking { SyncPreferences.getUploaderName(context).first() } ?: " "
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(8000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        queue.add(request)
    }
}