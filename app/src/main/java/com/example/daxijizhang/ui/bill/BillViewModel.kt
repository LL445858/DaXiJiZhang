package com.example.daxijizhang.ui.bill

import androidx.lifecycle.*
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.util.PinyinUtil
import kotlinx.coroutines.launch
import java.util.Date

class BillViewModel(val repository: BillRepository) : ViewModel() {

    private val _sortType = MutableLiveData<SortType>(SortType.START_DATE_DESC)
    val sortType: LiveData<SortType> = _sortType

    // 筛选条件
    private val _startDateRangeFilter = MutableLiveData<Pair<Date, Date>?>(null)
    private val _endDateRangeFilter = MutableLiveData<Pair<Date, Date>?>(null)
    private val _paymentStatusFilter = MutableLiveData<PaymentStatusFilter>(PaymentStatusFilter.ALL)

    // 筛选状态
    private val _isFilterActive = MutableLiveData<Boolean>(false)
    val isFilterActive: LiveData<Boolean> = _isFilterActive

    private val _bills = MediatorLiveData<List<Bill>>()
    val bills: LiveData<List<Bill>> = _bills

    val billsWithItems: LiveData<List<BillWithItems>> = repository.allBillsWithItems

    init {
        // 初始化时设置默认数据源
        updateBillsSource()
    }

    private fun updateBillsSource() {
        val sortType = _sortType.value ?: SortType.START_DATE_DESC

        // 对于小区名排序，使用默认数据源然后在应用层排序
        val source = if (sortType == SortType.COMMUNITY_ASC) {
            repository.getBillsSortedByStartDateDesc()
        } else {
            when (sortType) {
                SortType.START_DATE_ASC -> repository.getBillsSortedByStartDateAsc()
                SortType.START_DATE_DESC -> repository.getBillsSortedByStartDateDesc()
                SortType.END_DATE_ASC -> repository.getBillsSortedByEndDateAsc()
                SortType.END_DATE_DESC -> repository.getBillsSortedByEndDateDesc()
                SortType.AMOUNT_ASC -> repository.getBillsSortedByAmountAsc()
                SortType.AMOUNT_DESC -> repository.getBillsSortedByAmountDesc()
                else -> repository.getBillsSortedByStartDateDesc()
            }
        }

        _bills.addSource(source) { bills ->
            val filteredBills = applyFilters(bills)
            val sortedBills = applySorting(filteredBills)
            _bills.value = sortedBills
        }
    }

    private fun applyFilters(bills: List<Bill>): List<Bill> {
        var result = bills

        // 应用开始日期筛选
        _startDateRangeFilter.value?.let { (start, end) ->
            result = result.filter { bill ->
                bill.startDate in start..end
            }
        }

        // 应用结束日期筛选
        _endDateRangeFilter.value?.let { (start, end) ->
            result = result.filter { bill ->
                bill.endDate in start..end
            }
        }

        // 应用结清状态筛选
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
            else -> { /* ALL - 不过滤 */ }
        }

        return result
    }

    private fun applySorting(bills: List<Bill>): List<Bill> {
        return when (_sortType.value) {
            SortType.COMMUNITY_ASC -> {
                // 使用拼音工具进行小区名排序
                // 规则：中文转拼音全拼 -> 字母转小写 -> 数字保持原样 -> 逐个字符比较ASCII码
                bills.sortedWith(
                    compareBy<Bill> { PinyinUtil.getPinyin(it.communityName) }
                        .thenByDescending { it.startDate }
                )
            }
            SortType.AMOUNT_ASC -> bills.sortedWith(
                compareBy<Bill> { it.totalAmount }.thenByDescending { it.startDate }
            )
            SortType.AMOUNT_DESC -> bills.sortedWith(
                compareByDescending<Bill> { it.totalAmount }.thenByDescending { it.startDate }
            )
            SortType.START_DATE_ASC -> bills.sortedWith(
                compareBy<Bill> { it.startDate }.thenByDescending { it.startDate }
            )
            SortType.START_DATE_DESC -> bills.sortedWith(
                compareByDescending<Bill> { it.startDate }.thenByDescending { it.startDate }
            )
            SortType.END_DATE_ASC -> bills.sortedWith(
                compareBy<Bill> { it.endDate }.thenByDescending { it.startDate }
            )
            SortType.END_DATE_DESC -> bills.sortedWith(
                compareByDescending<Bill> { it.endDate }.thenByDescending { it.startDate }
            )
            else -> bills
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
        updateBillsSource()
    }

    // 设置开始日期范围筛选
    fun setStartDateRangeFilter(startDate: Date?, endDate: Date?) {
        _startDateRangeFilter.value = if (startDate != null && endDate != null) {
            Pair(startDate, endDate)
        } else {
            null
        }
        updateFilterStatus()
        updateBillsSource()
    }

    // 设置结束日期范围筛选
    fun setEndDateRangeFilter(startDate: Date?, endDate: Date?) {
        _endDateRangeFilter.value = if (startDate != null && endDate != null) {
            Pair(startDate, endDate)
        } else {
            null
        }
        updateFilterStatus()
        updateBillsSource()
    }

    // 设置结清状态筛选
    fun setPaymentStatusFilter(status: PaymentStatusFilter) {
        _paymentStatusFilter.value = status
        updateFilterStatus()
        updateBillsSource()
    }

    // 清除所有筛选
    fun clearAllFilters() {
        _startDateRangeFilter.value = null
        _endDateRangeFilter.value = null
        _paymentStatusFilter.value = PaymentStatusFilter.ALL
        _isFilterActive.value = false
        updateBillsSource()
    }

    private fun updateFilterStatus() {
        _isFilterActive.value = _startDateRangeFilter.value != null ||
                _endDateRangeFilter.value != null ||
                _paymentStatusFilter.value != PaymentStatusFilter.ALL
    }

    // 获取当前筛选条件描述
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
        return repository.saveBillWithItems(bill, items)
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            repository.deleteBill(bill)
        }
    }

    // 结付记录相关方法
    fun getPaymentRecordsByBillId(billId: Long): LiveData<List<PaymentRecord>> {
        return repository.getPaymentRecordsByBillId(billId)
    }

    suspend fun getTotalPaidByBillId(billId: Long): Double {
        return repository.getTotalPaidByBillId(billId)
    }

    fun insertPaymentRecord(paymentRecord: PaymentRecord) {
        viewModelScope.launch {
            repository.insertPaymentRecord(paymentRecord)
        }
    }

    fun updatePaymentRecord(paymentRecord: PaymentRecord) {
        viewModelScope.launch {
            repository.updatePaymentRecord(paymentRecord)
        }
    }

    fun deletePaymentRecord(paymentRecord: PaymentRecord) {
        viewModelScope.launch {
            repository.deletePaymentRecord(paymentRecord)
        }
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
