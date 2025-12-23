package com.orange.ussd.registration.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orange.ussd.registration.R
import com.orange.ussd.registration.data.model.RegistrationRecord
import com.orange.ussd.registration.data.model.RegistrationStatus
import java.text.SimpleDateFormat
import java.util.*

class RegistrationAdapter : ListAdapter<RegistrationRecord, RegistrationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_registration, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        private val tvFullName: TextView = itemView.findViewById(R.id.tvFullName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(record: RegistrationRecord) {
            tvPhoneNumber.text = record.phoneNumber
            tvFullName.text = record.fullName
            
            // Simple status display
            when (record.status) {
                RegistrationStatus.COMPLETED -> {
                    tvStatus.text = "PASS"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                RegistrationStatus.ALREADY_REGISTERED -> {
                    tvStatus.text = "SKIP"
                    tvStatus.setTextColor(Color.parseColor("#FF9800"))
                }
                RegistrationStatus.FAILED -> {
                    tvStatus.text = "FAIL"
                    tvStatus.setTextColor(Color.parseColor("#F44336"))
                }
                RegistrationStatus.CANCELLED -> {
                    tvStatus.text = "STOP"
                    tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                }
                RegistrationStatus.IN_PROGRESS -> {
                    tvStatus.text = "..."
                    tvStatus.setTextColor(Color.parseColor("#2196F3"))
                }
                RegistrationStatus.USSD_SENT -> {
                    tvStatus.text = "..."
                    tvStatus.setTextColor(Color.parseColor("#FF9800"))
                }
                else -> {
                    tvStatus.text = "WAIT"
                    tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RegistrationRecord>() {
        override fun areItemsTheSame(oldItem: RegistrationRecord, newItem: RegistrationRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RegistrationRecord, newItem: RegistrationRecord): Boolean {
            return oldItem == newItem
        }
    }
}
