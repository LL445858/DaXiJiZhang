package com.example.daxijizhang.ui.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daxijizhang.data.model.StatisticsData
import com.example.daxijizhang.data.repository.StatisticsRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class StatisticsViewModel(private val repository: StatisticsRepository) : ViewModel() {

    // 统计数据
    private val _statisticsData = MutableLiveData<StatisticsData>()
    val statisticsData: LiveData<StatisticsData> = _statisticsData

    // 加载状态
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // 空数据状态
    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty

    // 错误信息
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /**
     * 加载年份统计数据
     */
    fun loadYearStatistics(year: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val data = repository.getStatisticsByYear(year)
                _statisticsData.value = data
                _isEmpty.value = isDataEmpty(data)
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载月份统计数据
     */
    fun loadMonthStatistics(year: Int, month: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val data = repository.getStatisticsByMonth(year, month)
                _statisticsData.value = data
                _isEmpty.value = isDataEmpty(data)
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载自定义日期范围统计数据
     */
    fun loadCustomStatistics(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val data = repository.getStatisticsByDateRange(startDate, endDate)
                _statisticsData.value = data
                _isEmpty.value = isDataEmpty(data)
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 判断数据是否为空
     */
    private fun isDataEmpty(data: StatisticsData): Boolean {
        return data.startedProjects == 0 &&
                data.endedProjects == 0 &&
                data.completedProjects == 0 &&
                data.totalPayments == 0 &&
                data.topPayments.isEmpty()
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
