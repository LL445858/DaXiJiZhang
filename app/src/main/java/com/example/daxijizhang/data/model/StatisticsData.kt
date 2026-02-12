package com.example.daxijizhang.data.model

import java.util.Date

/**
 * 统计数据模型
 */
data class StatisticsData(
    // 时间范围
    val periodDays: Int = 0,                // 统计期间天数

    // 干活统计
    val startedProjects: Int = 0,           // 开始的项目数（开始日期在范围内）
    val endedProjects: Int = 0,             // 结束的项目数（结束日期在范围内）
    val completedProjects: Int = 0,         // 完成的项目数（开始和结束都在范围内）
    val averageDays: Double = 0.0,          // 平均每家用时（天）
    val totalPayments: Int = 0,             // 支付笔数
    val totalPaymentAmount: Double = 0.0,   // 支付总金额

    // 支付列表（Top10）
    val topPayments: List<PaymentWithBillInfo> = emptyList()
)

/**
 * 支付记录带账单信息
 */
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
    /**
     * 获取格式化的地址信息
     * 格式：{小区名} {期号}期 {楼栋号}栋{门牌号}
     */
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
