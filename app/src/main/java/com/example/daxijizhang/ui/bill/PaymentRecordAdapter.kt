package com.example.daxijizhang.ui.bill

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.databinding.ItemPaymentRecordBinding
import com.example.daxijizhang.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentRecordAdapter(
    private val onItemClick: ((PaymentRecord) -> Unit)? = null,
    private val onDeleteClick: ((PaymentRecord) -> Unit)? = null
) : ListAdapter<PaymentRecord, PaymentRecordAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPaymentRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(paymentRecord: PaymentRecord) {
            binding.tvPaymentDate.text = dateFormat.format(paymentRecord.paymentDate)
            binding.tvPaymentAmount.text = String.format("¥%.2f", paymentRecord.amount)
            // 使用主题颜色
            binding.tvPaymentAmount.setTextColor(ThemeManager.getThemeColor())

            binding.root.setOnClickListener {
                onItemClick?.invoke(paymentRecord)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick?.invoke(paymentRecord)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PaymentRecord>() {
        override fun areItemsTheSame(oldItem: PaymentRecord, newItem: PaymentRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PaymentRecord, newItem: PaymentRecord): Boolean {
            return oldItem == newItem
        }
    }
}
