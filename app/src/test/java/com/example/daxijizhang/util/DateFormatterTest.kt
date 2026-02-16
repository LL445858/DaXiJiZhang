package com.example.daxijizhang.util

import org.junit.Test
import org.junit.Assert.*
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DateFormatterTest {

    @Test
    fun testFormatDate() {
        val date = Date(1609459200000L)
        val result = DateFormatter.formatDate(date)
        assertNotNull(result)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun testFormatDateWithNull() {
        val result = DateFormatter.formatDate(null)
        assertEquals("", result)
    }

    @Test
    fun testFormatDateTime() {
        val date = Date(1609459200000L)
        val result = DateFormatter.formatDateTime(date)
        assertNotNull(result)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun testFormatDateTimeWithNull() {
        val result = DateFormatter.formatDateTime(null)
        assertEquals("", result)
    }

    @Test
    fun testFormatFileTimestamp() {
        val date = Date(1609459200000L)
        val result = DateFormatter.formatFileTimestamp(date)
        assertNotNull(result)
        assertTrue(result.matches(Regex("\\d{8}_\\d{6}")))
    }

    @Test
    fun testFormatCrashLogTimestamp() {
        val date = Date(1609459200000L)
        val result = DateFormatter.formatCrashLogTimestamp(date)
        assertNotNull(result)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")))
    }

    @Test
    fun testFormatShortDate() {
        val date = Date(1609459200000L)
        val result = DateFormatter.formatShortDate(date)
        assertNotNull(result)
        assertTrue(result.matches(Regex("\\d{2}-\\d{2}")))
    }

    @Test
    fun testFormatShortDateTime() {
        val date = Date(1609459200000L)
        val result = DateFormatter.formatShortDateTime(date)
        assertNotNull(result)
        assertTrue(result.matches(Regex("\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun testParseDate() {
        val dateString = "2021-01-01"
        val result = DateFormatter.parseDate(dateString)
        assertNotNull(result)
    }

    @Test
    fun testParseDateWithNull() {
        val result = DateFormatter.parseDate(null)
        assertNull(result)
    }

    @Test
    fun testParseDateWithBlank() {
        val result = DateFormatter.parseDate("")
        assertNull(result)
    }

    @Test
    fun testParseDateTime() {
        val dateString = "2021-01-01 12:00:00"
        val result = DateFormatter.parseDateTime(dateString)
        assertNotNull(result)
    }

    @Test
    fun testParseDateTimeWithDateOnly() {
        val dateString = "2021-01-01"
        val result = DateFormatter.parseDateTime(dateString)
        assertNotNull(result)
    }

    @Test
    fun testFormatDateRange() {
        val startDate = Date(1609459200000L)
        val endDate = Date(1609545600000L)
        val result = DateFormatter.formatDateRange(startDate, endDate)
        assertNotNull(result)
        assertTrue(result.contains("至"))
    }

    @Test
    fun testFormatDateRangeWithNull() {
        val result = DateFormatter.formatDateRange(null, Date())
        assertEquals("", result)
        
        val result2 = DateFormatter.formatDateRange(Date(), null)
        assertEquals("", result2)
    }

    @Test
    fun testFormatAmount() {
        val result = DateFormatter.formatAmount(1234.56)
        assertTrue(result.contains("¥"))
        assertTrue(result.contains("1,234.56"))
    }

    @Test
    fun testThreadSafety() {
        val threadCount = 10
        val iterations = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)
        val date = Date()
        
        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (j in 0 until iterations) {
                        val formatted = DateFormatter.formatDate(date)
                        if (!formatted.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            errorCount.incrementAndGet()
                        }
                        
                        val timestamp = DateFormatter.formatFileTimestamp(date)
                        if (!timestamp.matches(Regex("\\d{8}_\\d{6}"))) {
                            errorCount.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        
        latch.await()
        executor.shutdown()
        
        assertEquals("Thread safety test failed with errors", 0, errorCount.get())
    }

    @Test
    fun testConcurrentDifferentFormats() {
        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)
        val date = Date()
        
        val tasks = listOf<() -> String>(
            { DateFormatter.formatDate(date) },
            { DateFormatter.formatDateTime(date) },
            { DateFormatter.formatFileTimestamp(date) },
            { DateFormatter.formatCrashLogTimestamp(date) },
            { DateFormatter.formatShortDate(date) }
        )
        
        for (task in tasks) {
            executor.submit {
                try {
                    for (i in 0 until 100) {
                        val result = task()
                        if (result.isEmpty()) {
                            errorCount.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        
        latch.await()
        executor.shutdown()
        
        assertEquals("Concurrent different formats test failed", 0, errorCount.get())
    }

    @Test
    fun testParseWithMultipleFormats() {
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy")
        
        val result1 = DateFormatter.parseWithFormats("2021-01-15", patterns)
        assertNotNull(result1)
        
        val result2 = DateFormatter.parseWithFormats("2021/01/15", patterns)
        assertNotNull(result2)
        
        val result3 = DateFormatter.parseWithFormats("01/15/2021", patterns, Locale.US)
        assertNotNull(result3)
        
        val result4 = DateFormatter.parseWithFormats("invalid-date", patterns)
        assertNull(result4)
    }
}
