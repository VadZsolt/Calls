package com.example.calls.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.calls.R
import com.example.calls.data.SyncPreferences
import com.example.calls.services.CallSyncService
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var switchAutoSync: SwitchMaterial
    private lateinit var tvSyncStatus: TextView
    private lateinit var syncStatusDot: View
    private lateinit var lastUpload: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchAutoSync = view.findViewById(R.id.switchAutoSync)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        syncStatusDot = view.findViewById(R.id.syncStatusDot)
        lastUpload = view.findViewById(R.id.lastUpload)

        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startSyncService() else stopSyncService()
        }

        CallSyncService.isRunning
            .onEach { running ->
                tvSyncStatus.text = if (running) "Auto-sync: Running" else "Auto-sync: Stopped"

                if (switchAutoSync.isChecked != running) {
                    switchAutoSync.setOnCheckedChangeListener(null)
                    switchAutoSync.isChecked = running
                    switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) startSyncService() else stopSyncService()
                    }
                }

                syncStatusDot.backgroundTintList = ContextCompat.getColorStateList(
                    requireContext(),
                    if (running) R.color.call_incoming else R.color.call_missed
                )
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        updateLastUploadText()
    }

    override fun onResume() {
        super.onResume()
        updateLastUploadText()
    }

    private fun updateLastUploadText() {
        viewLifecycleOwner.lifecycleScope.launch {
            val lastSyncMillis = SyncPreferences.getLastSyncMillis(requireContext()).first()
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

    private fun startSyncService() {
        val intent = Intent(requireContext(), CallSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    private fun stopSyncService() {
        requireContext().stopService(Intent(requireContext(), CallSyncService::class.java))
    }
}