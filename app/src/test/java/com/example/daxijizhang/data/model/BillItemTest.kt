package com.example.daxijizhang.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class BillItemTest {

    @Test
    fun `calculateTotalPrice returns correct value`() {
        val unitPrice = 100.0
        val quantity = 5.0
        val expected = 500.0

        val result = BillItem.calculateTotalPrice(unitPrice, quantity)

        assertEquals(expected, result, 0.01)
    }

    @Test
    fun `calculateTotalPrice handles decimal values`() {
        val unitPrice = 99.99
        val quantity = 3.5
        val expected = 349.965

        val result = BillItem.calculateTotalPrice(unitPrice, quantity)

        assertEquals(expected, result, 0.001)
    }

    @Test
    fun `bill item calculates total price correctly`() {
        val item = BillItem(
            id = 1,
            billId = 1,
            projectName = "地砖",
            unitPrice = 80.0,
            quantity = 50.0
        )

        assertEquals(4000.0, item.totalPrice, 0.01)
    }

    @Test
    fun `bill item handles zero quantity`() {
        val item = BillItem(
            id = 1,
            billId = 1,
            projectName = "地砖",
            unitPrice = 80.0,
            quantity = 0.0
        )

        assertEquals(0.0, item.totalPrice, 0.01)
    }

    @Test
    fun `bill item handles zero unit price`() {
        val item = BillItem(
            id = 1,
            billId = 1,
            projectName = "地砖",
            unitPrice = 0.0,
            quantity = 50.0
        )

        assertEquals(0.0, item.totalPrice, 0.01)
    }
}
