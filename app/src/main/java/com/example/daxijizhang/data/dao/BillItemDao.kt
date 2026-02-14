package com.example.daxijizhang.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.daxijizhang.data.model.BillItem

@Dao
interface BillItemDao {
    @Insert
    suspend fun insert(item: BillItem): Long

    @Insert
    suspend fun insertAll(items: List<BillItem>): List<Long>

    @Update
    suspend fun update(item: BillItem)

    @Delete
    suspend fun delete(item: BillItem)

    @Query("SELECT * FROM bill_items WHERE id = :id")
    suspend fun getById(id: Long): BillItem?

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY orderIndex ASC")
    fun getByBillId(billId: Long): LiveData<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY orderIndex ASC")
    suspend fun getByBillIdList(billId: Long): List<BillItem>

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteByBillId(billId: Long)
}
