package com.example.daxijizhang.ui.bill

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.databinding.ItemBillBinding
import java.text.SimpleDateFormat
import java.util.Locale

class BillAdapter(
    private val onItemClick: (Bill) -> Unit
) : ListAdapter<Bill, BillAdapter.BillViewHolder>(BillDiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BillViewHolder(
        private val binding: ItemBillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(bill: Bill) {
            binding.apply {
                // 日期范围
                tvDateRange.text = "${dateFormat.format(bill.startDate)} 至 ${dateFormat.format(bill.endDate)}"

                // 地址
                tvAddress.text = bill.getDisplayAddress()

                // 金额
                tvAmount.text = String.format("¥%,.2f", bill.totalAmount)

                // 支付状态
                val statusText = bill.getPaymentStatus()
                tvPaymentStatus.text = statusText

                // 根据状态设置颜色
                val statusColor = if (bill.isPaid()) {
                    Color.parseColor("#4CAF50") // 绿色
                } else {
                    Color.parseColor("#FF9800") // 橙色
                }
                tvPaymentStatus.setTextColor(statusColor)

                // 点击事件
                root.setOnClickListener { onItemClick(bill) }
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
    }
}
