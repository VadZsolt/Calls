package com.example.calls.fragments

import android.app.AlertDialog
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.activities.MainActivity
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvCurrentName: TextView
    private lateinit var tvCurrentSim: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCurrentName = view.findViewById(R.id.tvCurrentName)
        tvCurrentSim = view.findViewById(R.id.tvCurrentSim)

        updateCurrentInfo()

        view.findViewById<View>(R.id.btnResetSim).setOnClickListener {
            lifecycleScope.launch {
                SyncPreferences.setSimAccountId(requireContext(), "")
                Toast.makeText(requireContext(), "SIM selection reset", Toast.LENGTH_SHORT).show()
                updateCurrentInfo()
                (activity as? MainActivity)?.reshowSimPicker()
            }
        }

        view.findViewById<View>(R.id.btnResetName).setOnClickListener {
            lifecycleScope.launch {
                SyncPreferences.setUploaderName(requireContext(), "")
                Toast.makeText(requireContext(), "Uploader name reset", Toast.LENGTH_SHORT).show()
                updateCurrentInfo()
                (activity as? MainActivity)?.reshowNamePrompt()
            }
        }

        view.findViewById<View>(R.id.btnOpenSheet).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, getString(R.string.sheet_url).toUri())
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnClearData).setOnClickListener {
            confirmClearAppData()
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentInfo()
    }

    private fun updateCurrentInfo() {
        lifecycleScope.launch {
            val name = SyncPreferences.getUploaderName(requireContext()).first()
            tvCurrentName.text = "Name: ${if (name.isNullOrBlank()) "Not set" else name}"

            val simId = SyncPreferences.getSimAccountId(requireContext()).first()
            tvCurrentSim.text = "SIM: ${
                if (simId.isNullOrBlank()) {
                    "Not set"
                } else if (!simId.isNullOrBlank()) {
                    "Selected slot " + simId.split('|')[1]
                } else {
                    "Configured"
                }
            }"
        }
    }

    private fun confirmClearAppData() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear App Data?")
            .setMessage("This will erase your name, SIM selection, and sync history. This cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.clearApplicationUserData() // wipes data and restarts the app
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}