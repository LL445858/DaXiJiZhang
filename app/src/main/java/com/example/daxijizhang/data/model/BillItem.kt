package com.example.daxijizhang.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["billId"])]
)
data class BillItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val projectName: String,
    val unitPrice: Double,
    val quantity: Double,
    val totalPrice: Double = unitPrice * quantity
) {
    companion object {
        fun calculateTotalPrice(unitPrice: Double, quantity: Double): Double {
            return unitPrice * quantity
        }
    }
}
