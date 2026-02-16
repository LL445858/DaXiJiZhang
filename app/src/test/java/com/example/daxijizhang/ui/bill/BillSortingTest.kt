package com.example.daxijizhang.ui.bill

import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.util.PinyinUtil
import org.junit.Test
import java.util.Date

class BillSortingTest {

    @Test
    fun testBillCommunitySorting() {
        val bills = listOf(
            createBill("曲阳名邸", Date(100)),
            createBill("陈帐", Date(200)),
            createBill("黎明左岸", Date(300))
        )
        
        println("=== 原始账单列表 ===")
        bills.forEach { println("${it.communityName} - startDate: ${it.startDate.time}") }
        
        val sortedBills = bills.sortedWith { b1, b2 ->
            val cmp = PinyinUtil.compareForSort(b1.communityName, b2.communityName)
            if (cmp != 0) cmp else b2.startDate.compareTo(b1.startDate)
        }
        
        println("\n=== 按小区名拼音排序后 ===")
        sortedBills.forEach { println("${it.communityName}") }
        
        println("\n预期顺序: 陈帐 < 黎明左岸 < 曲阳名邸")
        println("实际顺序: ${sortedBills.map { it.communityName }.joinToString(" < ")}")
    }
    
    @Test
    fun testBillCommunitySortingWithSameCommunity() {
        val bills = listOf(
            createBill("陈帐", Date(300)),
            createBill("陈帐", Date(100)),
            createBill("陈帐", Date(200))
        )
        
        println("=== 同一小区，不同开始日期 ===")
        bills.forEach { println("${it.communityName} - startDate: ${it.startDate.time}") }
        
        val sortedBills = bills.sortedWith { b1, b2 ->
            val cmp = PinyinUtil.compareForSort(b1.communityName, b2.communityName)
            if (cmp != 0) cmp else b2.startDate.compareTo(b1.startDate)
        }
        
        println("\n=== 排序后（同小区按开始日期降序）===")
        sortedBills.forEach { println("${it.communityName} - startDate: ${it.startDate.time}") }
    }
    
    @Test
    fun testMixedChineseEnglishSorting() {
        val communityNames = listOf(
            "Zoo小区", "Apple花园", "曲阳名邸", "陈帐", "123小区", "黎明左岸"
        )
        
        println("=== 混合中英文排序 ===")
        println("原始顺序: ${communityNames.joinToString(", ")}")
        
        val sorted = communityNames.sortedWith { s1, s2 ->
            PinyinUtil.compareForSort(s1, s2)
        }
        
        println("排序后: ${sorted.joinToString(", ")}")
    }
    
    private fun createBill(communityName: String, startDate: Date): Bill {
        return Bill(
            id = 0,
            communityName = communityName,
            phase = null,
            buildingNumber = null,
            roomNumber = null,
            startDate = startDate,
            endDate = Date(startDate.time + 86400000),
            totalAmount = 100.0,
            paidAmount = 0.0,
            waivedAmount = 0.0,
            remark = null,
            createdAt = startDate
        )
    }
}
