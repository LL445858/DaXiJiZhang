package com.example.daxijizhang.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar

object ThemeManager {

    private const val TAG = "ThemeManager"
    private const val PREFS_NAME = "user_settings"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_THEME_COLOR_HUE = "theme_color_hue"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_FONT_SIZE = "font_size"
    private const val DEFAULT_HUE = 210f
    private const val DEFAULT_FONT_SIZE = 16f
    private const val DEFAULT_COLOR = 0xFF2196F3.toInt() // 默认蓝色

    private var currentThemeColor: Int = DEFAULT_COLOR
    private var isInitialized = false

    // 旧版本颜色映射（用于兼容）
    private val legacyColorMap = mapOf(
        "blue" to 0xFF2196F3.toInt(),
        "purple" to 0xFF6200EE.toInt(),
        "orange" to 0xFFFF9800.toInt(),
        "red" to 0xFFF44336.toInt(),
        "green" to 0xFF4CAF50.toInt()
    )

    /**
     * 初始化主题管理器
     */
    fun init(application: Application) {
        if (isInitialized) return

        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 加载主题色（带类型安全检查和异常处理）
        currentThemeColor = loadThemeColorSafely(prefs)

        // 加载并应用深色模式（带类型安全检查和异常处理）
        val darkMode = loadDarkModeSafely(prefs)
        AppCompatDelegate.setDefaultNightMode(darkMode)

        // 注册Activity生命周期回调
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyTheme(activity)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        isInitialized = true
    }

    /**
     * 安全地加载主题颜色
     * 处理旧版本String类型和新版本Int类型的兼容问题
     */
    private fun loadThemeColorSafely(prefs: SharedPreferences): Int {
        return try {
            // 首先尝试作为Int读取
            prefs.getInt(KEY_THEME_COLOR, DEFAULT_COLOR)
        } catch (e: ClassCastException) {
            // 如果失败，可能是旧版本的String类型
            try {
                val colorString = prefs.getString(KEY_THEME_COLOR, null)
                val color = if (colorString != null) {
                    // 尝试从旧版颜色名称映射获取
                    legacyColorMap[colorString] ?: DEFAULT_COLOR
                } else {
                    DEFAULT_COLOR
                }
                // 将旧格式转换为新格式（Int）并保存
                prefs.edit().putInt(KEY_THEME_COLOR, color).apply()
                Log.i(TAG, "迁移旧版主题颜色: $colorString -> $color")
                color
            } catch (e2: Exception) {
                Log.e(TAG, "读取主题颜色失败，使用默认值", e2)
                DEFAULT_COLOR
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取主题颜色失败，使用默认值", e)
            DEFAULT_COLOR
        }
    }

    /**
     * 安全地加载深色模式设置
     * 处理可能的类型不兼容问题
     * 默认值为"始终关闭"(MODE_NIGHT_NO)
     */
    private fun loadDarkModeSafely(prefs: SharedPreferences): Int {
        return try {
            // 首次安装默认使用"始终关闭"
            prefs.getInt(KEY_DARK_MODE, AppCompatDelegate.MODE_NIGHT_NO)
        } catch (e: ClassCastException) {
            // 如果类型不匹配，尝试读取String并转换
            try {
                val modeString = prefs.getString(KEY_DARK_MODE, null)
                val mode = when (modeString) {
                    "yes", "on", "true" -> AppCompatDelegate.MODE_NIGHT_YES
                    "no", "off", "false" -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_NO
                }
                // 更新为正确的Int类型
                prefs.edit().putInt(KEY_DARK_MODE, mode).apply()
                Log.i(TAG, "迁移旧版深色模式设置: $modeString -> $mode")
                mode
            } catch (e2: Exception) {
                Log.e(TAG, "读取深色模式失败，使用默认设置", e2)
                AppCompatDelegate.MODE_NIGHT_NO
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取深色模式失败，使用默认设置", e)
            AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    /**
     * 获取当前主题颜色
     */
    fun getThemeColor(): Int = currentThemeColor

    /**
     * 设置主题颜色
     */
    fun setThemeColor(color: Int) {
        currentThemeColor = color
    }

    /**
     * 获取当前主题颜色（从SharedPreferences，带异常处理）
     */
    fun getThemeColor(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadThemeColorSafely(prefs)
        } catch (e: Exception) {
            Log.e(TAG, "获取主题颜色失败，使用默认值", e)
            DEFAULT_COLOR
        }
    }

    /**
     * 获取当前字体大小（带异常处理）
     */
    fun getFontSize(context: Context): Float {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        } catch (e: Exception) {
            Log.e(TAG, "获取字体大小失败，使用默认值", e)
            DEFAULT_FONT_SIZE
        }
    }

    /**
     * 应用主题颜色到工具栏
     */
    fun applyThemeColorToToolbar(toolbar: MaterialToolbar) {
        val color = currentThemeColor
        toolbar.setBackgroundColor(color)
    }

    /**
     * 应用字体大小到所有TextView
     */
    fun applyFontSizeToActivity(activity: Activity) {
        try {
            val fontSize = getFontSize(activity)
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            applyFontSizeToView(rootView, fontSize)
        } catch (e: Exception) {
            Log.e(TAG, "应用字体大小失败", e)
        }
    }

    /**
     * 递归应用字体大小到所有TextView
     */
    private fun applyFontSizeToView(view: View, fontSize: Float) {
        when (view) {
            is TextView -> {
                // 只调整正文字体大小，不调整标题和小字
                @Suppress("DEPRECATION")
                val currentSize = view.textSize / view.resources.displayMetrics.scaledDensity
                if (currentSize >= 14 && currentSize <= 20) {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                }
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyFontSizeToView(view.getChildAt(i), fontSize)
                }
            }
        }
    }

    /**
     * 应用完整主题到Activity
     */
    fun applyTheme(activity: Activity) {
        // 应用字体大小
        applyFontSizeToActivity(activity)
    }

    /**
     * 获取主题色的不同透明度版本
     */
    fun getThemeColorWithAlpha(alpha: Int): Int {
        return Color.argb(
            alpha,
            Color.red(currentThemeColor),
            Color.green(currentThemeColor),
            Color.blue(currentThemeColor)
        )
    }

    /**
     * 获取主题色的浅色版本
     */
    fun getThemeColorLight(): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(currentThemeColor, hsv)
        hsv[1] = hsv[1] * 0.3f // 降低饱和度
        hsv[2] = 0.95f // 提高亮度
        return Color.HSVToColor(hsv)
    }

    /**
     * 获取主题色的深色版本
     */
    fun getThemeColorDark(): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(currentThemeColor, hsv)
        hsv[2] = hsv[2] * 0.8f // 降低亮度
        return Color.HSVToColor(hsv)
    }

    /**
     * 判断当前是否为深色模式
     */
    fun isDarkMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
