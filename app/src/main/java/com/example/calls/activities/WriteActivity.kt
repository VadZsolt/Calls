package com.example.calls.activities

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import com.example.calls.sync.CallUploader
import com.example.calls.sync.SyncResult
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WriteActivity : AppCompatActivity() {

    lateinit var writeProgressLayout: RelativeLayout
    lateinit var writeProgressBar: ProgressBar
    lateinit var btnSaveDrive: MaterialButton
    lateinit var lastUpload: TextView

    private lateinit var callUploader: CallUploader

    private val CALL_LOG_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_write)

        writeProgressLayout = findViewById(R.id.writeProgressLayout)
        writeProgressBar = findViewById(R.id.writeProgressBar)
        btnSaveDrive = findViewById(R.id.btnSaveDrive)
        lastUpload = findViewById(R.id.lastUpload)

        writeProgressLayout.visibility = View.GONE
        writeProgressBar.visibility = View.GONE

        callUploader = CallUploader(applicationContext)

        updateLastUploadText()

        btnSaveDrive.setOnClickListener {
            checkPermissionAndProceed()
        }
        btnSaveDrive.setOnLongClickListener {
            lifecycleScope.launch {
                SyncPreferences.setLastSyncMillis(this@WriteActivity, 0L) // reset to force full re-sync
                Toast.makeText(this@WriteActivity, "Sync point reset", Toast.LENGTH_SHORT).show()
                updateLastUploadText()
            }
            true
        }
    }
    override fun onResume() {
        super.onResume()
        updateLastUploadText()
    }

    private fun updateLastUploadText() {
        lifecycleScope.launch {
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(this@WriteActivity).first()
            lastUpload.text = if (lastSyncMillis > 0L) {
                "Last Uploaded Date: ${formatMillis(lastSyncMillis)}"
            } else {
                "Last Upload: Never"
            }
        }
    }

    private fun checkPermissionAndProceed() {
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            runSync()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.READ_CALL_LOG), CALL_LOG_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_LOG_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runSync()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runSync() {
        writeProgressLayout.visibility = View.VISIBLE
        writeProgressBar.visibility = View.VISIBLE
        btnSaveDrive.visibility = View.GONE

        lifecycleScope.launch {
            val result = callUploader.syncNow()

            writeProgressLayout.visibility = View.GONE
            writeProgressBar.visibility = View.GONE
            btnSaveDrive.visibility = View.VISIBLE

            when (result) {
                is SyncResult.Success -> {
                    Toast.makeText(
                        this@WriteActivity,
                        "Uploaded ${result.count} new call(s)",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateLastUploadText()
                }
                is SyncResult.PartialFailure -> {
                    Toast.makeText(
                        this@WriteActivity,
                        "Uploaded ${result.uploaded} call(s), then lost connection — will resume next time",
                        Toast.LENGTH_LONG
                    ).show()
                    updateLastUploadText()
                }
                SyncResult.NoNewCalls -> {
                    Toast.makeText(this@WriteActivity, "No new calls to upload", Toast.LENGTH_SHORT).show()
                }
                SyncResult.NoSimSelected -> {
                    Toast.makeText(this@WriteActivity, "No SIM selected — please set it up first", Toast.LENGTH_SHORT).show()
                }
                SyncResult.NetworkFailure -> {
                    Toast.makeText(this@WriteActivity, "Upload failed — check your internet connection", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun formatMillis(millis: Long): String {
        val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}