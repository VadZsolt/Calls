package com.example.calls.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin)
        findViewById<android.widget.Button>(R.id.btnForceResync).setOnClickListener {
            lifecycleScope.launch {
                SyncPreferences.setLastSyncMillis(this@AdminActivity, 0L)
                Toast.makeText(this@AdminActivity, "Sync point reset for this device", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<android.widget.Button>(R.id.btnForceNowSync).setOnClickListener {
            lifecycleScope.launch {
                SyncPreferences.setLastSyncMillis(this@AdminActivity, System.currentTimeMillis())
                Toast.makeText(this@AdminActivity, "Sync point set to now for this device", Toast.LENGTH_SHORT).show()
            }
        }
    }
}