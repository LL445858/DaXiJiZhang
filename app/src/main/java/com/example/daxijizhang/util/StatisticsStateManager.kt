package com.example.daxijizhang.util

import com.example.daxijizhang.data.model.PeriodType
import java.util.Calendar

/**
 * 统计页面状态管理器
 * 用于在页面切换时保持时间范围选择状态
 * 单例模式，应用退出后状态重置
 */
object StatisticsStateManager {

    // 当前统计周期类型
    var currentPeriodType: PeriodType = PeriodType.MONTH

    // 选中的年份和月份
    var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    var selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    // 自定义日期范围
    var customStartDate: Calendar = Calendar.getInstance()
    var customEndDate: Calendar = Calendar.getInstance()

    // 是否已初始化
    private var isInitialized: Boolean = false

    /**
     * 初始化默认状态（如果未初始化）
     */
    fun initIfNeeded() {
        if (!isInitialized) {
            resetToDefault()
            isInitialized = true
        }
    }

    /**
     * 重置为默认状态
     */
    fun resetToDefault() {
        currentPeriodType = PeriodType.MONTH
        selectedYear = Calendar.getInstance().get(Calendar.YEAR)
        selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        customStartDate = Calendar.getInstance()
        customEndDate = Calendar.getInstance()
    }

    /**
     * 保存月统计状态
     */
    fun saveMonthState(year: Int, month: Int) {
        currentPeriodType = PeriodType.MONTH
        selectedYear = year
        selectedMonth = month
    }

    /**
     * 保存年统计状态
     */
    fun saveYearState(year: Int) {
        currentPeriodType = PeriodType.YEAR
        selectedYear = year
    }

    /**
     * 保存自定义日期状态
     */
    fun saveCustomState(startDate: Calendar, endDate: Calendar) {
        currentPeriodType = PeriodType.CUSTOM
        customStartDate = startDate.clone() as Calendar
        customEndDate = endDate.clone() as Calendar
    }
}
