package com.example.daxijizhang.data.cache

import android.util.Log
import android.util.LruCache
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.HeatmapData
import com.example.daxijizhang.data.model.StatisticsData
import com.example.daxijizhang.ui.view.YearlyHeatmapData
import com.example.daxijizhang.ui.view.YearlyIncomeData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object DataCacheManager {
    
    private const val TAG = "DataCacheManager"
    private const val BILL_CACHE_SIZE = 50
    private const val BILL_WITH_ITEMS_CACHE_SIZE = 30
    private const val STATISTICS_CACHE_SIZE = 20
    private const val HEATMAP_CACHE_SIZE = 24
    private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L
    
    private val isInitialized = AtomicBoolean(false)
    
    private var billCache: LruCache<Long, CacheEntry<Bill>>? = null
    private var billWithItemsCache: LruCache<Long, CacheEntry<BillWithItems>>? = null
    private var statisticsCache: LruCache<String, CacheEntry<StatisticsData>>? = null
    private var heatmapCache: LruCache<String, CacheEntry<HeatmapData>>? = null
    private var yearlyIncomeCache: LruCache<Int, CacheEntry<YearlyIncomeData>>? = null
    private var yearlyHeatmapCache: LruCache<Int, CacheEntry<YearlyHeatmapData>>? = null
    
    private val cacheTimestamps = ConcurrentHashMap<String, AtomicLong>()
    private var scope: CoroutineScope? = null
    private var cleanupJob: Job? = null
    
    private var billsListCache: CacheEntry<List<Bill>>? = null
    private var billsWithItemsListCache: CacheEntry<List<BillWithItems>>? = null
    
    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(): Boolean {
            return System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS
        }
    }
    
    fun initialize() {
        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "DataCacheManager already initialized")
            return
        }
        
        Log.i(TAG, "Initializing DataCacheManager")
        
        billCache = LruCache(BILL_CACHE_SIZE)
        billWithItemsCache = LruCache(BILL_WITH_ITEMS_CACHE_SIZE)
        statisticsCache = LruCache(STATISTICS_CACHE_SIZE)
        heatmapCache = LruCache(HEATMAP_CACHE_SIZE)
        yearlyIncomeCache = LruCache(10)
        yearlyHeatmapCache = LruCache(10)
        
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startPeriodicCleanup()
        
        Log.i(TAG, "DataCacheManager initialized successfully")
    }
    
    private fun ensureInitialized() {
        if (!isInitialized.get()) {
            Log.w(TAG, "DataCacheManager not initialized, initializing now")
            initialize()
        }
    }
    
    private fun startPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = scope?.launch {
            while (true) {
                delay(CACHE_VALIDITY_MS)
                cleanupExpiredEntries()
            }
        }
    }
    
    private fun cleanupExpiredEntries() {
        try {
            billCache?.let { cache ->
                synchronized(cache) {
                    val keysToRemove = cache.snapshot().keys.filter { 
                        cache[it]?.isValid() == false 
                    }
                    keysToRemove.forEach { cache.remove(it) }
                }
            }
            
            billWithItemsCache?.let { cache ->
                synchronized(cache) {
                    val keysToRemove = cache.snapshot().keys.filter { 
                        cache[it]?.isValid() == false 
                    }
                    keysToRemove.forEach { cache.remove(it) }
                }
            }
            
            statisticsCache?.let { cache ->
                synchronized(cache) {
                    val keysToRemove = cache.snapshot().keys.filter { 
                        cache[it]?.isValid() == false 
                    }
                    keysToRemove.forEach { cache.remove(it) }
                }
            }
            
            heatmapCache?.let { cache ->
                synchronized(cache) {
                    val keysToRemove = cache.snapshot().keys.filter { 
                        cache[it]?.isValid() == false 
                    }
                    keysToRemove.forEach { cache.remove(it) }
                }
            }
            
            if (billsListCache?.isValid() == false) {
                billsListCache = null
            }
            if (billsWithItemsListCache?.isValid() == false) {
                billsWithItemsListCache = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
        }
    }
    
    fun putBill(bill: Bill) {
        ensureInitialized()
        billCache?.let { cache ->
            synchronized(cache) {
                cache.put(bill.id, CacheEntry(bill))
            }
        }
        invalidateBillsListCache()
    }
    
    fun getBill(id: Long): Bill? {
        ensureInitialized()
        return billCache?.let { cache ->
            synchronized(cache) {
                cache[id]?.takeIf { it.isValid() }?.data
            }
        }
    }
    
    fun removeBill(id: Long) {
        ensureInitialized()
        billCache?.let { cache ->
            synchronized(cache) {
                cache.remove(id)
            }
        }
        billWithItemsCache?.let { cache ->
            synchronized(cache) {
                cache.remove(id)
            }
        }
        invalidateBillsListCache()
    }
    
    fun putBillWithItems(billWithItems: BillWithItems) {
        ensureInitialized()
        billWithItemsCache?.let { cache ->
            synchronized(cache) {
                cache.put(billWithItems.bill.id, CacheEntry(billWithItems))
            }
        }
    }
    
    fun getBillWithItems(id: Long): BillWithItems? {
        ensureInitialized()
        return billWithItemsCache?.let { cache ->
            synchronized(cache) {
                cache[id]?.takeIf { it.isValid() }?.data
            }
        }
    }
    
    fun putBillsList(bills: List<Bill>) {
        ensureInitialized()
        synchronized(this) {
            billsListCache = CacheEntry(bills)
        }
    }
    
    fun getBillsList(): List<Bill>? {
        ensureInitialized()
        return synchronized(this) {
            billsListCache?.takeIf { it.isValid() }?.data
        }
    }
    
    fun putBillsWithItemsList(billsWithItems: List<BillWithItems>) {
        ensureInitialized()
        synchronized(this) {
            billsWithItemsListCache = CacheEntry(billsWithItems)
        }
    }
    
    fun getBillsWithItemsList(): List<BillWithItems>? {
        ensureInitialized()
        return synchronized(this) {
            billsWithItemsListCache?.takeIf { it.isValid() }?.data
        }
    }
    
    private fun invalidateBillsListCache() {
        synchronized(this) {
            billsListCache = null
            billsWithItemsListCache = null
        }
    }
    
    fun putStatistics(key: String, data: StatisticsData) {
        ensureInitialized()
        statisticsCache?.let { cache ->
            synchronized(cache) {
                cache.put(key, CacheEntry(data))
            }
        }
    }
    
    fun getStatistics(key: String): StatisticsData? {
        ensureInitialized()
        return statisticsCache?.let { cache ->
            synchronized(cache) {
                cache[key]?.takeIf { it.isValid() }?.data
            }
        }
    }
    
    fun putHeatmap(key: String, data: HeatmapData) {
        ensureInitialized()
        heatmapCache?.let { cache ->
            synchronized(cache) {
                cache.put(key, CacheEntry(data))
            }
        }
    }
    
    fun getHeatmap(key: String): HeatmapData? {
        ensureInitialized()
        return heatmapCache?.let { cache ->
            synchronized(cache) {
                cache[key]?.takeIf { it.isValid() }?.data
            }
        }
    }
    
    fun putYearlyIncome(year: Int, data: YearlyIncomeData) {
        ensureInitialized()
        yearlyIncomeCache?.let { cache ->
            synchronized(cache) {
                cache.put(year, CacheEntry(data))
            }
        }
    }
    
    fun getYearlyIncome(year: Int): YearlyIncomeData? {
        ensureInitialized()
        return yearlyIncomeCache?.let { cache ->
            synchronized(cache) {
                cache[year]?.takeIf { it.isValid() }?.data
            }
        }
    }
    
    fun putYearlyHeatmap(year: Int, data: YearlyHeatmapData) {
        ensureInitialized()
        yearlyHeatmapCache?.let { cache ->
            synchronized(cache) {
                cache.put(year, CacheEntry(data))
            }
        }
    }
    
    fun getYearlyHeatmap(year: Int): YearlyHeatmapData? {
        ensureInitialized()
        return yearlyHeatmapCache?.let { cache ->
            synchronized(cache) {
                cache[year]?.takeIf { it.isValid() }?.data
            }
        }
    }
    
    fun generateStatisticsKey(startDate: Date, endDate: Date): String {
        return "stats_${startDate.time}_${endDate.time}"
    }
    
    fun generateHeatmapKey(year: Int, month: Int): String {
        return "heatmap_${year}_$month"
    }
    
    fun clearAllCaches() {
        Log.d(TAG, "Clearing all caches")
        billCache?.evictAll()
        billWithItemsCache?.evictAll()
        statisticsCache?.evictAll()
        heatmapCache?.evictAll()
        yearlyIncomeCache?.evictAll()
        yearlyHeatmapCache?.evictAll()
        synchronized(this) {
            billsListCache = null
            billsWithItemsListCache = null
        }
        cacheTimestamps.clear()
    }
    
    fun clearStatisticsCaches() {
        Log.d(TAG, "Clearing statistics caches")
        statisticsCache?.evictAll()
        heatmapCache?.evictAll()
        yearlyIncomeCache?.evictAll()
        yearlyHeatmapCache?.evictAll()
    }
    
    fun getCacheStats(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        result["bills"] = billCache?.size() ?: 0
        result["billsWithItems"] = billWithItemsCache?.size() ?: 0
        result["statistics"] = statisticsCache?.size() ?: 0
        result["heatmap"] = heatmapCache?.size() ?: 0
        result["yearlyIncome"] = yearlyIncomeCache?.size() ?: 0
        result["yearlyHeatmap"] = yearlyHeatmapCache?.size() ?: 0
        return result
    }
    
    fun preloadCriticalData(bills: List<Bill>, billsWithItems: List<BillWithItems>) {
        ensureInitialized()
        putBillsList(bills)
        putBillsWithItemsList(billsWithItems)
        
        bills.forEach { bill ->
            putBill(bill)
        }
        
        billsWithItems.forEach { billWithItems ->
            putBillWithItems(billWithItems)
        }
    }
    
    fun shutdown() {
        Log.i(TAG, "Shutting down DataCacheManager")
        cleanupJob?.cancel()
        cleanupJob = null
        clearAllCaches()
        isInitialized.set(false)
    }
    
    fun isInitialized(): Boolean = isInitialized.get()
}
