package com.example.daxijizhang.data.repository

import android.util.Log
import com.example.daxijizhang.data.cache.DataCacheManager
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.HeatmapData
import com.example.daxijizhang.data.model.PaymentWithBillInfo
import com.example.daxijizhang.data.model.StatisticsData
import com.example.daxijizhang.ui.view.YearlyHeatmapData
import com.example.daxijizhang.ui.view.YearlyIncomeData
import com.example.daxijizhang.util.InputValidator
import com.example.daxijizhang.util.MemoryGuard
import com.example.daxijizhang.util.SafeExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class StatisticsRepository(private val database: AppDatabase) {
    
    private val TAG = "StatisticsRepository"
    private val cacheManager = DataCacheManager
    private val calendarLock = Any()

    suspend fun getStatisticsByDateRange(startDate: Date, endDate: Date): StatisticsData =
        withContext(Dispatchers.IO) {
            SafeExecutor.runSafely("getStatisticsByDateRange", StatisticsData.empty()) {
                InputValidator.validateDateRange(startDate, endDate).getOrThrow()
                
                val cacheKey = cacheManager.generateStatisticsKey(startDate, endDate)
                cacheManager.getStatistics(cacheKey)?.let { 
                    Log.d(TAG, "Statistics cache hit for key: $cacheKey")
                    return@runSafely it 
                }
                
                if (MemoryGuard.isLowMemory()) {
                    Log.w(TAG, "Low memory detected, clearing old caches")
                    cacheManager.clearStatisticsCaches()
                }
                
                val billDao = database.billDao()
                val paymentDao = database.paymentRecordDao()

                val allBills = billDao.getAllList()

                val periodDays = calculateDaysBetween(startDate, endDate)

                val startedProjects = allBills.count { bill ->
                    !bill.startDate.before(startDate) && !bill.startDate.after(endDate)
                }

                val endedProjects = allBills.count { bill ->
                    !bill.endDate.before(startDate) && !bill.endDate.after(endDate)
                }

                val completedProjects = allBills.count { bill ->
                    !bill.startDate.before(startDate) && !bill.startDate.after(endDate) &&
                    !bill.endDate.before(startDate) && !bill.endDate.after(endDate)
                }

                val completedBills = allBills.filter { bill ->
                    !bill.startDate.before(startDate) && !bill.startDate.after(endDate) &&
                    !bill.endDate.before(startDate) && !bill.endDate.after(endDate)
                }

                val averageDays = if (completedBills.isNotEmpty()) {
                    val totalDays = completedBills.sumOf { bill ->
                        calculateDaysBetween(bill.startDate, bill.endDate).toDouble()
                    }
                    totalDays / completedBills.size
                } else {
                    0.0
                }

                val allPaymentsWithBillInfo = mutableListOf<PaymentWithBillInfo>()
                var totalPaymentAmount = 0.0

                try {
                    val paymentsWithInfo = paymentDao.getPaymentsWithBillInfoByDateRange(
                        startDate.time,
                        endDate.time
                    )
                    
                    for (payment in paymentsWithInfo) {
                        allPaymentsWithBillInfo.add(payment)
                        totalPaymentAmount += payment.amount
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching payments with JOIN query", e)
                }

                val topPayments = allPaymentsWithBillInfo
                    .sortedByDescending { it.amount }
                    .take(10)

                val result = StatisticsData(
                    periodDays = periodDays,
                    startedProjects = startedProjects,
                    endedProjects = endedProjects,
                    completedProjects = completedProjects,
                    averageDays = averageDays,
                    totalPayments = allPaymentsWithBillInfo.size,
                    totalPaymentAmount = totalPaymentAmount,
                    topPayments = topPayments
                )
                
                cacheManager.putStatistics(cacheKey, result)
                Log.d(TAG, "Statistics cached for key: $cacheKey")
                
                result
            }
        }

    private fun calculateDaysBetween(startDate: Date, endDate: Date): Int {
        synchronized(calendarLock) {
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startMillis = calendar.timeInMillis

            calendar.time = endDate
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val endMillis = calendar.timeInMillis

            val diffInMillis = endMillis - startMillis
            return TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS).toInt() + 1
        }
    }

    suspend fun getStatisticsByYear(year: Int): StatisticsData {
        return SafeExecutor.runSafely("getStatisticsByYear", StatisticsData.empty()) {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (year < 2000 || year > currentYear + 1) {
                Log.w(TAG, "Invalid year: $year, using current year")
                throw IllegalArgumentException("无效的年份: $year")
            }
            
            val calendar = Calendar.getInstance()

            calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            calendar.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endDate = calendar.time

            getStatisticsByDateRange(startDate, endDate)
        }
    }

    suspend fun getStatisticsByMonth(year: Int, month: Int): StatisticsData {
        return SafeExecutor.runSafely("getStatisticsByMonth", StatisticsData.empty()) {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (year < 2000 || year > currentYear + 1) {
                Log.w(TAG, "Invalid year: $year")
                throw IllegalArgumentException("无效的年份: $year")
            }
            if (month < 1 || month > 12) {
                Log.w(TAG, "Invalid month: $month")
                throw IllegalArgumentException("无效的月份: $month")
            }
            
            val calendar = Calendar.getInstance()

            calendar.set(year, month - 1, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            calendar.set(year, month - 1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endDate = calendar.time

            getStatisticsByDateRange(startDate, endDate)
        }
    }

    suspend fun getHeatmapData(year: Int, month: Int): HeatmapData = withContext(Dispatchers.IO) {
        SafeExecutor.runSafely("getHeatmapData", HeatmapData(year, month, emptyMap(), 0)) {
            if (month < 1 || month > 12) {
                throw IllegalArgumentException("无效的月份: $month")
            }
            
            val cacheKey = cacheManager.generateHeatmapKey(year, month)
            cacheManager.getHeatmap(cacheKey)?.let { 
                Log.d(TAG, "Heatmap cache hit for key: $cacheKey")
                return@runSafely it 
            }
            
            val billDao = database.billDao()
            val allBills = billDao.getAllList()

            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val monthStart = calendar.time

            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            val dayCounts = mutableMapOf<Int, Int>()

            for (day in 1..daysInMonth) {
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.time

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val dayEnd = calendar.time

                val count = allBills.count { bill ->
                    !bill.startDate.after(dayEnd) && !bill.endDate.before(dayStart)
                }

                if (count > 0) {
                    dayCounts[day] = count
                }
            }

            val maxCount = if (dayCounts.isNotEmpty()) dayCounts.values.maxOrNull() ?: 0 else 0

            val result = HeatmapData(
                year = year,
                month = month,
                dayCounts = dayCounts,
                maxCount = maxCount
            )
            
            cacheManager.putHeatmap(cacheKey, result)
            Log.d(TAG, "Heatmap cached for key: $cacheKey")
            
            result
        }
    }

    suspend fun getYearlyIncomeData(year: Int): YearlyIncomeData = withContext(Dispatchers.IO) {
        SafeExecutor.runSafely("getYearlyIncomeData", YearlyIncomeData(year, emptyMap())) {
            if (year < 2000 || year > Calendar.getInstance().get(Calendar.YEAR) + 1) {
                throw IllegalArgumentException("无效的年份: $year")
            }
            
            cacheManager.getYearlyIncome(year)?.let { 
                Log.d(TAG, "YearlyIncome cache hit for year: $year")
                return@runSafely it 
            }
            
            val paymentDao = database.paymentRecordDao()
            
            val calendar = Calendar.getInstance()
            calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val yearStart = calendar.time.time
            
            calendar.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val yearEnd = calendar.time.time
            
            val monthlyIncomes = mutableMapOf<Int, Double>()
            
            try {
                val monthlyPayments = paymentDao.getMonthlyPaymentsByYear(yearStart, yearEnd)
                for (monthlyPayment in monthlyPayments) {
                    val month = monthlyPayment.month.toIntOrNull()
                    if (month != null && monthlyPayment.total > 0) {
                        monthlyIncomes[month] = monthlyPayment.total
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching monthly payments with JOIN query", e)
            }
            
            val result = YearlyIncomeData(year = year, monthlyIncomes = monthlyIncomes)
            cacheManager.putYearlyIncome(year, result)
            Log.d(TAG, "YearlyIncome cached for year: $year")
            
            result
        }
    }

    suspend fun getYearlyHeatmapData(year: Int): YearlyHeatmapData = withContext(Dispatchers.IO) {
        SafeExecutor.runSafely("getYearlyHeatmapData", YearlyHeatmapData(year, emptyMap())) {
            if (year < 2000 || year > Calendar.getInstance().get(Calendar.YEAR) + 1) {
                throw IllegalArgumentException("无效的年份: $year")
            }
            
            cacheManager.getYearlyHeatmap(year)?.let { 
                Log.d(TAG, "YearlyHeatmap cache hit for year: $year")
                return@runSafely it 
            }
            
            val monthlyHeatmaps = mutableMapOf<Int, HeatmapData>()
            
            for (month in 1..12) {
                monthlyHeatmaps[month] = getHeatmapData(year, month)
            }
            
            val result = YearlyHeatmapData(year = year, monthlyHeatmaps = monthlyHeatmaps)
            cacheManager.putYearlyHeatmap(year, result)
            Log.d(TAG, "YearlyHeatmap cached for year: $year")
            
            result
        }
    }
    
    fun clearCache() {
        cacheManager.clearStatisticsCaches()
        Log.d(TAG, "Statistics caches cleared")
    }
}
