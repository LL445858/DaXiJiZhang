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
            phase = "一",
            buildingNumber = "5",
            roomNumber = "1201"
        )

        assertEquals("阳光花园 一期 5栋1201", bill.getDisplayAddress())
    }

    @Test
    fun `getDisplayAddress skips blank fields`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            phase = "",
            buildingNumber = "5",
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

        assertEquals("待结清，剩余¥700.00", bill.getPaymentStatus())
    }

    @Test
    fun `getPaymentStatus returns waived status when waivedAmount is positive`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 950.0,
            waivedAmount = 50.0
        )

        assertEquals("已结清，抹零¥50.00", bill.getPaymentStatus())
    }

    @Test
    fun `getPaymentStatus returns overpaid status when paid more than total`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 1050.0
        )

        assertEquals("已结清，多收¥50.00", bill.getPaymentStatus())
    }

    @Test
    fun `isPaid returns true when overpaid`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 1050.0
        )

        assertTrue(bill.isPaid())
    }

    @Test
    fun `isPaid returns true when waived`() {
        val bill = Bill(
            id = 1,
            startDate = Date(),
            endDate = Date(),
            communityName = "阳光花园",
            totalAmount = 1000.0,
            paidAmount = 950.0,
            waivedAmount = 50.0
        )

        assertTrue(bill.isPaid())
    }
}
