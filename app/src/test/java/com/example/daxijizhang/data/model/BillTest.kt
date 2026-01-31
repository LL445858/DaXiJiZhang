package com.example.daxijizhang.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class BillTest {

    @Test
    fun `getDisplayAddress returns community name only when other fields are null`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            phase = null,
            buildingNumber = null,
            roomNumber = null
        )

        assertEquals("阳光花园", bill.getDisplayAddress())
    }

    @Test
    fun `getDisplayAddress returns full address when all fields are present`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            phase = "一期",
            buildingNumber = "5栋",
            roomNumber = "1201"
        )

        assertEquals("阳光花园 一期 5栋 1201", bill.getDisplayAddress())
    }

    @Test
    fun `getDisplayAddress skips blank fields`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            phase = "",
            buildingNumber = "5栋",
            roomNumber = ""
        )

        assertEquals("阳光花园 5栋", bill.getDisplayAddress())
    }

    @Test
    fun `isPaid returns true when fully paid`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 1000.0
        )

        assertTrue(bill.isPaid())
    }

    @Test
    fun `isPaid returns true when paid with small floating point difference`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 999.99
        )

        assertTrue(bill.isPaid())
    }

    @Test
    fun `isPaid returns false when not fully paid`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 500.0
        )

        assertFalse(bill.isPaid())
    }

    @Test
    fun `getPaymentStatus returns paid status when fully paid`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 1000.0
        )

        assertEquals("已结清", bill.getPaymentStatus())
    }

    @Test
    fun `getPaymentStatus returns remaining amount when not fully paid`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 300.0
        )

        assertEquals("剩余¥700.00未结清", bill.getPaymentStatus())
    }
}
