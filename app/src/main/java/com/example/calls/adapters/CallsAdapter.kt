package com.example.calls.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.calls.R
import com.example.calls.models.Calls

class CallsAdapter(private val calls: List<Calls>) :
    RecyclerView.Adapter<CallsAdapter.CallViewHolder>() {

    class CallViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.callItemContainer)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvType: TextView = itemView.findViewById(R.id.tvType)
        val tvUploader: TextView = itemView.findViewById(R.id.tvUploader)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call, parent, false)
        return CallViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        val call = calls[position]
        holder.tvName.text = call.Name
        holder.tvNumber.text = call.Number
        holder.tvDate.text = call.Date
        holder.tvType.text = call.Type
        holder.tvUploader.text = call.Uploader

        val colorRes = when (call.Type) {
            "Incoming" -> R.color.call_incoming
            "Outgoing" -> R.color.call_outgoing
            "Missed" -> R.color.call_missed
            "Rejected" -> R.color.call_rejected
            "Voicemail" -> R.color.call_voicemail
            else -> R.color.call_default
        }

        holder.container.setBackgroundColor(
            ContextCompat.getColor(holder.container.context, colorRes)
        )
    }

    override fun getItemCount(): Int = calls.size
}