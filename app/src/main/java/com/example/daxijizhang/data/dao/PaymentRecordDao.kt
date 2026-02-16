package com.example.daxijizhang.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.model.PaymentWithBillInfo

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

    @Query("""
        SELECT 
            pr.id as paymentId,
            pr.paymentDate as paymentDate,
            pr.amount as amount,
            b.id as billId,
            b.communityName as communityName,
            b.phase as phase,
            b.buildingNumber as buildingNumber,
            b.roomNumber as roomNumber
        FROM payment_records pr
        INNER JOIN bills b ON pr.billId = b.id
        WHERE pr.paymentDate >= :startDate AND pr.paymentDate <= :endDate
        ORDER BY pr.amount DESC
    """)
    suspend fun getPaymentsWithBillInfoByDateRange(startDate: Long, endDate: Long): List<PaymentWithBillInfo>

    @Query("""
        SELECT 
            pr.id as paymentId,
            pr.paymentDate as paymentDate,
            pr.amount as amount,
            b.id as billId,
            b.communityName as communityName,
            b.phase as phase,
            b.buildingNumber as buildingNumber,
            b.roomNumber as roomNumber
        FROM payment_records pr
        INNER JOIN bills b ON pr.billId = b.id
        ORDER BY pr.paymentDate DESC
    """)
    suspend fun getAllPaymentsWithBillInfo(): List<PaymentWithBillInfo>

    @Query("""
        SELECT SUM(pr.amount) 
        FROM payment_records pr
        WHERE pr.paymentDate >= :startDate AND pr.paymentDate <= :endDate
    """)
    suspend fun getTotalPaymentAmountByDateRange(startDate: Long, endDate: Long): Double?

    @Query("""
        SELECT 
            strftime('%m', datetime(pr.paymentDate / 1000, 'unixepoch')) as month,
            SUM(pr.amount) as total
        FROM payment_records pr
        WHERE pr.paymentDate >= :yearStart AND pr.paymentDate <= :yearEnd
        GROUP BY strftime('%m', datetime(pr.paymentDate / 1000, 'unixepoch'))
    """)
    suspend fun getMonthlyPaymentsByYear(yearStart: Long, yearEnd: Long): List<MonthlyPaymentTotal>

    data class MonthlyPaymentTotal(
        val month: String,
        val total: Double
    )
}
