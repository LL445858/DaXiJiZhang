package com.example.daxijizhang.ui.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daxijizhang.data.model.HeatmapData
import com.example.daxijizhang.data.model.StatisticsData
import com.example.daxijizhang.data.repository.StatisticsRepository
import com.example.daxijizhang.ui.view.YearlyHeatmapData
import com.example.daxijizhang.ui.view.YearlyIncomeData
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class StatisticsViewModel(private val repository: StatisticsRepository) : ViewModel() {

    private val _statisticsData = MutableLiveData<StatisticsData>()
    val statisticsData: LiveData<StatisticsData> = _statisticsData

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _heatmapData = MutableLiveData<HeatmapData?>()
    val heatmapData: LiveData<HeatmapData?> = _heatmapData

    private val _yearlyIncomeData = MutableLiveData<YearlyIncomeData?>()
    val yearlyIncomeData: LiveData<YearlyIncomeData?> = _yearlyIncomeData

    private val _yearlyHeatmapData = MutableLiveData<YearlyHeatmapData?>()
    val yearlyHeatmapData: LiveData<YearlyHeatmapData?> = _yearlyHeatmapData

    private enum class CacheType {
        NONE, MONTH, YEAR, CUSTOM
    }
    
    private var cacheType: CacheType = CacheType.NONE
    private var cachedYear: Int? = null
    private var cachedMonth: Int? = null
    private var cachedCustomStart: Date? = null
    private var cachedCustomEnd: Date? = null
    
    private var cachedStatisticsData: StatisticsData? = null
    private var cachedHeatmapData: HeatmapData? = null
    private var cachedYearlyIncomeData: YearlyIncomeData? = null
    private var cachedYearlyHeatmapData: YearlyHeatmapData? = null

    fun loadYearStatistics(year: Int, forceRefresh: Boolean = false) {
        if (!forceRefresh && cacheType == CacheType.YEAR && cachedYear == year && cachedStatisticsData != null) {
            _statisticsData.value = cachedStatisticsData!!
            _yearlyIncomeData.value = cachedYearlyIncomeData
            _yearlyHeatmapData.value = cachedYearlyHeatmapData
            _heatmapData.value = null
            _isEmpty.value = isDataEmpty(cachedStatisticsData!!)
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _heatmapData.value = null
            try {
                val data = repository.getStatisticsByYear(year)
                _statisticsData.value = data
                _isEmpty.value = isDataEmpty(data)
                
                val yearlyIncome = repository.getYearlyIncomeData(year)
                _yearlyIncomeData.value = yearlyIncome
                
                val yearlyHeatmap = repository.getYearlyHeatmapData(year)
                _yearlyHeatmapData.value = yearlyHeatmap
                
                cacheType = CacheType.YEAR
                cachedYear = year
                cachedMonth = null
                cachedCustomStart = null
                cachedCustomEnd = null
                cachedStatisticsData = data
                cachedHeatmapData = null
                cachedYearlyIncomeData = yearlyIncome
                cachedYearlyHeatmapData = yearlyHeatmap
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMonthStatistics(year: Int, month: Int, forceRefresh: Boolean = false) {
        if (!forceRefresh && cacheType == CacheType.MONTH && cachedYear == year && cachedMonth == month && cachedStatisticsData != null) {
            _statisticsData.value = cachedStatisticsData!!
            _heatmapData.value = cachedHeatmapData
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            _isEmpty.value = isDataEmpty(cachedStatisticsData!!)
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            try {
                val data = repository.getStatisticsByMonth(year, month)
                _statisticsData.value = data
                _isEmpty.value = isDataEmpty(data)
                
                val heatmap = repository.getHeatmapData(year, month)
                _heatmapData.value = heatmap
                
                cacheType = CacheType.MONTH
                cachedYear = year
                cachedMonth = month
                cachedCustomStart = null
                cachedCustomEnd = null
                cachedStatisticsData = data
                cachedHeatmapData = heatmap
                cachedYearlyIncomeData = null
                cachedYearlyHeatmapData = null
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCustomStatistics(startDate: Date, endDate: Date, forceRefresh: Boolean = false) {
        if (!forceRefresh && cacheType == CacheType.CUSTOM && cachedCustomStart == startDate && cachedCustomEnd == endDate && cachedStatisticsData != null) {
            _statisticsData.value = cachedStatisticsData!!
            _heatmapData.value = null
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            _isEmpty.value = isDataEmpty(cachedStatisticsData!!)
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _heatmapData.value = null
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            try {
                val data = repository.getStatisticsByDateRange(startDate, endDate)
                _statisticsData.value = data
                _isEmpty.value = isDataEmpty(data)
                
                cacheType = CacheType.CUSTOM
                cachedYear = null
                cachedMonth = null
                cachedCustomStart = startDate
                cachedCustomEnd = endDate
                cachedStatisticsData = data
                cachedHeatmapData = null
                cachedYearlyIncomeData = null
                cachedYearlyHeatmapData = null
            } catch (e: Exception) {
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isDataEmpty(data: StatisticsData): Boolean {
        return data.startedProjects == 0 &&
                data.endedProjects == 0 &&
                data.completedProjects == 0 &&
                data.totalPayments == 0 &&
                data.topPayments.isEmpty()
    }

    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearCache() {
        cacheType = CacheType.NONE
        cachedYear = null
        cachedMonth = null
        cachedCustomStart = null
        cachedCustomEnd = null
        cachedStatisticsData = null
        cachedHeatmapData = null
        cachedYearlyIncomeData = null
        cachedYearlyHeatmapData = null
    }
}
