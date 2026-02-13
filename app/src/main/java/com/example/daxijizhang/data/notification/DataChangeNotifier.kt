package com.example.daxijizhang.data.notification

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.atomic.AtomicLong

object DataChangeNotifier {
    private const val TAG = "DataChangeNotifier"
    
    private val _dataVersion = MutableLiveData(0L)
    val dataVersion: LiveData<Long> = _dataVersion
    
    private val versionCounter = AtomicLong(0)
    
    private val _lastChangeType = MutableLiveData<ChangeType?>()
    val lastChangeType: LiveData<ChangeType?> = _lastChangeType
    
    enum class ChangeType {
        BILL_ADDED,
        BILL_UPDATED,
        BILL_DELETED,
        PAYMENT_ADDED,
        PAYMENT_UPDATED,
        PAYMENT_DELETED,
        DATA_IMPORTED,
        DATA_SYNCED
    }
    
    fun notifyBillAdded() {
        notifyChange(ChangeType.BILL_ADDED)
    }
    
    fun notifyBillUpdated() {
        notifyChange(ChangeType.BILL_UPDATED)
    }
    
    fun notifyBillDeleted() {
        notifyChange(ChangeType.BILL_DELETED)
    }
    
    fun notifyPaymentAdded() {
        notifyChange(ChangeType.PAYMENT_ADDED)
    }
    
    fun notifyPaymentUpdated() {
        notifyChange(ChangeType.PAYMENT_UPDATED)
    }
    
    fun notifyPaymentDeleted() {
        notifyChange(ChangeType.PAYMENT_DELETED)
    }
    
    fun notifyDataImported() {
        notifyChange(ChangeType.DATA_IMPORTED)
    }
    
    fun notifyDataSynced() {
        notifyChange(ChangeType.DATA_SYNCED)
    }
    
    private fun notifyChange(type: ChangeType) {
        val newVersion = versionCounter.incrementAndGet()
        _lastChangeType.postValue(type)
        _dataVersion.postValue(newVersion)
        Log.d(TAG, "Data changed: $type, version: $newVersion")
    }
    
    fun getCurrentVersion(): Long {
        return versionCounter.get()
    }
}
