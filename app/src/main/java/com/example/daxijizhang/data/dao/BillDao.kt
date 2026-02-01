package com.example.daxijizhang.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillWithItems
import java.util.Date

@Dao
interface BillDao {
    @Insert
    suspend fun insert(bill: Bill): Long

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): Bill?

    @Query("SELECT * FROM bills ORDER BY startDate DESC")
    fun getAll(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY startDate DESC")
    suspend fun getAllList(): List<Bill>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :billId")
    suspend fun getBillWithItems(billId: Long): BillWithItems?

    @Transaction
    @Query("SELECT * FROM bills ORDER BY startDate DESC")
    fun getAllBillsWithItems(): LiveData<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY startDate DESC")
    suspend fun getAllBillsWithItemsList(): List<BillWithItems>

    // 排序查询
    @Query("SELECT * FROM bills ORDER BY startDate ASC")
    fun getAllSortedByStartDateAsc(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY startDate DESC")
    fun getAllSortedByStartDateDesc(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY endDate ASC")
    fun getAllSortedByEndDateAsc(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY endDate DESC")
    fun getAllSortedByEndDateDesc(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY communityName ASC")
    fun getAllSortedByCommunityAsc(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY totalAmount ASC")
    fun getAllSortedByAmountAsc(): LiveData<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY totalAmount DESC")
    fun getAllSortedByAmountDesc(): LiveData<List<Bill>>

    // 日期区间筛选
    @Query("SELECT * FROM bills WHERE startDate >= :startDate AND endDate <= :endDate ORDER BY startDate DESC")
    fun getBillsByDateRange(startDate: Date, endDate: Date): LiveData<List<Bill>>
}
