package com.example.calls.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var btnRead: Button
    lateinit var btnWrite: Button
    lateinit var btnResetSim: Button

    private val PHONE_STATE_PERMISSION_CODE = 200
    private var isPromptShowing = false

    //elso futtatas, beallitja az alapot
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRead = findViewById(R.id.btnRead)
        btnWrite = findViewById(R.id.btnWrite)
        btnResetSim = findViewById(R.id.btnResetSim)

        btnRead.setOnClickListener {
            val intent = Intent(this, ReadActivity::class.java)
            startActivity(intent)
        }
        btnWrite.setOnClickListener {
            val intent = Intent(this, WriteActivity::class.java)
            startActivity(intent)
        }
        btnResetSim.setOnClickListener {
            resetSimSelection()
        }
    }

    override fun onResume() {
        super.onResume()
        checkUploaderName()
    }
    //sim valasztas reset
    private fun resetSimSelection() {
        lifecycleScope.launch {
            SyncPreferences.setSimAccountId(this@MainActivity, "")
            Toast.makeText(this@MainActivity, "SIM selection reset", Toast.LENGTH_SHORT).show()
            checkPhoneStatePermissionAndPromptSim()
        }
    }
    //felhasznalonev megnezese hogy van-e elmentve
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
    //felhasznalonev beallitasa
    private fun showNamePrompt() {
        isPromptShowing = true
        val input = EditText(this)
        input.hint = "Enter your name"

        val dialog = AlertDialog.Builder(this)
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
                        Toast.makeText(
                            this@MainActivity,
                            "Welcome, $enteredName!",
                            Toast.LENGTH_SHORT
                        ).show()
                        isPromptShowing = false
                        checkSimSelection()
                    }
                }
            }
            .create()

        dialog.show()
    }
    //sim kartyak megnezese
    private fun checkSimSelection() {
        if (isPromptShowing) return

        lifecycleScope.launch {
            val savedAccountId = SyncPreferences.getSimAccountId(this@MainActivity).first()
            if (savedAccountId.isNullOrBlank()) {
                checkPhoneStatePermissionAndPromptSim()
            }
        }
    }
    //engedelyek az apphoz
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
    //sim valaszto
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

                android.util.Log.d("SimDebug", "Storing simAccountId = '$storedValue'")

                lifecycleScope.launch {
                    SyncPreferences.setSimAccountId(this@MainActivity, storedValue)
                    Toast.makeText(
                        this@MainActivity,
                        "Selected: ${labels[which]}",
                        Toast.LENGTH_SHORT
                    ).show()
                    isPromptShowing = false
                }
            }
            .show()
    }
}