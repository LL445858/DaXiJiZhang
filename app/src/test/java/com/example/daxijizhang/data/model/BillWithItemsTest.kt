package com.example.daxijizhang.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class BillWithItemsTest {

    @Test
    fun `calculateTotalAmount sums all item prices`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园"
        )

        val items = listOf(
            BillItem(
                id = 1,
                billId = 1,
                projectName = "地砖",
                unitPrice = 100.0,
                quantity = 10.0,
                totalPrice = 1000.0
            ),
            BillItem(
                id = 2,
                billId = 1,
                projectName = "墙面漆",
                unitPrice = 50.0,
                quantity = 20.0,
                totalPrice = 1000.0
            ),
            BillItem(
                id = 3,
                billId = 1,
                projectName = "吊顶",
                unitPrice = 200.0,
                quantity = 5.0,
                totalPrice = 1000.0
            )
        )

        val billWithItems = BillWithItems(bill, items)

        assertEquals(3000.0, billWithItems.calculateTotalAmount(), 0.01)
    }

    @Test
    fun `calculateTotalAmount returns zero for empty items list`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园"
        )

        val billWithItems = BillWithItems(bill, emptyList())

        assertEquals(0.0, billWithItems.calculateTotalAmount(), 0.01)
    }

    @Test
    fun `calculateTotalAmount handles single item`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园"
        )

        val items = listOf(
            BillItem(
                id = 1,
                billId = 1,
                projectName = "地砖",
                unitPrice = 150.0,
                quantity = 10.0,
                totalPrice = 1500.0
            )
        )

        val billWithItems = BillWithItems(bill, items)

        assertEquals(1500.0, billWithItems.calculateTotalAmount(), 0.01)
    }
}
