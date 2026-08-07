package com.example.calls.sync

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

object VolleySingleton {
    @Volatile
    private var queue: RequestQueue? = null

    fun getInstance(context: Context): RequestQueue {
        return queue ?: synchronized(this) {
            queue ?: Volley.newRequestQueue(context.applicationContext).also { queue = it }
        }
    }
}