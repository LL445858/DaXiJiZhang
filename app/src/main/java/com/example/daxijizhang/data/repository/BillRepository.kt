package com.example.daxijizhang.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.example.daxijizhang.data.cache.DataCacheManager
import com.example.daxijizhang.data.dao.BillDao
import com.example.daxijizhang.data.dao.BillItemDao
import com.example.daxijizhang.data.dao.PaymentRecordDao
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.notification.DataChangeNotifier
import com.example.daxijizhang.util.InputValidator
import com.example.daxijizhang.util.SafeExecutor
import com.example.daxijizhang.util.MemoryGuard
import java.util.Date

class BillRepository(
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val paymentRecordDao: PaymentRecordDao
) {
    private val TAG = "BillRepository"
    
    private val cacheManager = DataCacheManager
    private val notifier = DataChangeNotifier
    
    val allBills: LiveData<List<Bill>> = billDao.getAll()
    val allBillsWithItems: LiveData<List<BillWithItems>> = billDao.getAllBillsWithItems()

    suspend fun insertBill(bill: Bill): Long {
        return SafeExecutor.runSafely("insertBill", -1L) {
            validateBill(bill)
            
            if (MemoryGuard.isCriticalMemory()) {
                Log.w(TAG, "Critical memory detected, clearing caches before insert")
                cacheManager.clearAllCaches()
            }
            
            val id = billDao.insert(bill)
            cacheManager.clearAllCaches()
            notifier.notifyBillAdded()
            Log.d(TAG, "Bill inserted with id: $id")
            id
        }
    }

    suspend fun updateBill(bill: Bill) {
        SafeExecutor.runSafely("updateBill") {
            validateBill(bill)
            
            billDao.update(bill)
            cacheManager.clearAllCaches()
            notifier.notifyBillUpdated()
            Log.d(TAG, "Bill updated: ${bill.id}")
        }
    }

    suspend fun deleteBill(bill: Bill) {
        SafeExecutor.runSafely("deleteBill") {
            billDao.delete(bill)
            cacheManager.clearAllCaches()
            notifier.notifyBillDeleted()
            Log.d(TAG, "Bill deleted: ${bill.id}")
        }
    }

    suspend fun getBillById(id: Long): Bill? {
        return SafeExecutor.runSafely("getBillById", null) {
            if (id <= 0) {
                Log.w(TAG, "Invalid bill id: $id")
                return@runSafely null
            }
            
            cacheManager.getBill(id) ?: run {
                val bill = billDao.getById(id)
                bill?.let { cacheManager.putBill(it) }
                bill
            }
        }
    }

    suspend fun getBillWithItems(billId: Long): BillWithItems? {
        return SafeExecutor.runSafely("getBillWithItems", null) {
            if (billId <= 0) {
                Log.w(TAG, "Invalid bill id: $billId")
                return@runSafely null
            }
            
            cacheManager.getBillWithItems(billId) ?: run {
                val billWithItems = billDao.getBillWithItems(billId)
                billWithItems?.let { cacheManager.putBillWithItems(it) }
                billWithItems
            }
        }
    }

    suspend fun getAllBillsWithItemsList(): List<BillWithItems> {
        return SafeExecutor.runSafely("getAllBillsWithItemsList", emptyList()) {
            cacheManager.getBillsWithItemsList() ?: run {
                val billsWithItems = billDao.getAllBillsWithItemsList()
                cacheManager.putBillsWithItemsList(billsWithItems)
                billsWithItems.forEach { cacheManager.putBillWithItems(it) }
                billsWithItems
            }
        }
    }

    suspend fun insertBillItem(item: BillItem): Long {
        return SafeExecutor.runSafely("insertItem", -1L) {
            validateBillItem(item)
            val id = billItemDao.insert(item)
            cacheManager.clearAllCaches()
            notifier.notifyBillUpdated()
            Log.d(TAG, "BillItem inserted with id: $id")
            id
        }
    }

    suspend fun insertBillItems(items: List<BillItem>): List<Long> {
        return SafeExecutor.runSafely("insertBillItems", emptyList()) {
            if (items.isEmpty()) return@runSafely emptyList()
            
            items.forEach { validateBillItem(it) }
            val ids = billItemDao.insertAll(items)
            cacheManager.clearAllCaches()
            notifier.notifyBillUpdated()
            Log.d(TAG, "${ids.size} BillItems inserted")
            ids
        }
    }

    suspend fun updateBillItem(item: BillItem) {
        SafeExecutor.runSafely("updateBillItem") {
            validateBillItem(item)
            billItemDao.update(item)
            cacheManager.clearAllCaches()
            notifier.notifyBillUpdated()
            Log.d(TAG, "BillItem updated: ${item.id}")
        }
    }

    suspend fun deleteBillItem(item: BillItem) {
        SafeExecutor.runSafely("deleteBillItem") {
            billItemDao.delete(item)
            cacheManager.clearAllCaches()
            notifier.notifyBillUpdated()
            Log.d(TAG, "BillItem deleted: ${item.id}")
        }
    }

    suspend fun deleteBillItemsByBillId(billId: Long) {
        SafeExecutor.runSafely("deleteBillItemsByBillId") {
            billItemDao.deleteByBillId(billId)
            cacheManager.clearAllCaches()
            Log.d(TAG, "BillItems deleted for bill: $billId")
        }
    }

    suspend fun saveBillWithItems(
        bill: Bill,
        items: List<BillItem>,
        paymentRecords: List<PaymentRecord> = emptyList(),
        waivedAmount: Double = 0.0
    ): Long {
        return SafeExecutor.runSafely("saveBillWithItems", -1L) {
            validateBill(bill)
            if (items.isEmpty()) {
                throw IllegalArgumentException("账单项目不能为空")
            }
            items.forEach { validateBillItem(it) }
            paymentRecords.forEach { validatePaymentRecord(it) }
            
            if (MemoryGuard.isCriticalMemory()) {
                Log.w(TAG, "Critical memory detected, clearing caches")
                cacheManager.clearAllCaches()
            }
            
            val totalAmount = items.sumOf { it.totalPrice }
            val totalPaid = paymentRecords.sumOf { it.amount }
            val billWithAmount = bill.copy(
                totalAmount = totalAmount,
                paidAmount = totalPaid,
                waivedAmount = waivedAmount.coerceAtLeast(0.0)
            )
            val billId = billDao.insert(billWithAmount)

            val itemsWithBillIdAndOrder = items.mapIndexed { index, item ->
                item.copy(billId = billId, orderIndex = index)
            }
            billItemDao.insertAll(itemsWithBillIdAndOrder)

            if (paymentRecords.isNotEmpty()) {
                val recordsWithBillId = paymentRecords.map { it.copy(billId = billId) }
                recordsWithBillId.forEach { paymentRecordDao.insert(it) }
            }
            
            cacheManager.clearAllCaches()
            notifier.notifyBillAdded()
            
            Log.d(TAG, "Bill saved with id: $billId, items: ${items.size}, payments: ${paymentRecords.size}")
            billId
        }
    }

    suspend fun updateBillWithItems(
        billId: Long,
        bill: Bill,
        items: List<BillItem>,
        paymentRecords: List<PaymentRecord> = emptyList(),
        waivedAmount: Double = 0.0
    ): Boolean {
        return SafeExecutor.runSafely("updateBillWithItems", false) {
            validateBill(bill)
            if (items.isEmpty()) {
                throw IllegalArgumentException("账单项目不能为空")
            }
            items.forEach { validateBillItem(it) }
            paymentRecords.forEach { validatePaymentRecord(it) }
            
            val totalAmount = items.sumOf { it.totalPrice }
            val totalPaid = paymentRecords.sumOf { it.amount }
            val billWithAmount = bill.copy(
                id = billId,
                totalAmount = totalAmount,
                paidAmount = totalPaid,
                waivedAmount = waivedAmount.coerceAtLeast(0.0)
            )
            
            billDao.update(billWithAmount)
            
            billItemDao.deleteByBillId(billId)
            val itemsWithBillIdAndOrder = items.mapIndexed { index, item ->
                item.copy(billId = billId, orderIndex = index)
            }
            billItemDao.insertAll(itemsWithBillIdAndOrder)

            paymentRecordDao.deleteByBillId(billId)
            if (paymentRecords.isNotEmpty()) {
                val recordsWithBillId = paymentRecords.map { it.copy(billId = billId) }
                recordsWithBillId.forEach { paymentRecordDao.insert(it) }
            }
            
            cacheManager.clearAllCaches()
            notifier.notifyBillUpdated()
            
            Log.d(TAG, "Bill updated with id: $billId, items: ${items.size}, payments: ${paymentRecords.size}")
            true
        }
    }

    suspend fun updateBillPaidAmount(billId: Long) {
        SafeExecutor.runSafely("updateBillPaidAmount") {
            val totalPaid = paymentRecordDao.getTotalPaidByBillId(billId) ?: 0.0
            val bill = billDao.getById(billId)
            bill?.let {
                billDao.update(it.copy(paidAmount = totalPaid))
                cacheManager.putBill(it.copy(paidAmount = totalPaid))
            }
        }
    }

    suspend fun insertPaymentRecord(paymentRecord: PaymentRecord): Long {
        return SafeExecutor.runSafely("insertPaymentRecord", -1L) {
            validatePaymentRecord(paymentRecord)
            val id = paymentRecordDao.insert(paymentRecord)
            updateBillPaidAmount(paymentRecord.billId)
            cacheManager.clearAllCaches()
            notifier.notifyPaymentAdded()
            Log.d(TAG, "PaymentRecord inserted with id: $id")
            id
        }
    }

    suspend fun updatePaymentRecord(paymentRecord: PaymentRecord) {
        SafeExecutor.runSafely("updatePaymentRecord") {
            validatePaymentRecord(paymentRecord)
            paymentRecordDao.update(paymentRecord)
            updateBillPaidAmount(paymentRecord.billId)
            cacheManager.clearAllCaches()
            notifier.notifyPaymentUpdated()
            Log.d(TAG, "PaymentRecord updated: ${paymentRecord.id}")
        }
    }

    suspend fun deletePaymentRecord(paymentRecord: PaymentRecord) {
        SafeExecutor.runSafely("deletePaymentRecord") {
            paymentRecordDao.delete(paymentRecord)
            updateBillPaidAmount(paymentRecord.billId)
            cacheManager.clearAllCaches()
            notifier.notifyPaymentDeleted()
            Log.d(TAG, "PaymentRecord deleted: ${paymentRecord.id}")
        }
    }

    suspend fun deletePaymentRecordsByBillId(billId: Long) {
        SafeExecutor.runSafely("deletePaymentRecordsByBillId") {
            paymentRecordDao.deleteByBillId(billId)
            cacheManager.clearAllCaches()
            Log.d(TAG, "PaymentRecords deleted for bill: $billId")
        }
    }

    fun getPaymentRecordsByBillId(billId: Long): LiveData<List<PaymentRecord>> {
        return paymentRecordDao.getByBillId(billId)
    }

    suspend fun getPaymentRecordsByBillIdList(billId: Long): List<PaymentRecord> {
        return SafeExecutor.runSafely("getPaymentRecordsByBillIdList", emptyList()) {
            paymentRecordDao.getByBillIdList(billId)
        }
    }

    suspend fun getTotalPaidByBillId(billId: Long): Double {
        return SafeExecutor.runSafely("getTotalPaidByBillId", 0.0) {
            paymentRecordDao.getTotalPaidByBillId(billId) ?: 0.0
        }
    }

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
    
    private fun validateBill(bill: Bill) {
        InputValidator.validateDate(bill.startDate, "开始日期").getOrThrow()
        InputValidator.validateDate(bill.endDate, "结束日期").getOrThrow()
        InputValidator.validateDateRange(bill.startDate, bill.endDate).getOrThrow()
        InputValidator.validateString(bill.communityName, 100, "小区名").getOrThrow()
    }
    
    private fun validateBillItem(item: BillItem) {
        InputValidator.validateString(item.projectName, 100, "项目名").getOrThrow()
        InputValidator.validateAmount(item.unitPrice, 0.0, 1_000_000.0, "单价").getOrThrow()
        InputValidator.validateAmount(item.quantity, 0.0, 1_000_000.0, "数量").getOrThrow()
    }
    
    private fun validatePaymentRecord(record: PaymentRecord) {
        InputValidator.validateDate(record.paymentDate, "支付日期").getOrThrow()
        InputValidator.validateAmount(record.amount, 0.0, 100_000_000.0, "支付金额").getOrThrow()
    }
}
