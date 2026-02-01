package com.example.daxijizhang.data.repository

import androidx.lifecycle.LiveData
import com.example.daxijizhang.data.dao.BillDao
import com.example.daxijizhang.data.dao.BillItemDao
import com.example.daxijizhang.data.dao.PaymentRecordDao
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import java.util.Date

class BillRepository(
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val paymentRecordDao: PaymentRecordDao
) {
    val allBills: LiveData<List<Bill>> = billDao.getAll()
    val allBillsWithItems: LiveData<List<BillWithItems>> = billDao.getAllBillsWithItems()

    suspend fun insertBill(bill: Bill): Long {
        return billDao.insert(bill)
    }

    suspend fun updateBill(bill: Bill) {
        billDao.update(bill)
    }

    suspend fun deleteBill(bill: Bill) {
        billDao.delete(bill)
    }

    suspend fun getBillById(id: Long): Bill? {
        return billDao.getById(id)
    }

    suspend fun getBillWithItems(billId: Long): BillWithItems? {
        return billDao.getBillWithItems(billId)
    }

    suspend fun getAllBillsWithItemsList(): List<BillWithItems> {
        return billDao.getAllBillsWithItemsList()
    }

    suspend fun insertBillItem(item: BillItem): Long {
        return billItemDao.insert(item)
    }

    suspend fun insertBillItems(items: List<BillItem>): List<Long> {
        return billItemDao.insertAll(items)
    }

    suspend fun updateBillItem(item: BillItem) {
        billItemDao.update(item)
    }

    suspend fun deleteBillItem(item: BillItem) {
        billItemDao.delete(item)
    }

    suspend fun deleteBillItemsByBillId(billId: Long) {
        billItemDao.deleteByBillId(billId)
    }

    suspend fun saveBillWithItems(
        bill: Bill,
        items: List<BillItem>,
        paymentRecords: List<PaymentRecord> = emptyList(),
        waivedAmount: Double = 0.0
    ): Long {
        val totalAmount = items.sumOf { it.totalPrice }
        val totalPaid = paymentRecords.sumOf { it.amount }
        val billWithAmount = bill.copy(
            totalAmount = totalAmount,
            paidAmount = totalPaid,
            waivedAmount = waivedAmount
        )
        val billId = billDao.insert(billWithAmount)

        val itemsWithBillId = items.map { it.copy(billId = billId) }
        billItemDao.insertAll(itemsWithBillId)

        // 保存结付记录（不包括抹零金额，因为抹零不创建虚拟记录）
        if (paymentRecords.isNotEmpty()) {
            val recordsWithBillId = paymentRecords.map { it.copy(billId = billId) }
            recordsWithBillId.forEach { paymentRecordDao.insert(it) }
        }

        return billId
    }

    // 更新账单的已支付金额
    suspend fun updateBillPaidAmount(billId: Long) {
        val totalPaid = paymentRecordDao.getTotalPaidByBillId(billId) ?: 0.0
        val bill = billDao.getById(billId)
        bill?.let {
            billDao.update(it.copy(paidAmount = totalPaid))
        }
    }

    // 结付记录相关方法
    suspend fun insertPaymentRecord(paymentRecord: PaymentRecord): Long {
        val id = paymentRecordDao.insert(paymentRecord)
        // 更新账单的已支付金额
        updateBillPaidAmount(paymentRecord.billId)
        return id
    }

    suspend fun updatePaymentRecord(paymentRecord: PaymentRecord) {
        paymentRecordDao.update(paymentRecord)
        // 更新账单的已支付金额
        updateBillPaidAmount(paymentRecord.billId)
    }

    suspend fun deletePaymentRecord(paymentRecord: PaymentRecord) {
        paymentRecordDao.delete(paymentRecord)
        // 更新账单的已支付金额
        updateBillPaidAmount(paymentRecord.billId)
    }

    suspend fun deletePaymentRecordsByBillId(billId: Long) {
        paymentRecordDao.deleteByBillId(billId)
    }

    fun getPaymentRecordsByBillId(billId: Long): LiveData<List<PaymentRecord>> {
        return paymentRecordDao.getByBillId(billId)
    }

    suspend fun getPaymentRecordsByBillIdList(billId: Long): List<PaymentRecord> {
        return paymentRecordDao.getByBillIdList(billId)
    }

    suspend fun getTotalPaidByBillId(billId: Long): Double {
        return paymentRecordDao.getTotalPaidByBillId(billId) ?: 0.0
    }

    // 排序方法
    fun getBillsSortedByStartDateAsc(): LiveData<List<Bill>> =
        billDao.getAllSortedByStartDateAsc()

    fun getBillsSortedByStartDateDesc(): LiveData<List<Bill>> =
        billDao.getAllSortedByStartDateDesc()

    fun getBillsSortedByEndDateAsc(): LiveData<List<Bill>> =
        billDao.getAllSortedByEndDateAsc()

    fun getBillsSortedByEndDateDesc(): LiveData<List<Bill>> =
        billDao.getAllSortedByEndDateDesc()

    fun getBillsSortedByCommunityAsc(): LiveData<List<Bill>> =
        billDao.getAllSortedByCommunityAsc()

    fun getBillsSortedByAmountAsc(): LiveData<List<Bill>> =
        billDao.getAllSortedByAmountAsc()

    fun getBillsSortedByAmountDesc(): LiveData<List<Bill>> =
        billDao.getAllSortedByAmountDesc()

    fun getBillsByDateRange(startDate: Date, endDate: Date): LiveData<List<Bill>> =
        billDao.getBillsByDateRange(startDate, endDate)
}
