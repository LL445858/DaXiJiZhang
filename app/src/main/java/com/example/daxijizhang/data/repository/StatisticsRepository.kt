package com.example.daxijizhang.data.repository

import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.PaymentWithBillInfo
import com.example.daxijizhang.data.model.StatisticsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class StatisticsRepository(private val database: AppDatabase) {

    /**
     * 获取指定日期范围内的统计数据
     *
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 统计数据
     */
    suspend fun getStatisticsByDateRange(startDate: Date, endDate: Date): StatisticsData =
        withContext(Dispatchers.IO) {
            val billDao = database.billDao()
            val paymentDao = database.paymentRecordDao()

            // 获取所有账单
            val allBills = billDao.getAllList()

            // 计算统计期间天数（包含起始和结束当天）
            val periodDays = calculateDaysBetween(startDate, endDate)

            // 统计开始的项目（开始日期在范围内）
            val startedProjects = allBills.count { bill ->
                !bill.startDate.before(startDate) && !bill.startDate.after(endDate)
            }

            // 统计结束的项目（结束日期在范围内）
            val endedProjects = allBills.count { bill ->
                !bill.endDate.before(startDate) && !bill.endDate.after(endDate)
            }

            // 统计完成的项目（开始和结束都在范围内）
            val completedProjects = allBills.count { bill ->
                !bill.startDate.before(startDate) && !bill.startDate.after(endDate) &&
                !bill.endDate.before(startDate) && !bill.endDate.after(endDate)
            }

            // 计算平均每家用时（针对完成的项目）
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

            // 获取所有支付记录并关联账单信息
            val allPaymentsWithBillInfo = mutableListOf<PaymentWithBillInfo>()
            var totalPaymentAmount = 0.0

            allBills.forEach { bill ->
                val payments = paymentDao.getByBillIdList(bill.id)
                payments.forEach { payment ->
                    // 检查支付日期是否在范围内
                    if (!payment.paymentDate.before(startDate) && !payment.paymentDate.after(endDate)) {
                        allPaymentsWithBillInfo.add(
                            PaymentWithBillInfo(
                                paymentId = payment.id,
                                paymentDate = payment.paymentDate,
                                amount = payment.amount,
                                billId = bill.id,
                                communityName = bill.communityName,
                                phase = bill.phase,
                                buildingNumber = bill.buildingNumber,
                                roomNumber = bill.roomNumber
                            )
                        )
                        totalPaymentAmount += payment.amount
                    }
                }
            }

            // 按金额降序排序，取前10条
            val topPayments = allPaymentsWithBillInfo
                .sortedByDescending { it.amount }
                .take(10)

            StatisticsData(
                periodDays = periodDays,
                startedProjects = startedProjects,
                endedProjects = endedProjects,
                completedProjects = completedProjects,
                averageDays = averageDays,
                totalPayments = allPaymentsWithBillInfo.size,
                totalPaymentAmount = totalPaymentAmount,
                topPayments = topPayments
            )
        }

    /**
     * 计算两个日期之间的天数（包含起始和结束当天）
     */
    private fun calculateDaysBetween(startDate: Date, endDate: Date): Int {
        val startCal = Calendar.getInstance().apply { time = startDate }
        val endCal = Calendar.getInstance().apply { time = endDate }

        // 清除时间部分
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        endCal.set(Calendar.HOUR_OF_DAY, 0)
        endCal.set(Calendar.MINUTE, 0)
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)

        val diffInMillis = endCal.timeInMillis - startCal.timeInMillis
        return TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS).toInt() + 1
    }

    /**
     * 获取年份统计数据
     *
     * @param year 年份
     * @return 统计数据
     */
    suspend fun getStatisticsByYear(year: Int): StatisticsData {
        val calendar = Calendar.getInstance()

        // 设置开始日期为当年1月1日
        calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // 设置结束日期为当年12月31日
        calendar.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return getStatisticsByDateRange(startDate, endDate)
    }

    /**
     * 获取月份统计数据
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @return 统计数据
     */
    suspend fun getStatisticsByMonth(year: Int, month: Int): StatisticsData {
        val calendar = Calendar.getInstance()

        // 设置开始日期为当月1日
        calendar.set(year, month - 1, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // 设置结束日期为当月最后一日
        calendar.set(year, month - 1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return getStatisticsByDateRange(startDate, endDate)
    }
}
