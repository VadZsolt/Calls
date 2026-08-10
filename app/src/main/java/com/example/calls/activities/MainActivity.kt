package com.example.calls.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import com.example.calls.fragments.CallsFragment
import com.example.calls.fragments.MainFragment
import com.example.calls.fragments.SettingsFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.Build
import android.net.Uri
import android.provider.Settings
import com.example.calls.update.AppUpdater
import com.example.calls.update.UpdateInfo

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar

    private val ALL_PERMISSIONS_CODE = 500
    private var isPromptShowing = false
    private var pendingUpdateInfo: com.example.calls.update.UpdateInfo? = null

    private val allRequiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_PHONE_NUMBERS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_CALL_LOG
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        toolbar = findViewById(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            val fragment = when (menuItem.itemId) {
                R.id.nav_main -> MainFragment()
                R.id.nav_calls -> CallsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> MainFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            toolbar.title = menuItem.title
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MainFragment())
                .commit()
            navView.setCheckedItem(R.id.nav_main)
            toolbar.title = "Home"
        }

        checkUploaderName()
        checkForAppUpdate()
    }

    override fun onResume() {
        super.onResume()
        val info = pendingUpdateInfo
        if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            pendingUpdateInfo = null
            startDownload(info)
        }
    }

    // ---------- Called from SettingsFragment after a reset ----------

    fun reshowSimPicker() {
        checkAllPermissionsThenPromptSim()
    }

    fun reshowNamePrompt() {
        checkUploaderName()
    }

    // ---------- Onboarding chain ----------

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
                checkAllPermissionsThenPromptSim()
            }
        }
    }

    private fun checkAllPermissionsThenPromptSim() {
        val missing = allRequiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            showSimPrompt()
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, missing.toTypedArray(), ALL_PERMISSIONS_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == ALL_PERMISSIONS_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Some permissions were denied — call sync, SIM detection, or notifications may not work correctly",
                    Toast.LENGTH_LONG
                ).show()
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.clearApplicationUserData()
                return
            }
            showSimPrompt()
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
                val storedValue = "${selectedSim.iccId ?: ""}|${selectedSim.subscriptionId}|${selectedSim.simSlotIndex}"

                lifecycleScope.launch {
                    SyncPreferences.setSimAccountId(this@MainActivity, storedValue)
                    Toast.makeText(this@MainActivity, "Selected: ${labels[which]}", Toast.LENGTH_SHORT).show()
                    isPromptShowing = false
                }
            }
            .show()
    }
    private fun checkForAppUpdate() {
        AppUpdater(this).checkForUpdate { updateInfo ->
            if (updateInfo != null) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Update available")
                        .setMessage(updateInfo.changelog.ifBlank { "A new version is available." } + "\n\n" + updateInfo.description)
                        .setPositiveButton("Update") { _, _ ->
                            requestInstallPermissionThenDownload(updateInfo)
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
        }
    }

    private fun requestInstallPermissionThenDownload(updateInfo: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingUpdateInfo = updateInfo
            Toast.makeText(this, "Please allow installing updates, then come back", Toast.LENGTH_LONG).show()
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        startDownload(updateInfo)
    }

    private fun startDownload(updateInfo: UpdateInfo) {
        AppUpdater(this).downloadAndInstall(
            updateInfo.apkUrl,
            onStarted = {
                Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
            },
            onProgress = { percent ->
                // Optional: update a progress bar here if you add one to the UI
            },
            onComplete = {
                Toast.makeText(this, "Download complete — installing...", Toast.LENGTH_SHORT).show()
            },
            onError = { message ->
                Toast.makeText(this, "Download failed: $message", Toast.LENGTH_LONG).show()
            }
        )
    }
}