package com.example.daxijizhang.util

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.Date
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object CrashHandler : Thread.UncaughtExceptionHandler {
    
    private const val TAG = "CrashHandler"
    private const val CRASH_DIR = "crash_logs"
    
    private val contextRef = java.util.concurrent.atomic.AtomicReference<android.content.Context>(null)
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var isInitialized = false
    
    fun initialize(ctx: Context) {
        if (isInitialized) return
        
        contextRef.set(ctx.applicationContext)
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        isInitialized = true
        
        Log.i(TAG, "CrashHandler initialized")
    }
    
    private fun getContext(): Context? = contextRef.get()
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)
        
        try {
            saveCrashLog(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
        
        defaultHandler?.uncaughtException(thread, throwable) ?: run {
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }
    
    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        val ctx = getContext() ?: return
        
        try {
            val crashDir = File(ctx.cacheDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            
            val timestamp = DateFormatter.formatCrashLogTimestamp(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")
            
            FileWriter(crashFile, true).use { writer ->
                PrintWriter(writer).use { pw ->
                    pw.println("========== Crash Log ==========")
                    pw.println("Time: ${Date()}")
                    pw.println("Thread: ${thread.name} (id: ${thread.id})")
                    pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    pw.println("App Version: ${getAppVersion(ctx)}")
                    pw.println()
                    pw.println("Exception:")
                    throwable.printStackTrace(pw)
                    pw.println()
                    pw.println("Stack Trace:")
                    thread.stackTrace.forEach { frame ->
                        pw.println("    at $frame")
                    }
                    pw.println("================================")
                    pw.println()
                }
            }
            
            Log.i(TAG, "Crash log saved to: ${crashFile.absolutePath}")
            
            cleanupOldCrashLogs(crashDir)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving crash log", e)
        }
    }
    
    private fun getAppVersion(ctx: Context): String {
        return try {
            val pm = ctx.packageManager
            val pi = pm.getPackageInfo(ctx.packageName, 0)
            "${pi.versionName} (${pi.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun cleanupOldCrashLogs(crashDir: File) {
        try {
            val files = crashDir.listFiles() ?: return
            val maxFiles = 10
            
            if (files.size > maxFiles) {
                files.sortedBy { it.lastModified() }
                    .take(files.size - maxFiles)
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up crash logs", e)
        }
    }
    
    fun getCrashLogs(): List<File> {
        val ctx = getContext() ?: return emptyList()
        val crashDir = File(ctx.cacheDir, CRASH_DIR)
        if (!crashDir.exists()) return emptyList()
        
        return crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun clearCrashLogs() {
        val ctx = getContext() ?: return
        val crashDir = File(ctx.cacheDir, CRASH_DIR)
        if (crashDir.exists()) {
            crashDir.listFiles()?.forEach { it.delete() }
        }
    }
    
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception", throwable)
    }
}

class SafeThreadFactory(private val namePrefix: String) : ThreadFactory {
    private val threadNumber = AtomicInteger(1)
    
    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r, "$namePrefix-${threadNumber.getAndIncrement()}")
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
            Log.e("SafeThread", "Uncaught exception in thread ${t.name}", e)
        }
        return thread
    }
}

object SafeExecutor {
    
    val errorHandler = { operation: String, error: Throwable ->
        Log.e("SafeExecutor", "Error in operation: $operation", error)
    }
    
    inline fun <T> runSafely(operation: String, default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            errorHandler(operation, e)
            default
        }
    }
    
    inline fun runSafely(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            errorHandler(operation, e)
        }
    }
    
    inline fun <T> runWithNullCheck(operation: String, block: () -> T?): T? {
        return try {
            block()
        } catch (e: NullPointerException) {
            Log.e("SafeExecutor", "NullPointerException in operation: $operation", e)
            null
        } catch (e: Exception) {
            errorHandler(operation, e)
            null
        }
    }
}

object InputValidator {
    
    fun validateString(input: String?, maxLength: Int = 100, fieldName: String = "输入"): Result<String> {
        if (input.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("$fieldName 不能为空"))
        }
        if (input.length > maxLength) {
            return Result.failure(IllegalArgumentException("$fieldName 长度不能超过 $maxLength 个字符"))
        }
        return Result.success(input.trim())
    }
    
    fun validateAmount(amount: Double?, min: Double = 0.0, max: Double = Double.MAX_VALUE, fieldName: String = "金额"): Result<Double> {
        if (amount == null) {
            return Result.failure(IllegalArgumentException("$fieldName 不能为空"))
        }
        if (amount < min) {
            return Result.failure(IllegalArgumentException("$fieldName 不能小于 $min"))
        }
        if (amount > max) {
            return Result.failure(IllegalArgumentException("$fieldName 不能超过 $max"))
        }
        if (amount.isNaN() || amount.isInfinite()) {
            return Result.failure(IllegalArgumentException("$fieldName 格式无效"))
        }
        return Result.success(amount)
    }
    
    fun validateDate(date: Date?, fieldName: String = "日期"): Result<Date> {
        if (date == null) {
            return Result.failure(IllegalArgumentException("$fieldName 不能为空"))
        }
        return Result.success(date)
    }
    
    fun validateDateRange(startDate: Date?, endDate: Date?): Result<Pair<Date, Date>> {
        if (startDate == null) {
            return Result.failure(IllegalArgumentException("开始日期不能为空"))
        }
        if (endDate == null) {
            return Result.failure(IllegalArgumentException("结束日期不能为空"))
        }
        if (startDate.after(endDate)) {
            return Result.failure(IllegalArgumentException("开始日期不能晚于结束日期"))
        }
        return Result.success(Pair(startDate, endDate))
    }
    
    fun sanitizeString(input: String?): String {
        return input?.trim()?.takeIf { it.isNotBlank() } ?: ""
    }
    
    fun sanitizeAmount(input: String?): Double {
        return input?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }
}

object MemoryGuard {
    
    private const val LOW_MEMORY_THRESHOLD = 0.15
    private const val CRITICAL_MEMORY_THRESHOLD = 0.05
    
    fun isLowMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableRatio = (maxMemory - usedMemory).toDouble() / maxMemory
        
        return availableRatio < LOW_MEMORY_THRESHOLD
    }
    
    fun isCriticalMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableRatio = (maxMemory - usedMemory).toDouble() / maxMemory
        
        return availableRatio < CRITICAL_MEMORY_THRESHOLD
    }
    
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        return MemoryInfo(
            maxMemoryMB = maxMemory / (1024 * 1024),
            totalMemoryMB = totalMemory / (1024 * 1024),
            freeMemoryMB = freeMemory / (1024 * 1024),
            usedMemoryMB = usedMemory / (1024 * 1024),
            availableRatio = (maxMemory - usedMemory).toDouble() / maxMemory
        )
    }
    
    fun triggerGC() {
        System.gc()
        Runtime.getRuntime().runFinalization()
    }
    
    data class MemoryInfo(
        val maxMemoryMB: Long,
        val totalMemoryMB: Long,
        val freeMemoryMB: Long,
        val usedMemoryMB: Long,
        val availableRatio: Double
    ) {
        val isLowMemory: Boolean
            get() = availableRatio < LOW_MEMORY_THRESHOLD
        
        val isCriticalMemory: Boolean
            get() = availableRatio < CRITICAL_MEMORY_THRESHOLD
        
        override fun toString(): String {
            return "MemoryInfo(max=${maxMemoryMB}MB, used=${usedMemoryMB}MB, free=${freeMemoryMB}MB, ratio=${"%.2f".format(availableRatio * 100)}%)"
        }
    }
}
