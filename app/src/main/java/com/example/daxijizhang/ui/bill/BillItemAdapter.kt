package com.example.daxijizhang.ui.bill

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.databinding.ItemBillItemBinding
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil.setOnOptimizedClickListener

class BillItemAdapter(
    private val onItemClick: ((BillItem) -> Unit)? = null,
    private val onDeleteClick: ((BillItem) -> Unit)? = null
) : ListAdapter<BillItem, BillItemAdapter.ItemViewHolder>(ItemDiffCallback()) {

    private val TAG = "BillItemAdapter"
    private var themeColorCache: Int = ThemeManager.getThemeColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemBillItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        try {
            holder.bind(getItem(position))
        } catch (e: Exception) {
            Log.e(TAG, "Error binding bill item at position $position", e)
        }
    }
    
    fun updateThemeColor(color: Int) {
        themeColorCache = color
    }

    inner class ItemViewHolder(
        private val binding: ItemBillItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BillItem) {
            binding.apply {
                try {
                    tvProjectName.text = item.projectName
                    tvItemTotal.text = String.format("¥%.2f", item.totalPrice)
                    tvItemTotal.setTextColor(themeColorCache)
                    tvItemDetail.text = "单价：¥${String.format("%.2f", item.unitPrice)} × 数量：${item.quantity}"

                    root.setOnOptimizedClickListener(debounceTime = 150) {
                        onItemClick?.invoke(item)
                    }

                    btnDelete.setOnOptimizedClickListener(debounceTime = 150) {
                        onDeleteClick?.invoke(item)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in bind()", e)
                }
            }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<BillItem>() {
        override fun areItemsTheSame(oldItem: BillItem, newItem: BillItem): Boolean {
            return oldItem.id == newItem.id && oldItem.billId == newItem.billId
        }

        override fun areContentsTheSame(oldItem: BillItem, newItem: BillItem): Boolean {
            return oldItem.projectName == newItem.projectName &&
                    oldItem.unitPrice == newItem.unitPrice &&
                    oldItem.quantity == newItem.quantity &&
                    oldItem.totalPrice == newItem.totalPrice
        }
    }
}
