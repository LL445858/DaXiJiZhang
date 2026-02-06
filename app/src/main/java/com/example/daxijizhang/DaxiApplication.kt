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

    override fun attachBaseContext(base: Context) {
        // 在Application级别应用字体缩放
        val scaledContext = applyFontScaleToContext(base)
        super.attachBaseContext(scaledContext)
    }

    /**
     * 应用字体缩放到Context
     */
    private fun applyFontScaleToContext(context: Context): Context {
        // 从SharedPreferences读取字体缩放值
        val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        val scale = try {
            prefs.getFloat("font_size_percent", 100f) / 100f
        } catch (e: Exception) {
            1.0f
        }

        // 如果缩放值为1.0（100%），不需要修改
        if (scale == 1.0f) {
            return context
        }

        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = scale
        return context.createConfigurationContext(configuration)
    }

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
