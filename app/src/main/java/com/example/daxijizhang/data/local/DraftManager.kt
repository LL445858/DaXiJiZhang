package com.example.daxijizhang.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.PaymentRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

data class BillDraft(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val communityName: String = "",
    val phase: String = "",
    val buildingNumber: String = "",
    val roomNumber: String = "",
    val remark: String = "",
    val items: List<BillItem> = emptyList(),
    val paymentRecords: List<PaymentRecord> = emptyList(),
    val waivedAmount: Double = 0.0
) {
    fun hasContent(): Boolean {
        return communityName.isNotBlank() ||
               phase.isNotBlank() ||
               buildingNumber.isNotBlank() ||
               roomNumber.isNotBlank() ||
               remark.isNotBlank() ||
               items.isNotEmpty() ||
               paymentRecords.isNotEmpty() ||
               startDate != null ||
               endDate != null
    }
}

class DraftManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "bill_draft_prefs"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_END_DATE = "end_date"
        private const val KEY_COMMUNITY = "community"
        private const val KEY_PHASE = "phase"
        private const val KEY_BUILDING = "building"
        private const val KEY_ROOM = "room"
        private const val KEY_REMARK = "remark"
        private const val KEY_ITEMS = "items"
        private const val KEY_PAYMENT_RECORDS = "payment_records"
        private const val KEY_WAIVED_AMOUNT = "waived_amount"
    }

    fun saveDraft(draft: BillDraft) {
        prefs.edit().apply {
            putLong(KEY_START_DATE, draft.startDate ?: -1)
            putLong(KEY_END_DATE, draft.endDate ?: -1)
            putString(KEY_COMMUNITY, draft.communityName)
            putString(KEY_PHASE, draft.phase)
            putString(KEY_BUILDING, draft.buildingNumber)
            putString(KEY_ROOM, draft.roomNumber)
            putString(KEY_REMARK, draft.remark)
            putString(KEY_ITEMS, gson.toJson(draft.items))
            putString(KEY_PAYMENT_RECORDS, gson.toJson(draft.paymentRecords))
            putFloat(KEY_WAIVED_AMOUNT, draft.waivedAmount.toFloat())
            apply()
        }
    }

    fun loadDraft(): BillDraft {
        val startDate = prefs.getLong(KEY_START_DATE, -1).takeIf { it != -1L }
        val endDate = prefs.getLong(KEY_END_DATE, -1).takeIf { it != -1L }

        val itemsJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val itemsType = object : TypeToken<List<BillItem>>() {}.type
        val items: List<BillItem> = gson.fromJson(itemsJson, itemsType) ?: emptyList()

        val paymentJson = prefs.getString(KEY_PAYMENT_RECORDS, "[]") ?: "[]"
        val paymentType = object : TypeToken<List<PaymentRecord>>() {}.type
        val paymentRecords: List<PaymentRecord> = gson.fromJson(paymentJson, paymentType) ?: emptyList()

        return BillDraft(
            startDate = startDate,
            endDate = endDate,
            communityName = prefs.getString(KEY_COMMUNITY, "") ?: "",
            phase = prefs.getString(KEY_PHASE, "") ?: "",
            buildingNumber = prefs.getString(KEY_BUILDING, "") ?: "",
            roomNumber = prefs.getString(KEY_ROOM, "") ?: "",
            remark = prefs.getString(KEY_REMARK, "") ?: "",
            items = items,
            paymentRecords = paymentRecords,
            waivedAmount = prefs.getFloat(KEY_WAIVED_AMOUNT, 0f).toDouble()
        )
    }

    fun clearDraft() {
        prefs.edit().clear().apply()
    }

    fun hasDraft(): Boolean {
        return loadDraft().hasContent()
    }
}
