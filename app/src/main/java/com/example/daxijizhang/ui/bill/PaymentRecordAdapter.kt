package com.example.daxijizhang.ui.bill

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.databinding.ItemPaymentRecordBinding
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentRecordAdapter(
    private val onItemClick: ((PaymentRecord) -> Unit)? = null,
    private val onDeleteClick: ((PaymentRecord) -> Unit)? = null
) : ListAdapter<PaymentRecord, PaymentRecordAdapter.ViewHolder>(DiffCallback()) {

    private val TAG = "PaymentRecordAdapter"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var themeColorCache: Int = ThemeManager.getThemeColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            holder.bind(getItem(position))
        } catch (e: Exception) {
            Log.e(TAG, "Error binding payment record at position $position", e)
        }
    }
    
    fun updateThemeColor(color: Int) {
        themeColorCache = color
    }

    inner class ViewHolder(
        private val binding: ItemPaymentRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(paymentRecord: PaymentRecord) {
            binding.apply {
                try {
                    tvPaymentDate.text = dateFormat.format(paymentRecord.paymentDate)
                    tvPaymentAmount.text = String.format("¥%.2f", paymentRecord.amount)
                    tvPaymentAmount.setTextColor(themeColorCache)

                    root.setOnOptimizedClickListener(debounceTime = 150) {
                        onItemClick?.invoke(paymentRecord)
                    }

                    btnDelete.setOnOptimizedClickListener(debounceTime = 150) {
                        onDeleteClick?.invoke(paymentRecord)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in bind()", e)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PaymentRecord>() {
        override fun areItemsTheSame(oldItem: PaymentRecord, newItem: PaymentRecord): Boolean {
            return oldItem.id == newItem.id && oldItem.billId == newItem.billId
        }

        override fun areContentsTheSame(oldItem: PaymentRecord, newItem: PaymentRecord): Boolean {
            return oldItem.paymentDate == newItem.paymentDate &&
                    oldItem.amount == newItem.amount
        }
    }
}
