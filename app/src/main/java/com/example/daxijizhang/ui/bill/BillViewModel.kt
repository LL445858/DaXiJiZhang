package com.example.daxijizhang.ui.bill

import androidx.lifecycle.*
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import kotlinx.coroutines.launch
import java.util.Date

class BillViewModel(val repository: BillRepository) : ViewModel() {

    private val _sortType = MutableLiveData<SortType>(SortType.START_DATE_DESC)
    val sortType: LiveData<SortType> = _sortType

    private val _dateRangeFilter = MutableLiveData<Pair<Date, Date>?>(null)

    private val _bills = MediatorLiveData<List<Bill>>()
    val bills: LiveData<List<Bill>> = _bills

    val billsWithItems: LiveData<List<BillWithItems>> = repository.allBillsWithItems

    init {
        // 初始化时设置默认数据源
        updateBillsSource(SortType.START_DATE_DESC, null)
    }

    private fun updateBillsSource(sort: SortType, range: Pair<Date, Date>?) {
        val source = if (range != null) {
            repository.getBillsByDateRange(range.first, range.second)
        } else {
            when (sort) {
                SortType.START_DATE_ASC -> repository.getBillsSortedByStartDateAsc()
                SortType.START_DATE_DESC -> repository.getBillsSortedByStartDateDesc()
                SortType.END_DATE_ASC -> repository.getBillsSortedByEndDateAsc()
                SortType.END_DATE_DESC -> repository.getBillsSortedByEndDateDesc()
                SortType.COMMUNITY_ASC -> repository.getBillsSortedByCommunityAsc()
                SortType.AMOUNT_ASC -> repository.getBillsSortedByAmountAsc()
                SortType.AMOUNT_DESC -> repository.getBillsSortedByAmountDesc()
            }
        }

        _bills.addSource(source) { bills ->
            _bills.value = bills
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
        updateBillsSource(type, _dateRangeFilter.value)
    }

    fun setDateRangeFilter(startDate: Date?, endDate: Date?) {
        _dateRangeFilter.value = if (startDate != null && endDate != null) {
            Pair(startDate, endDate)
        } else {
            null
        }
        updateBillsSource(_sortType.value ?: SortType.START_DATE_DESC, _dateRangeFilter.value)
    }

    fun clearDateRangeFilter() {
        _dateRangeFilter.value = null
        updateBillsSource(_sortType.value ?: SortType.START_DATE_DESC, null)
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
