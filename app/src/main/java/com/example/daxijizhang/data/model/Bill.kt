package com.example.daxijizhang.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startDate: Date,
    val endDate: Date,
    val communityName: String,
    val phase: String? = null,
    val buildingNumber: String? = null,
    val roomNumber: String? = null,
    val remark: String? = null,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val waivedAmount: Double = 0.0,  // 抹零金额
    val createdAt: Date = Date()
) {
    fun getDisplayAddress(): String {
        val sb = StringBuilder()
        sb.append(communityName)
        if (!phase.isNullOrBlank()) {
            sb.append(" ").append(phase).append("期")
        }
        if (!buildingNumber.isNullOrBlank()) {
            sb.append(" ").append(buildingNumber).append("栋")
        }
        if (!roomNumber.isNullOrBlank()) {
            sb.append(roomNumber)
        }
        return sb.toString()
    }

    fun getPaymentStatus(): String {
        val actualPaid = paidAmount + waivedAmount
        val remaining = totalAmount - actualPaid
        return when {
            waivedAmount > 0.01 -> {
                "已结清，抹零¥${String.format("%.2f", waivedAmount)}"
            }
            remaining <= 0.01 -> {
                "已结清"
            }
            else -> {
                "待结清，剩余¥${String.format("%.2f", remaining)}"
            }
        }
    }

    fun isPaid(): Boolean {
        val actualPaid = paidAmount + waivedAmount
        return (totalAmount - actualPaid) <= 0.01
    }
}
