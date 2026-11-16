package com.example.calls.sync

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.example.calls.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PresenceReporter {

    fun send(context: Context, name: String, active: Boolean) {
        val url = context.getString(R.string.script_url) + "?action=setPresence"
        val date = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date())

        val request = object : StringRequest(
            Request.Method.POST, url,
            Response.Listener { },
            Response.ErrorListener { }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["who"] = name
                params["active"] = if (active) "1" else "0"
                params["date"] = date
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        request.setShouldCache(false)
        VolleySingleton.getInstance(context).add(request)
    }
}