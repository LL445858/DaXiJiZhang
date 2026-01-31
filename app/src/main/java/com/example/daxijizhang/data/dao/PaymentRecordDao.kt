package com.example.daxijizhang.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.daxijizhang.data.model.PaymentRecord

@Dao
interface PaymentRecordDao {
    @Insert
    suspend fun insert(paymentRecord: PaymentRecord): Long

    @Update
    suspend fun update(paymentRecord: PaymentRecord)

    @Delete
    suspend fun delete(paymentRecord: PaymentRecord)

    @Query("SELECT * FROM payment_records WHERE id = :id")
    suspend fun getById(id: Long): PaymentRecord?

    @Query("SELECT * FROM payment_records WHERE billId = :billId ORDER BY paymentDate DESC")
    fun getByBillId(billId: Long): LiveData<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE billId = :billId ORDER BY paymentDate DESC")
    suspend fun getByBillIdList(billId: Long): List<PaymentRecord>

    @Query("SELECT SUM(amount) FROM payment_records WHERE billId = :billId")
    suspend fun getTotalPaidByBillId(billId: Long): Double?

    @Query("DELETE FROM payment_records WHERE billId = :billId")
    suspend fun deleteByBillId(billId: Long)
}
