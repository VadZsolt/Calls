package com.example.calls.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.calls.R
import com.example.calls.models.Contact
import com.example.calls.utils.bindNames

class ContactsAdapter(private val contacts: List<Contact>) :
    RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.contactItemContainer)
        val tvName: TextView = itemView.findViewById(R.id.tvContactName)
        val tvNumber: TextView = itemView.findViewById(R.id.tvContactNumber)
        val tvNameBadge: TextView = itemView.findViewById(R.id.tvNameBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        bindNames(holder.itemView.context, holder.tvName, holder.tvNameBadge, contact.Names)
        holder.tvNumber.text = contact.Number

        holder.container.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${contact.Number}")
            }
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = contacts.size
}