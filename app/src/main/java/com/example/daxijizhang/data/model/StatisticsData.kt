package com.example.daxijizhang.data.model

import java.util.Date

data class StatisticsData(
    val periodDays: Int = 0,
    val startedProjects: Int = 0,
    val endedProjects: Int = 0,
    val completedProjects: Int = 0,
    val averageDays: Double = 0.0,
    val totalPayments: Int = 0,
    val totalPaymentAmount: Double = 0.0,
    val topPayments: List<PaymentWithBillInfo> = emptyList()
) {
    companion object {
        fun empty(): StatisticsData = StatisticsData()
    }
    
    fun isEmpty(): Boolean {
        return startedProjects == 0 &&
                endedProjects == 0 &&
                completedProjects == 0 &&
                totalPayments == 0 &&
                topPayments.isEmpty()
    }
}

data class PaymentWithBillInfo(
    val paymentId: Long,
    val paymentDate: Date,
    val amount: Double,
    val billId: Long,
    val communityName: String,
    val phase: String?,
    val buildingNumber: String?,
    val roomNumber: String?
) {
    fun getFormattedAddress(): String {
        val sb = StringBuilder()
        sb.append(communityName)
        if (!phase.isNullOrBlank()) {
            sb.append(" ").append(phase).append("期")
        }
        if (!buildingNumber.isNullOrBlank()) {
            sb.append(" ").append(buildingNumber).append("栋")
        }
        if (!roomNumber.isNullOrBlank()) {
            sb.append(roomNumber)
        }
        return sb.toString()
    }
}
