package com.example.daxijizhang.data.model

data class HeatmapData(
    val year: Int,
    val month: Int,
    val dayCounts: Map<Int, Int>,
    val maxCount: Int
) {
    fun getCount(day: Int): Int {
        return dayCounts[day] ?: 0
    }
}
