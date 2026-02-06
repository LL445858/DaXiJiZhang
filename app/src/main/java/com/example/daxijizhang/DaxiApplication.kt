package com.example.daxijizhang

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.daxijizhang.util.AutoBackupManager
import com.example.daxijizhang.util.ThemeManager

class DaxiApplication : Application() {

    private val TAG = "DaxiApplication"

    // MainActivity弱引用，用于主题颜色变化时通知更新
    private var mainActivityRef: java.lang.ref.WeakReference<MainActivity>? = null

    // 字体缩放变化监听器列表
    private val fontScaleListeners = mutableListOf<OnFontScaleChangeListener>()

    // 字体缩放变化监听器接口
    interface OnFontScaleChangeListener {
        fun onFontScaleChanged(scale: Float)
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化主题管理器
        ThemeManager.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }

    // 注意：字体缩放通过BaseActivity.attachBaseContext统一处理
    // 不在Application级别应用，避免重复缩放

    /**
     * 注册MainActivity引用
     */
    fun registerMainActivity(activity: MainActivity) {
        mainActivityRef = java.lang.ref.WeakReference(activity)
    }

    /**
     * 注销MainActivity引用
     */
    fun unregisterMainActivity() {
        mainActivityRef?.clear()
        mainActivityRef = null
    }

    /**
     * 通知主题颜色变化
     */
    fun notifyThemeColorChanged(color: Int) {
        mainActivityRef?.get()?.reapplyThemeColor()
    }

    /**
     * 添加字体缩放变化监听器
     */
    fun addFontScaleListener(listener: OnFontScaleChangeListener) {
        if (!fontScaleListeners.contains(listener)) {
            fontScaleListeners.add(listener)
        }
    }

    /**
     * 移除字体缩放变化监听器
     */
    fun removeFontScaleListener(listener: OnFontScaleChangeListener) {
        fontScaleListeners.remove(listener)
    }

    /**
     * 通知字体缩放变化
     */
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
        override fun onStop(owner: LifecycleOwner) {
            Log.i(TAG, "应用进入后台，执行自动备份检查")
            performAutoBackup()
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
