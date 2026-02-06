package com.example.daxijizhang.ui.bill

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.databinding.ItemBillItemBinding
import com.example.daxijizhang.util.ThemeManager

class BillItemAdapter(
    private val onItemClick: ((BillItem) -> Unit)? = null,
    private val onDeleteClick: ((BillItem) -> Unit)? = null
) : ListAdapter<BillItem, BillItemAdapter.ItemViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemBillItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ItemViewHolder(
        private val binding: ItemBillItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BillItem) {
            binding.apply {
                tvProjectName.text = item.projectName
                tvItemTotal.text = String.format("¥%.2f", item.totalPrice)
                // 使用主题颜色
                tvItemTotal.setTextColor(ThemeManager.getThemeColor())
                tvItemDetail.text = "单价：¥${String.format("%.2f", item.unitPrice)} × 数量：${item.quantity}"

                root.setOnClickListener {
                    onItemClick?.invoke(item)
                }

                btnDelete.setOnClickListener {
                    onDeleteClick?.invoke(item)
                }
            }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<BillItem>() {
        override fun areItemsTheSame(oldItem: BillItem, newItem: BillItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BillItem, newItem: BillItem): Boolean {
            return oldItem == newItem
        }
    }
}
