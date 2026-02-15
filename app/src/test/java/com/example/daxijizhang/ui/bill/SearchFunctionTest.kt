package com.example.daxijizhang.ui.bill

import com.example.daxijizhang.data.model.Bill
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class SearchFunctionTest {

    private fun sortSearchResults(bills: List<Bill>, query: String): List<Bill> {
        val lowerQuery = query.lowercase()
        return bills.sortedWith(compareBy { bill ->
            val communityMatch = bill.communityName.lowercase().contains(lowerQuery)
            val remarkMatch = !bill.remark.isNullOrBlank() && bill.remark.lowercase().contains(lowerQuery)
            when {
                bill.communityName.lowercase() == lowerQuery -> 0
                communityMatch && remarkMatch -> 1
                communityMatch -> 2
                remarkMatch -> 3
                else -> 4
            }
        })
    }

    private fun matchesSearchQuery(bill: Bill, query: String): Boolean {
        val lowerQuery = query.lowercase()
        val communityMatch = bill.communityName.lowercase().contains(lowerQuery)
        val remarkMatch = !bill.remark.isNullOrBlank() && bill.remark.lowercase().contains(lowerQuery)
        return communityMatch || remarkMatch
    }

    @Test
    fun `matchesSearchQuery returns true when communityName contains query`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "幸福小区",
            remark = null
        )
        assertTrue(matchesSearchQuery(bill, "幸福"))
    }

    @Test
    fun `matchesSearchQuery returns true when remark contains query`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            remark = "我很幸福"
        )
        assertTrue(matchesSearchQuery(bill, "幸福"))
    }

    @Test
    fun `matchesSearchQuery returns true when both fields contain query`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "幸福小区",
            remark = "幸福生活"
        )
        assertTrue(matchesSearchQuery(bill, "幸福"))
    }

    @Test
    fun `matchesSearchQuery returns false when neither field contains query`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            remark = "美好的生活"
        )
        assertFalse(matchesSearchQuery(bill, "幸福"))
    }

    @Test
    fun `matchesSearchQuery is case insensitive`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "XINGFU小区",
            remark = null
        )
        assertTrue(matchesSearchQuery(bill, "xingfu"))
        assertTrue(matchesSearchQuery(bill, "XINGFU"))
        assertTrue(matchesSearchQuery(bill, "Xingfu"))
    }

    @Test
    fun `matchesSearchQuery handles empty remark`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            remark = ""
        )
        assertFalse(matchesSearchQuery(bill, "幸福"))
    }

    @Test
    fun `matchesSearchQuery handles null remark`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            remark = null
        )
        assertFalse(matchesSearchQuery(bill, "幸福"))
    }

    @Test
    fun `sortSearchResults prioritizes exact community name match`() {
        val bills = listOf(
            Bill(id = 1, startDate = Date(), endDate = Date(), communityName = "幸福花园", remark = null),
            Bill(id = 2, startDate = Date(), endDate = Date(), communityName = "幸福", remark = null),
            Bill(id = 3, startDate = Date(), endDate = Date(), communityName = "幸福小区", remark = null)
        )
        val sorted = sortSearchResults(bills, "幸福")
        assertEquals(2L, sorted[0].id)
    }

    @Test
    fun `sortSearchResults prioritizes both fields match over single field match`() {
        val bills = listOf(
            Bill(id = 1, startDate = Date(), endDate = Date(), communityName = "幸福小区", remark = null),
            Bill(id = 2, startDate = Date(), endDate = Date(), communityName = "阳光花园", remark = "幸福生活"),
            Bill(id = 3, startDate = Date(), endDate = Date(), communityName = "幸福家园", remark = "幸福")
        )
        val sorted = sortSearchResults(bills, "幸福")
        assertEquals(3L, sorted[0].id)
    }

    @Test
    fun `sortSearchResults prioritizes community match over remark match`() {
        val bills = listOf(
            Bill(id = 1, startDate = Date(), endDate = Date(), communityName = "阳光花园", remark = "幸福生活"),
            Bill(id = 2, startDate = Date(), endDate = Date(), communityName = "幸福小区", remark = null)
        )
        val sorted = sortSearchResults(bills, "幸福")
        assertEquals(2L, sorted[0].id)
    }

    @Test
    fun `sortSearchResults handles empty list`() {
        val bills = emptyList<Bill>()
        val sorted = sortSearchResults(bills, "幸福")
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun `matchesSearchQuery handles special characters`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光@花园",
            remark = "测试#备注"
        )
        assertTrue(matchesSearchQuery(bill, "@"))
        assertTrue(matchesSearchQuery(bill, "#"))
    }

    @Test
    fun `matchesSearchQuery handles whitespace in query`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光 花园",
            remark = null
        )
        assertTrue(matchesSearchQuery(bill, "阳光 "))
        assertTrue(matchesSearchQuery(bill, " 花园"))
    }

    @Test
    fun `matchesSearchQuery handles partial match`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园小区",
            remark = null
        )
        assertTrue(matchesSearchQuery(bill, "阳光"))
        assertTrue(matchesSearchQuery(bill, "花园"))
        assertTrue(matchesSearchQuery(bill, "小区"))
    }

    @Test
    fun `matchesSearchQuery handles very long query`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            remark = null
        )
        val longQuery = "这是一个非常非常非常非常非常非常长的搜索关键词"
        assertFalse(matchesSearchQuery(bill, longQuery))
    }

    @Test
    fun `matchesSearchQuery handles query with numbers`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园5栋",
            remark = "2024年账单"
        )
        assertTrue(matchesSearchQuery(bill, "5栋"))
        assertTrue(matchesSearchQuery(bill, "2024"))
    }
}
