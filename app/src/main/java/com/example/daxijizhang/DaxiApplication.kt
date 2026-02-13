package com.example.daxijizhang

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.daxijizhang.data.cache.DataCacheManager
import com.example.daxijizhang.util.AutoBackupManager
import com.example.daxijizhang.util.CrashHandler
import com.example.daxijizhang.util.MemoryGuard
import com.example.daxijizhang.util.ThemeManager

class DaxiApplication : Application() {

    private val TAG = "DaxiApplication"

    companion object {
        @Volatile
        private var instance: DaxiApplication? = null

        fun getInstance(): DaxiApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    private var mainActivityRef: java.lang.ref.WeakReference<MainActivity>? = null

    private val fontScaleListeners = mutableListOf<OnFontScaleChangeListener>()

    interface OnFontScaleChangeListener {
        fun onFontScaleChanged(scale: Float)
    }

    override fun onCreate() {
        super.onCreate()
        
        instance = this
        
        CrashHandler.initialize(this)
        
        ThemeManager.init(this)
        
        DataCacheManager.initialize()
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
        
        logMemoryStatus("Application onCreate")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory called, clearing caches")
        DataCacheManager.clearAllCaches()
        MemoryGuard.triggerGC()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory called with level: $level")
        when (level) {
            TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_RUNNING_CRITICAL -> {
                DataCacheManager.clearStatisticsCaches()
            }
            TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_BACKGROUND -> {
                DataCacheManager.clearAllCaches()
            }
        }
    }

    private fun logMemoryStatus(tag: String) {
        val memoryInfo = MemoryGuard.getMemoryInfo()
        Log.d(TAG, "[$tag] Memory: ${memoryInfo.usedMemoryMB}MB / ${memoryInfo.maxMemoryMB}MB (${String.format("%.1f", memoryInfo.availableRatio * 100)}% available)")
    }

    fun registerMainActivity(activity: MainActivity) {
        mainActivityRef = java.lang.ref.WeakReference(activity)
    }

    fun unregisterMainActivity() {
        mainActivityRef?.clear()
        mainActivityRef = null
    }

    fun notifyThemeColorChanged(color: Int) {
        mainActivityRef?.get()?.reapplyThemeColor()
    }

    fun addFontScaleListener(listener: OnFontScaleChangeListener) {
        if (!fontScaleListeners.contains(listener)) {
            fontScaleListeners.add(listener)
        }
    }

    fun removeFontScaleListener(listener: OnFontScaleChangeListener) {
        fontScaleListeners.remove(listener)
    }

    fun notifyFontScaleChanged(scale: Float) {
        fontScaleListeners.forEach { listener ->
            try {
                listener.onFontScaleChanged(scale)
            } catch (e: Exception) {
                Log.e(TAG, "通知字体缩放变化失败", e)
            }
        }
    }

    inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            Log.i(TAG, "应用进入前台")
        }
        
        override fun onStop(owner: LifecycleOwner) {
            Log.i(TAG, "应用进入后台，执行自动备份检查")
            performAutoBackup()
        }
        
        override fun onDestroy(owner: LifecycleOwner) {
            Log.i(TAG, "应用进程即将销毁")
            DataCacheManager.shutdown()
        }
    }

    private fun performAutoBackup() {
        try {
            AutoBackupManager.getInstance(this).performAutoBackupIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "自动备份执行失败", e)
        }
    }
}
