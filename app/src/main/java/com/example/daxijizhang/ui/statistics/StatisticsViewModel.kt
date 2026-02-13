package com.example.daxijizhang.ui.statistics

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daxijizhang.data.cache.DataCacheManager
import com.example.daxijizhang.data.model.HeatmapData
import com.example.daxijizhang.data.model.StatisticsData
import com.example.daxijizhang.data.notification.DataChangeNotifier
import com.example.daxijizhang.data.repository.StatisticsRepository
import com.example.daxijizhang.ui.view.YearlyHeatmapData
import com.example.daxijizhang.ui.view.YearlyIncomeData
import com.example.daxijizhang.util.CrashHandler
import com.example.daxijizhang.util.MemoryGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class StatisticsViewModel(private val repository: StatisticsRepository) : ViewModel() {

    private val TAG = "StatisticsViewModel"
    private val cacheManager = DataCacheManager

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
    
    private var isLoadingData = false

    fun observeDataChanges(owner: androidx.lifecycle.LifecycleOwner) {
        DataChangeNotifier.dataVersion.observe(owner) { version ->
            if (version > 0) {
                Log.d(TAG, "Data change detected, version: $version, refreshing statistics")
                refreshCurrentData()
            }
        }
    }

    fun loadYearStatistics(year: Int, forceRefresh: Boolean = false) {
        if (isLoadingData && !forceRefresh) {
            Log.d(TAG, "Already loading, skipping year statistics request")
            return
        }
        
        if (!forceRefresh && cacheType == CacheType.YEAR && cachedYear == year && cachedStatisticsData != null) {
            Log.d(TAG, "Using cached year statistics for year: $year")
            _statisticsData.value = cachedStatisticsData!!
            _yearlyIncomeData.value = cachedYearlyIncomeData
            _yearlyHeatmapData.value = cachedYearlyHeatmapData
            _heatmapData.value = null
            _isEmpty.value = cachedStatisticsData!!.isEmpty()
            return
        }
        
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            isLoadingData = true
            _isLoading.value = true
            _errorMessage.value = null
            _heatmapData.value = null
            
            try {
                val data = withContext(Dispatchers.IO) { 
                    repository.getStatisticsByYear(year) 
                }
                _statisticsData.value = data
                _isEmpty.value = data.isEmpty()
                
                val yearlyIncome = withContext(Dispatchers.IO) { 
                    repository.getYearlyIncomeData(year) 
                }
                _yearlyIncomeData.value = yearlyIncome
                
                val yearlyHeatmap = withContext(Dispatchers.IO) { 
                    repository.getYearlyHeatmapData(year) 
                }
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
                
                Log.d(TAG, "Year statistics loaded successfully for year: $year")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load year statistics", e)
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
                _statisticsData.value = StatisticsData.empty()
            } finally {
                _isLoading.value = false
                isLoadingData = false
            }
        }
    }

    fun loadMonthStatistics(year: Int, month: Int, forceRefresh: Boolean = false) {
        if (isLoadingData && !forceRefresh) {
            Log.d(TAG, "Already loading, skipping month statistics request")
            return
        }
        
        if (!forceRefresh && cacheType == CacheType.MONTH && cachedYear == year && cachedMonth == month && cachedStatisticsData != null) {
            Log.d(TAG, "Using cached month statistics for $year-$month")
            _statisticsData.value = cachedStatisticsData!!
            _heatmapData.value = cachedHeatmapData
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            _isEmpty.value = cachedStatisticsData!!.isEmpty()
            return
        }
        
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            isLoadingData = true
            _isLoading.value = true
            _errorMessage.value = null
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            
            try {
                val data = withContext(Dispatchers.IO) { 
                    repository.getStatisticsByMonth(year, month) 
                }
                _statisticsData.value = data
                _isEmpty.value = data.isEmpty()
                
                val heatmap = withContext(Dispatchers.IO) { 
                    repository.getHeatmapData(year, month) 
                }
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
                
                Log.d(TAG, "Month statistics loaded successfully for $year-$month")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load month statistics", e)
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
                _statisticsData.value = StatisticsData.empty()
            } finally {
                _isLoading.value = false
                isLoadingData = false
            }
        }
    }

    fun loadCustomStatistics(startDate: Date, endDate: Date, forceRefresh: Boolean = false) {
        if (isLoadingData && !forceRefresh) {
            Log.d(TAG, "Already loading, skipping custom statistics request")
            return
        }
        
        if (!forceRefresh && cacheType == CacheType.CUSTOM && cachedCustomStart == startDate && cachedCustomEnd == endDate && cachedStatisticsData != null) {
            Log.d(TAG, "Using cached custom statistics")
            _statisticsData.value = cachedStatisticsData!!
            _heatmapData.value = null
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            _isEmpty.value = cachedStatisticsData!!.isEmpty()
            return
        }
        
        viewModelScope.launch(CrashHandler.coroutineExceptionHandler) {
            isLoadingData = true
            _isLoading.value = true
            _errorMessage.value = null
            _heatmapData.value = null
            _yearlyIncomeData.value = null
            _yearlyHeatmapData.value = null
            
            try {
                val data = withContext(Dispatchers.IO) { 
                    repository.getStatisticsByDateRange(startDate, endDate) 
                }
                _statisticsData.value = data
                _isEmpty.value = data.isEmpty()
                
                cacheType = CacheType.CUSTOM
                cachedYear = null
                cachedMonth = null
                cachedCustomStart = startDate
                cachedCustomEnd = endDate
                cachedStatisticsData = data
                cachedHeatmapData = null
                cachedYearlyIncomeData = null
                cachedYearlyHeatmapData = null
                
                Log.d(TAG, "Custom statistics loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load custom statistics", e)
                _errorMessage.value = "加载统计数据失败：${e.message}"
                _isEmpty.value = true
                _statisticsData.value = StatisticsData.empty()
            } finally {
                _isLoading.value = false
                isLoadingData = false
            }
        }
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
        repository.clearCache()
        Log.d(TAG, "Statistics cache cleared")
    }
    
    fun refreshCurrentData() {
        val currentCacheType = cacheType
        val year = cachedYear
        val month = cachedMonth
        val customStart = cachedCustomStart
        val customEnd = cachedCustomEnd
        
        Log.d(TAG, "Refreshing current data, cacheType: $currentCacheType")
        
        cacheType = CacheType.NONE
        cachedStatisticsData = null
        cachedHeatmapData = null
        cachedYearlyIncomeData = null
        cachedYearlyHeatmapData = null
        repository.clearCache()
        
        when (currentCacheType) {
            CacheType.YEAR -> year?.let { loadYearStatistics(it, true) }
            CacheType.MONTH -> {
                year?.let { y ->
                    month?.let { m ->
                        loadMonthStatistics(y, m, true)
                    }
                }
            }
            CacheType.CUSTOM -> {
                customStart?.let { start ->
                    customEnd?.let { end ->
                        loadCustomStatistics(start, end, true)
                    }
                }
            }
            else -> {}
        }
    }
}
