package com.example.daxijizhang.ui.bill

import android.util.Log
import androidx.lifecycle.*
import com.example.daxijizhang.data.cache.DataCacheManager
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.util.CrashHandler
import com.example.daxijizhang.util.PinyinUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class BillViewModel(val repository: BillRepository) : ViewModel() {

    private val TAG = "BillViewModel"
    private val cacheManager = DataCacheManager

    private val _sortType = MutableLiveData<SortType>(SortType.START_DATE_DESC)
    val sortType: LiveData<SortType> = _sortType

    private val _startDateRangeFilter = MutableLiveData<Pair<Date, Date>?>(null)
    private val _endDateRangeFilter = MutableLiveData<Pair<Date, Date>?>(null)
    private val _paymentStatusFilter = MutableLiveData<PaymentStatusFilter>(PaymentStatusFilter.ALL)

    private val _isFilterActive = MutableLiveData<Boolean>(false)
    val isFilterActive: LiveData<Boolean> = _isFilterActive

    private val _bills = MediatorLiveData<List<Bill>>()
    val bills: LiveData<List<Bill>> = _bills

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    val billsWithItems: LiveData<List<BillWithItems>> = repository.allBillsWithItems

    private var cachedBillsList: List<Bill>? = null
    private var lastSortType: SortType? = null
    private var currentSource: LiveData<List<Bill>>? = null
    private var originalBillsList: List<Bill> = emptyList()

    init {
        updateBillsSource()
    }

    private fun updateBillsSource() {
        val sortType = _sortType.value ?: SortType.START_DATE_DESC

        val newSource = when (sortType) {
            SortType.START_DATE_ASC -> repository.getBillsSortedByStartDateAsc()
            SortType.START_DATE_DESC -> repository.getBillsSortedByStartDateDesc()
            SortType.END_DATE_ASC -> repository.getBillsSortedByEndDateAsc()
            SortType.END_DATE_DESC -> repository.getBillsSortedByEndDateDesc()
            SortType.AMOUNT_ASC -> repository.getBillsSortedByAmountAsc()
            SortType.AMOUNT_DESC -> repository.getBillsSortedByAmountDesc()
            SortType.COMMUNITY_ASC -> repository.getBillsSortedByCommunityAsc()
        }

        currentSource?.let { oldSource ->
            _bills.removeSource(oldSource)
        }
        
        currentSource = newSource

        _bills.addSource(newSource) { bills ->
            originalBillsList = bills
            viewModelScope.launch(Dispatchers.Default + CrashHandler.coroutineExceptionHandler) {
                val filteredBills = applyFilters(bills)
                val sortedBills = applySorting(filteredBills)
                
                if (sortedBills != cachedBillsList || sortType != lastSortType) {
                    cachedBillsList = sortedBills
                    lastSortType = sortType
                    withContext(Dispatchers.Main) {
                        _bills.value = sortedBills
                    }
                }
            }
        }
    }

    private fun applyCurrentFilters() {
        cachedBillsList = null
        viewModelScope.launch(Dispatchers.Default + CrashHandler.coroutineExceptionHandler) {
            val filteredBills = applyFilters(originalBillsList)
            val sortedBills = applySorting(filteredBills)
            withContext(Dispatchers.Main) {
                _bills.value = sortedBills
            }
        }
    }

    private fun applyFilters(bills: List<Bill>): List<Bill> {
        var result = bills

        _startDateRangeFilter.value?.let { (start, end) ->
            result = result.filter { bill ->
                bill.startDate in start..end
            }
        }

        _endDateRangeFilter.value?.let { (start, end) ->
            result = result.filter { bill ->
                bill.endDate in start..end
            }
        }

        when (_paymentStatusFilter.value) {
            PaymentStatusFilter.PAID -> {
                result = result.filter { bill ->
                    (bill.paidAmount + bill.waivedAmount) >= bill.totalAmount - 0.01
                }
            }
            PaymentStatusFilter.UNPAID -> {
                result = result.filter { bill ->
                    (bill.paidAmount + bill.waivedAmount) < bill.totalAmount - 0.01
                }
            }
            else -> { }
        }

        return result
    }

    private fun applySorting(bills: List<Bill>): List<Bill> {
        return when (_sortType.value) {
            SortType.COMMUNITY_ASC -> {
                bills.sortedWith { b1, b2 ->
                    val cmp = PinyinUtil.compareForSort(b1.communityName, b2.communityName)
                    if (cmp != 0) cmp else b2.startDate.compareTo(b1.startDate)
                }
            }
            SortType.AMOUNT_ASC -> bills.sortedBy { it.totalAmount }
            SortType.AMOUNT_DESC -> bills.sortedByDescending { it.totalAmount }
            SortType.START_DATE_ASC -> bills.sortedBy { it.startDate }
            SortType.START_DATE_DESC -> bills.sortedByDescending { it.startDate }
            SortType.END_DATE_ASC -> bills.sortedBy { it.endDate }
            SortType.END_DATE_DESC -> bills.sortedByDescending { it.endDate }
            else -> bills
        }
    }

    fun setSortType(type: SortType) {
        if (_sortType.value != type) {
            _sortType.value = type
            cachedBillsList = null
            updateBillsSource()
        }
    }

    fun setStartDateRangeFilter(startDate: Date?, endDate: Date?) {
        _startDateRangeFilter.value = if (startDate != null && endDate != null) {
            Pair(startDate, endDate)
        } else {
            null
        }
        updateFilterStatus()
        applyCurrentFilters()
    }

    fun setEndDateRangeFilter(startDate: Date?, endDate: Date?) {
        _endDateRangeFilter.value = if (startDate != null && endDate != null) {
            Pair(startDate, endDate)
        } else {
            null
        }
        updateFilterStatus()
        applyCurrentFilters()
    }

    fun setPaymentStatusFilter(status: PaymentStatusFilter) {
        _paymentStatusFilter.value = status
        updateFilterStatus()
        applyCurrentFilters()
    }

    fun setAllFilters(
        startDateFrom: Date?,
        startDateTo: Date?,
        endDateFrom: Date?,
        endDateTo: Date?,
        paymentStatus: PaymentStatusFilter
    ) {
        _startDateRangeFilter.value = if (startDateFrom != null && startDateTo != null) {
            Pair(startDateFrom, startDateTo)
        } else {
            null
        }
        _endDateRangeFilter.value = if (endDateFrom != null && endDateTo != null) {
            Pair(endDateFrom, endDateTo)
        } else {
            null
        }
        _paymentStatusFilter.value = paymentStatus
        updateFilterStatus()
        applyCurrentFilters()
    }

    fun clearAllFilters() {
        _startDateRangeFilter.value = null
        _endDateRangeFilter.value = null
        _paymentStatusFilter.value = PaymentStatusFilter.ALL
        _isFilterActive.value = false
        cachedBillsList = null
        updateBillsSource()
    }

    private fun updateFilterStatus() {
        _isFilterActive.value = _startDateRangeFilter.value != null ||
                _endDateRangeFilter.value != null ||
                _paymentStatusFilter.value != PaymentStatusFilter.ALL
    }

    fun getCurrentFilterDescription(): String {
        val filters = mutableListOf<String>()

        _startDateRangeFilter.value?.let { (start, end) ->
            filters.add("开始日期: ${formatDate(start)}-${formatDate(end)}")
        }

        _endDateRangeFilter.value?.let { (start, end) ->
            filters.add("结束日期: ${formatDate(start)}-${formatDate(end)}")
        }

        when (_paymentStatusFilter.value) {
            PaymentStatusFilter.PAID -> filters.add("已结清")
            PaymentStatusFilter.UNPAID -> filters.add("未结清")
            else -> {}
        }

        return if (filters.isEmpty()) "无筛选条件" else filters.joinToString("\n")
    }

    private fun formatDate(date: Date): String {
        val sdf = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
        return sdf.format(date)
    }

    suspend fun saveBillWithItems(bill: Bill, items: List<BillItem>): Long {
        return try {
            _isLoading.value = true
            val result = repository.saveBillWithItems(bill, items)
            cacheManager.clearAllCaches()
            Log.d(TAG, "Bill saved successfully with id: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bill", e)
            _errorMessage.value = "保存账单失败：${e.message}"
            -1L
        } finally {
            _isLoading.value = false
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            try {
                _isLoading.value = true
                repository.deleteBill(bill)
                cacheManager.clearAllCaches()
                Log.d(TAG, "Bill deleted: ${bill.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete bill", e)
                _errorMessage.value = "删除账单失败：${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getPaymentRecordsByBillId(billId: Long): LiveData<List<PaymentRecord>> {
        return repository.getPaymentRecordsByBillId(billId)
    }

    suspend fun getTotalPaidByBillId(billId: Long): Double {
        return repository.getTotalPaidByBillId(billId)
    }

    fun insertPaymentRecord(paymentRecord: PaymentRecord) {
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            try {
                repository.insertPaymentRecord(paymentRecord)
                Log.d(TAG, "Payment record inserted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert payment record", e)
                _errorMessage.value = "添加支付记录失败：${e.message}"
            }
        }
    }

    fun updatePaymentRecord(paymentRecord: PaymentRecord) {
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            try {
                repository.updatePaymentRecord(paymentRecord)
                Log.d(TAG, "Payment record updated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update payment record", e)
                _errorMessage.value = "更新支付记录失败：${e.message}"
            }
        }
    }

    fun deletePaymentRecord(paymentRecord: PaymentRecord) {
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            try {
                repository.deletePaymentRecord(paymentRecord)
                Log.d(TAG, "Payment record deleted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete payment record", e)
                _errorMessage.value = "删除支付记录失败：${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refreshBills() {
        cachedBillsList = null
        updateBillsSource()
    }

    enum class SortType {
        START_DATE_ASC,
        START_DATE_DESC,
        END_DATE_ASC,
        END_DATE_DESC,
        COMMUNITY_ASC,
        AMOUNT_ASC,
        AMOUNT_DESC
    }

    enum class PaymentStatusFilter {
        ALL,
        PAID,
        UNPAID
    }

    class Factory(private val repository: BillRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BillViewModel::class.java)) {
                return BillViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
