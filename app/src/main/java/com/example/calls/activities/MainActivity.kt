package com.example.calls.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import com.example.calls.services.CallSyncService
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    lateinit var btnRead: Button
    lateinit var btnWrite: Button
    lateinit var btnResetSim: TextView
    lateinit var btnResetName: TextView

    lateinit var btnReadByDate: Button

    lateinit var switchAutoSync: SwitchMaterial
    lateinit var tvSyncStatus: TextView
    lateinit var syncStatusDot: View
    lateinit var lastUpload: TextView

    private val PHONE_STATE_PERMISSION_CODE = 200
    private var isPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRead = findViewById(R.id.btnRead)
        btnWrite = findViewById(R.id.btnWrite)
        btnResetSim = findViewById(R.id.btnResetSim)
        btnResetName = findViewById(R.id.btnResetName)
        btnReadByDate = findViewById(R.id.btnReadbyDate)

        switchAutoSync = findViewById(R.id.switchAutoSync)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        syncStatusDot = findViewById(R.id.syncStatusDot)
        lastUpload = findViewById(R.id.lastUpload)

        btnRead.setOnClickListener {
            startActivity(Intent(this, ReadActivity::class.java))
        }
        btnWrite.setOnClickListener {
            startActivity(Intent(this, WriteActivity::class.java))
        }
        btnResetSim.setOnClickListener {
            resetSimSelection()
        }
        btnResetName.setOnClickListener {
            resetUploaderName()
        }
        btnReadByDate.setOnClickListener {
            startActivity(Intent(this, ReadByDateActivity::class.java))
        }

        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                startSyncService()
            } else {
                stopSyncService()
            }
        }

        // Reactive: updates status text, switch, and dot the instant the real state changes
        CallSyncService.isRunning
            .onEach { running ->
                tvSyncStatus.text = if (running) "Auto-sync: Running" else "Auto-sync: Stopped"

                if (switchAutoSync.isChecked != running) {
                    switchAutoSync.setOnCheckedChangeListener(null)
                    switchAutoSync.isChecked = running
                    switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            requestNotificationPermissionIfNeeded()
                            startSyncService()
                        } else {
                            stopSyncService()
                        }
                    }
                }

                syncStatusDot.backgroundTintList = ContextCompat.getColorStateList(
                    this,
                    if (running) R.color.call_incoming else R.color.call_missed
                )
            }
            .launchIn(lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        checkUploaderName()
        updateLastUploadText()
    }

    private fun updateLastUploadText() {
        lifecycleScope.launch {
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(this@MainActivity).first()
            lastUpload.text = if (lastSyncMillis > 0L) {
                "Last Uploaded Date: ${formatMillis(lastSyncMillis)}"
            } else {
                "Last Uploaded Date: Never"
            }
        }
    }

    private fun formatMillis(millis: Long): String {
        val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun resetSimSelection() {
        lifecycleScope.launch {
            SyncPreferences.setSimAccountId(this@MainActivity, "")
            Toast.makeText(this@MainActivity, "SIM selection reset", Toast.LENGTH_SHORT).show()
            checkPhoneStatePermissionAndPromptSim()
        }
    }

    private fun resetUploaderName() {
        lifecycleScope.launch {
            SyncPreferences.setUploaderName(this@MainActivity, "")
            Toast.makeText(this@MainActivity, "Uploader name reset", Toast.LENGTH_SHORT).show()
            checkUploaderName()
        }
    }

    private fun checkUploaderName() {
        if (isPromptShowing) return

        lifecycleScope.launch {
            val name = SyncPreferences.getUploaderName(this@MainActivity).first()
            if (name.isNullOrBlank()) {
                showNamePrompt()
            } else {
                checkSimSelection()
            }
        }
    }

    private fun showNamePrompt() {
        isPromptShowing = true
        val input = EditText(this)
        input.hint = "Enter your name"

        AlertDialog.Builder(this)
            .setTitle("Who's using this device?")
            .setMessage("Please enter your name so uploads can be identified.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val enteredName = input.text.toString().trim()
                if (enteredName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    isPromptShowing = false
                    showNamePrompt()
                } else {
                    lifecycleScope.launch {
                        SyncPreferences.setUploaderName(this@MainActivity, enteredName)
                        Toast.makeText(this@MainActivity, "Welcome, $enteredName!", Toast.LENGTH_SHORT).show()
                        isPromptShowing = false
                        checkSimSelection()
                    }
                }
            }
            .create()
            .show()
    }

    private fun checkSimSelection() {
        if (isPromptShowing) return

        lifecycleScope.launch {
            val savedAccountId = SyncPreferences.getSimAccountId(this@MainActivity).first()
            if (savedAccountId.isNullOrBlank()) {
                checkPhoneStatePermissionAndPromptSim()
            }
        }
    }

    private fun checkPhoneStatePermissionAndPromptSim() {
        val phoneStateGranted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val phoneNumbersGranted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED

        if (phoneStateGranted && phoneNumbersGranted) {
            showSimPrompt()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.READ_PHONE_NUMBERS
                ),
                PHONE_STATE_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_STATE_PERMISSION_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                showSimPrompt()
            } else {
                Toast.makeText(this, "Phone permissions are required to select a SIM", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSimPrompt() {
        isPromptShowing = true

        val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

        val subscriptions = try {
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        }

        if (subscriptions.isNullOrEmpty()) {
            Toast.makeText(this, "No SIM cards found on this device", Toast.LENGTH_SHORT).show()
            isPromptShowing = false
            return
        }

        val labels = subscriptions.map { info ->
            "${info.displayName} (${info.number ?: "Slot ${info.simSlotIndex + 1}"})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select the SIM to sync calls from")
            .setCancelable(false)
            .setItems(labels) { _, which ->
                val selectedSim = subscriptions[which]

                val iccId = selectedSim.iccId ?: ""
                val subId = selectedSim.subscriptionId.toString()
                val slotIndex = selectedSim.simSlotIndex.toString()

                val storedValue = "$iccId|$subId|$slotIndex"

                lifecycleScope.launch {
                    SyncPreferences.setSimAccountId(this@MainActivity, storedValue)
                    Toast.makeText(this@MainActivity, "Selected: ${labels[which]}", Toast.LENGTH_SHORT).show()
                    isPromptShowing = false
                }
            }
            .show()
    }

    private fun startSyncService() {
        val intent = Intent(this, CallSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSyncService() {
        val intent = Intent(this, CallSyncService::class.java)
        stopService(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 300
                )
            }
        }
    }
}