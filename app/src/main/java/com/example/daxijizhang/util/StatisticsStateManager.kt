package com.example.daxijizhang.util

import android.content.Context
import com.example.daxijizhang.DaxiApplication
import com.example.daxijizhang.data.model.PeriodType
import java.util.Calendar

object StatisticsStateManager {

    var currentPeriodType: PeriodType = PeriodType.MONTH

    var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    var customStartDate: Calendar = Calendar.getInstance()
    var customEndDate: Calendar = Calendar.getInstance()

    private var isInitialized: Boolean = false
    private var isCustomDateUserModified: Boolean = false

    private const val PREFS_NAME = "statistics_settings"
    private const val KEY_DEFAULT_PERIOD_TYPE = "default_period_type"
    private const val DEFAULT_PERIOD_TYPE_VALUE = "MONTH"
    private const val KEY_DEFAULT_SORT_TYPE = "default_sort_type"
    private const val DEFAULT_SORT_TYPE_VALUE = "START_DATE_DESC"

    private fun getContext(): Context {
        return DaxiApplication.getInstance()
    }

    private fun getPrefs() = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDefaultPeriodType(): PeriodType {
        val typeString = getPrefs().getString(KEY_DEFAULT_PERIOD_TYPE, DEFAULT_PERIOD_TYPE_VALUE) ?: DEFAULT_PERIOD_TYPE_VALUE
        return when (typeString) {
            "YEAR" -> PeriodType.YEAR
            "CUSTOM" -> PeriodType.CUSTOM
            else -> PeriodType.MONTH
        }
    }

    fun setDefaultPeriodType(periodType: PeriodType) {
        val typeString = when (periodType) {
            PeriodType.MONTH -> "MONTH"
            PeriodType.YEAR -> "YEAR"
            PeriodType.CUSTOM -> "CUSTOM"
        }
        getPrefs().edit().putString(KEY_DEFAULT_PERIOD_TYPE, typeString).apply()
    }

    fun getDefaultSortType(): String {
        return getPrefs().getString(KEY_DEFAULT_SORT_TYPE, DEFAULT_SORT_TYPE_VALUE) ?: DEFAULT_SORT_TYPE_VALUE
    }

    fun setDefaultSortType(sortType: String) {
        getPrefs().edit().putString(KEY_DEFAULT_SORT_TYPE, sortType).apply()
    }

    fun initIfNeeded() {
        if (!isInitialized) {
            resetToDefault()
            isInitialized = true
        }
    }

    fun resetToDefault() {
        currentPeriodType = getDefaultPeriodType()
        selectedYear = Calendar.getInstance().get(Calendar.YEAR)
        selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        isCustomDateUserModified = false
        setDefaultCustomDateRange()
    }

    private fun setDefaultCustomDateRange() {
        val today = Calendar.getInstance()
        val endDate = today.clone() as Calendar
        
        val startDate = today.clone() as Calendar
        startDate.add(Calendar.MONTH, -1)
        
        val targetDay = today.get(Calendar.DAY_OF_MONTH)
        val lastDayOfStartMonth = startDate.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        if (targetDay > lastDayOfStartMonth) {
            startDate.set(Calendar.DAY_OF_MONTH, lastDayOfStartMonth)
        }
        
        startDate.set(Calendar.HOUR_OF_DAY, 0)
        startDate.set(Calendar.MINUTE, 0)
        startDate.set(Calendar.SECOND, 0)
        startDate.set(Calendar.MILLISECOND, 0)
        
        endDate.set(Calendar.HOUR_OF_DAY, 23)
        endDate.set(Calendar.MINUTE, 59)
        endDate.set(Calendar.SECOND, 59)
        endDate.set(Calendar.MILLISECOND, 999)
        
        customStartDate = startDate
        customEndDate = endDate
    }

    fun ensureCustomDateInitialized() {
        if (!isCustomDateUserModified) {
            setDefaultCustomDateRange()
        }
    }

    fun saveMonthState(year: Int, month: Int) {
        currentPeriodType = PeriodType.MONTH
        selectedYear = year
        selectedMonth = month
    }

    fun saveYearState(year: Int) {
        currentPeriodType = PeriodType.YEAR
        selectedYear = year
    }

    fun saveCustomState(startDate: Calendar, endDate: Calendar) {
        currentPeriodType = PeriodType.CUSTOM
        customStartDate = startDate.clone() as Calendar
        customEndDate = endDate.clone() as Calendar
        isCustomDateUserModified = true
    }
}
