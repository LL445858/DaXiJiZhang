package com.example.daxijizhang.ui.bill

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.databinding.ItemBillBinding
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener
import java.text.SimpleDateFormat
import java.util.Locale

class BillAdapter(
    private val onItemClick: (Bill) -> Unit
) : ListAdapter<Bill, BillAdapter.BillViewHolder>(BillDiffCallback()) {

    private val TAG = "BillAdapter"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var themeColorCache: Int = ThemeManager.getThemeColor()
    
    private val cachedThemeColorWithAlpha: Int by lazy {
        (0x33 shl 24) or (themeColorCache and 0x00FFFFFF)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        try {
            holder.bind(getItem(position))
        } catch (e: Exception) {
            Log.e(TAG, "Error binding bill at position $position", e)
        }
    }

    override fun onBindViewHolder(
        holder: BillViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            try {
                holder.bindPartial(getItem(position), payloads)
            } catch (e: Exception) {
                Log.e(TAG, "Error partial binding bill at position $position", e)
                onBindViewHolder(holder, position)
            }
        }
    }

    fun updateThemeColor(color: Int) {
        themeColorCache = color
    }

    inner class BillViewHolder(
        private val binding: ItemBillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(bill: Bill) {
            binding.apply {
                try {
                    tvDateRange.text = "${dateFormat.format(bill.startDate)} 至 ${dateFormat.format(bill.endDate)}"

                    tvAddress.text = bill.getDisplayAddress()

                    tvAmount.text = String.format("¥%,.2f", bill.totalAmount)
                    tvAmount.setTextColor(themeColorCache)

                    val statusText = bill.getPaymentStatus()
                    tvPaymentStatus.text = statusText

                    tvPaymentStatus.setBackgroundColor(cachedThemeColorWithAlpha)

                    val statusColor = if (bill.isPaid()) {
                        Color.parseColor("#4CAF50")
                    } else {
                        Color.parseColor("#FF9800")
                    }
                    tvPaymentStatus.setTextColor(statusColor)

                    root.setOnOptimizedClickListener(debounceTime = 200) {
                        onItemClick(bill)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in bind()", e)
                }
            }
        }
        
        fun bindPartial(bill: Bill, payloads: MutableList<Any>) {
            binding.apply {
                payloads.forEach { payload ->
                    when (payload) {
                        "amount" -> {
                            tvAmount.text = String.format("¥%,.2f", bill.totalAmount)
                            tvAmount.setTextColor(themeColorCache)
                        }
                        "status" -> {
                            val statusText = bill.getPaymentStatus()
                            tvPaymentStatus.text = statusText
                            tvPaymentStatus.setBackgroundColor(cachedThemeColorWithAlpha)
                            val statusColor = if (bill.isPaid()) {
                                Color.parseColor("#4CAF50")
                            } else {
                                Color.parseColor("#FF9800")
                            }
                            tvPaymentStatus.setTextColor(statusColor)
                        }
                    }
                }
            }
        }
    }

    class BillDiffCallback : DiffUtil.ItemCallback<Bill>() {
        override fun areItemsTheSame(oldItem: Bill, newItem: Bill): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Bill, newItem: Bill): Boolean {
            return oldItem == newItem
        }
        
        override fun getChangePayload(oldItem: Bill, newItem: Bill): Any? {
            return when {
                oldItem.totalAmount != newItem.totalAmount || 
                oldItem.paidAmount != newItem.paidAmount ||
                oldItem.waivedAmount != newItem.waivedAmount -> "amount"
                oldItem.startDate != newItem.startDate ||
                oldItem.endDate != newItem.endDate ||
                oldItem.communityName != newItem.communityName -> null
                else -> null
            }
        }
    }
}
