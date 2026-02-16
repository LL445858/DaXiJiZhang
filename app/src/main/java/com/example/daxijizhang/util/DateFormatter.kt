package com.example.daxijizhang.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {

    private const val DATE_FORMAT_DEFAULT = "yyyy-MM-dd"
    private const val DATE_TIME_FORMAT_DEFAULT = "yyyy-MM-dd HH:mm:ss"
    private const val FILE_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
    private const val CRASH_LOG_FORMAT = "yyyy-MM-dd_HH-mm-ss"
    private const val SHORT_DATE_FORMAT = "MM-dd"
    private const val SHORT_DATETIME_FORMAT = "MM-dd HH:mm"

    private val threadLocalFormats = mutableMapOf<String, ThreadLocal<SimpleDateFormat>>()

    private fun getFormat(pattern: String, locale: Locale = Locale.getDefault()): SimpleDateFormat {
        val key = "${pattern}_${locale.toLanguageTag()}"
        if (!threadLocalFormats.containsKey(key)) {
            threadLocalFormats[key] = ThreadLocal.withInitial {
                SimpleDateFormat(pattern, locale)
            }
        }
        return threadLocalFormats[key]!!.get()!!
    }

    fun formatDate(date: Date?, pattern: String = DATE_FORMAT_DEFAULT, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        return getFormat(pattern, locale).format(date)
    }

    fun formatDate(date: Date?): String {
        if (date == null) return ""
        return getFormat(DATE_FORMAT_DEFAULT).format(date)
    }

    fun formatDateTime(date: Date?): String {
        if (date == null) return ""
        return getFormat(DATE_TIME_FORMAT_DEFAULT).format(date)
    }

    fun formatFileTimestamp(date: Date): String {
        return getFormat(FILE_TIMESTAMP_FORMAT).format(date)
    }

    fun formatCrashLogTimestamp(date: Date): String {
        return getFormat(CRASH_LOG_FORMAT).format(date)
    }

    fun formatShortDate(date: Date?): String {
        if (date == null) return ""
        return getFormat(SHORT_DATE_FORMAT).format(date)
    }

    fun formatShortDateTime(date: Date?): String {
        if (date == null) return ""
        return getFormat(SHORT_DATETIME_FORMAT).format(date)
    }

    fun parseDate(dateString: String?, pattern: String = DATE_FORMAT_DEFAULT, locale: Locale = Locale.getDefault()): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            getFormat(pattern, locale).parse(dateString)
        } catch (e: ParseException) {
            null
        }
    }

    fun parseDateTime(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            getFormat(DATE_TIME_FORMAT_DEFAULT).parse(dateString)
        } catch (e: ParseException) {
            try {
                getFormat(DATE_FORMAT_DEFAULT).parse(dateString)
            } catch (e2: ParseException) {
                null
            }
        }
    }

    fun parseWithFormats(dateString: String, patterns: List<String>, locale: Locale = Locale.US): Date? {
        for (pattern in patterns) {
            try {
                return getFormat(pattern, locale).parse(dateString)
            } catch (e: ParseException) {
                continue
            }
        }
        return null
    }

    fun formatDateRange(startDate: Date?, endDate: Date?): String {
        if (startDate == null || endDate == null) return ""
        return "${formatDate(startDate)} 至 ${formatDate(endDate)}"
    }

    fun formatAmount(amount: Double): String {
        return String.format("¥%,.2f", amount)
    }
}
