package com.example.calls.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.activities.MainActivity
import com.example.calls.data.SyncPreferences
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btnResetSim).setOnClickListener {
            lifecycleScope.launch {
                SyncPreferences.setSimAccountId(requireContext(), "")
                Toast.makeText(requireContext(), "SIM selection reset", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.reshowSimPicker()
            }
        }

        view.findViewById<TextView>(R.id.btnResetName).setOnClickListener {
            lifecycleScope.launch {
                SyncPreferences.setUploaderName(requireContext(), "")
                Toast.makeText(requireContext(), "Uploader name reset", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.reshowNamePrompt()
            }
        }
    }
}