package com.example.calls.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.calls.R
import com.example.calls.models.UploaderStat

class UploaderStatsAdapter(private val stats: List<UploaderStat>) :
    RecyclerView.Adapter<UploaderStatsAdapter.StatViewHolder>() {

    class StatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvName: TextView = itemView.findViewById(R.id.tvUploaderName)
        val tvLastUpload: TextView = itemView.findViewById(R.id.tvLastUpload)
        val tvTotal: TextView = itemView.findViewById(R.id.tvTotalCalls)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_uploader_stat, parent, false)
        return StatViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatViewHolder, position: Int) {
        val stat = stats[position]
        holder.tvRank.text = (position + 1).toString()
        holder.tvName.text = stat.uploader
        holder.tvLastUpload.text = "Last: ${stat.lastUpload}"
        holder.tvTotal.text = stat.total.toString()
    }

    override fun getItemCount(): Int = stats.size
}