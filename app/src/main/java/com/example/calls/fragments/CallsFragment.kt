package com.example.calls.fragments

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.calls.R
import com.example.calls.activities.ReadActivity
import com.example.calls.activities.ReadByDateActivity
import com.example.calls.activities.StatsActivity
import com.example.calls.activities.WriteActivity

class CallsFragment : Fragment(R.layout.fragment_calls) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnWrite).setOnClickListener {
            startActivity(Intent(requireContext(), WriteActivity::class.java))
        }
        view.findViewById<View>(R.id.btnRead).setOnClickListener {
            startActivity(Intent(requireContext(), ReadActivity::class.java))
        }
        view.findViewById<View>(R.id.btnReadByDate).setOnClickListener {
            startActivity(Intent(requireContext(), ReadByDateActivity::class.java))
        }
        view.findViewById<View>(R.id.btnUploaderStats).setOnClickListener {
            startActivity(Intent(requireContext(), StatsActivity::class.java))
        }
    }
    override fun onResume() {
        super.onResume()
        // Lock to portrait
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onPause() {
        super.onPause()
        // Optional: restore previous orientation when leaving the fragment
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}