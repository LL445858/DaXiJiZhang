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
    private const val KEY_FONT_SIZE_PERCENT = "font_size_percent"
    private const val DEFAULT_HUE = 210f
    private const val DEFAULT_FONT_SIZE = 16f
    private const val DEFAULT_FONT_SIZE_PERCENT = 1.0f // 默认100%
    private const val DEFAULT_COLOR = 0xFF2196F3.toInt() // 默认蓝色

    private var currentThemeColor: Int = DEFAULT_COLOR
    private var currentFontScale: Float = DEFAULT_FONT_SIZE_PERCENT
    private var isInitialized = false

    // 主题颜色变化监听器列表
    private val themeColorListeners = mutableListOf<OnThemeColorChangeListener>()

    // 主题颜色变化监听器接口
    interface OnThemeColorChangeListener {
        fun onThemeColorChanged(color: Int)
    }

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

        // 加载字体缩放比例
        currentFontScale = loadFontScaleSafely(prefs)

        // 加载并应用深色模式（带类型安全检查和异常处理）
        val darkMode = loadDarkModeSafely(prefs)
        AppCompatDelegate.setDefaultNightMode(darkMode)

        // 注意：字体缩放现在通过BaseActivity.attachBaseContext统一处理
        // 不再需要在Activity生命周期回调中单独应用

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
     * 安全地加载字体缩放比例
     * 处理可能的类型不兼容问题
     * 默认值为1.0f (100%)
     */
    private fun loadFontScaleSafely(prefs: SharedPreferences): Float {
        return try {
            prefs.getFloat(KEY_FONT_SIZE_PERCENT, DEFAULT_FONT_SIZE_PERCENT)
        } catch (e: Exception) {
            Log.e(TAG, "读取字体缩放比例失败，使用默认值", e)
            DEFAULT_FONT_SIZE_PERCENT
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
     * 获取当前字体缩放比例 (0.5 - 1.5)
     */
    fun getFontScale(): Float = currentFontScale

    /**
     * 设置字体缩放比例
     */
    fun setFontScale(scale: Float) {
        currentFontScale = scale.coerceIn(0.5f, 1.5f)
    }

    /**
     * 设置主题颜色
     */
    fun setThemeColor(color: Int) {
        currentThemeColor = color
        // 通知所有监听器主题颜色已变化
        notifyThemeColorChanged(color)
    }

    /**
     * 添加主题颜色变化监听器
     */
    fun addThemeColorChangeListener(listener: OnThemeColorChangeListener) {
        if (!themeColorListeners.contains(listener)) {
            themeColorListeners.add(listener)
        }
    }

    /**
     * 移除主题颜色变化监听器
     */
    fun removeThemeColorChangeListener(listener: OnThemeColorChangeListener) {
        themeColorListeners.remove(listener)
    }

    /**
     * 通知所有监听器主题颜色已变化
     */
    private fun notifyThemeColorChanged(color: Int) {
        themeColorListeners.forEach { listener ->
            try {
                listener.onThemeColorChanged(color)
            } catch (e: Exception) {
                Log.e(TAG, "通知主题颜色变化失败", e)
            }
        }
    }

    /**
     * 应用主题颜色到视图
     * 用于在运行时动态更新视图的主题色
     */
    fun applyThemeColorToView(view: View) {
        when (view) {
            is com.google.android.material.floatingactionbutton.FloatingActionButton -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(currentThemeColor)
            }
            is com.google.android.material.button.MaterialButton -> {
                if (view.tag == "primary") {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(currentThemeColor)
                }
            }
            is com.google.android.material.slider.Slider -> {
                view.trackActiveTintList = android.content.res.ColorStateList.valueOf(currentThemeColor)
                view.thumbTintList = android.content.res.ColorStateList.valueOf(currentThemeColor)
            }
            is com.google.android.material.switchmaterial.SwitchMaterial -> {
                view.thumbTintList = createSwitchThumbColorStateList()
                view.trackTintList = createSwitchTrackColorStateList()
            }
            is android.widget.ImageView -> {
                if (view.tag == "theme_icon") {
                    view.setColorFilter(currentThemeColor)
                }
            }
            is android.widget.TextView -> {
                if (view.tag == "theme_text") {
                    view.setTextColor(currentThemeColor)
                }
            }
        }
    }

    /**
     * 创建开关滑块颜色状态列表
     */
    fun createSwitchThumbColorStateList(): android.content.res.ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            currentThemeColor,
            Color.WHITE
        )
        return android.content.res.ColorStateList(states, colors)
    }

    /**
     * 创建开关轨道颜色状态列表
     */
    fun createSwitchTrackColorStateList(): android.content.res.ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            getThemeColorWithAlpha(128),
            Color.parseColor("#FFE0E0E0")
        )
        return android.content.res.ColorStateList(states, colors)
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
     * 应用完整主题到Activity
     * 注意：字体缩放现在通过Application配置统一处理
     */
    fun applyTheme(activity: Activity) {
        // 字体缩放已通过Application配置处理，这里不需要额外操作
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
