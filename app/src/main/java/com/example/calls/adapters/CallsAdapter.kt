package com.example.calls.adapters

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.calls.R
import com.example.calls.models.Calls
import com.example.calls.sync.NoteUploader
import com.example.calls.utils.bindNames

class CallsAdapter(private val calls: MutableList<Calls>) :
    RecyclerView.Adapter<CallsAdapter.CallViewHolder>() {

    class CallViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.callItemContainer)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvType: TextView = itemView.findViewById(R.id.tvType)
        val tvUploader: TextView = itemView.findViewById(R.id.tvUploader)
        val tvObservation: TextView = itemView.findViewById(R.id.tvObservation)
        val cardCall: CardView = itemView.findViewById(R.id.cardCall)

        val tvNameBadge: TextView = itemView.findViewById(R.id.tvNameBadge)
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

        if (!call.Observation.isNullOrBlank()) {
            holder.tvObservation.text = "📝 ${call.Observation}"
            holder.tvObservation.visibility = View.VISIBLE
        } else {
            holder.tvObservation.visibility = View.GONE
        }

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
        holder.cardCall.setCardBackgroundColor(
            ContextCompat.getColorStateList(holder.cardCall.context, colorRes)
        )
        holder.container.setOnClickListener { view ->
            view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction {
                            val rawNumber = call.Number?.removePrefix("'") ?: return@withEndAction

                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$rawNumber")
                            }

                            view.context.startActivity(intent)
                        }
                        .start()
                }
                .start()
        }
        holder.container.setOnLongClickListener { view ->
            showNoteDialog(view.context, call, position)
            true
        }
        bindNames(holder.itemView.context, holder.tvName, holder.tvNameBadge, call.Names.ifEmpty { listOf(call.Name ?: "Unknown") })
    }
    private fun showNoteDialog(context: android.content.Context, call: Calls, position: Int) {
        val callId = call.Id
        if (callId.isNullOrBlank()) {
            Toast.makeText(context, "Can't add a note to this call", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(context).apply {
            hint = "Add a short note about this call"
            setText(call.Observation ?: "")
            setSelection(text.length)
        }

        AlertDialog.Builder(context)
            .setTitle("Note for ${call.Name ?: call.Number}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val note = input.text.toString().trim()
                NoteUploader(context).uploadNote(callId, note) { success ->
                    if (success) {
                        calls[position] = call.copy(Observation = note)
                        notifyItemChanged(position)
                        Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save note — check your connection", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun getItemCount(): Int = calls.size
}