package com.example.daxijizhang.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.daxijizhang.R
import com.google.android.material.appbar.MaterialToolbar

object ThemeManager {

    private const val PREFS_NAME = "user_settings"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_FONT_SIZE = "font_size"
    private const val DEFAULT_THEME_COLOR = "blue"
    private const val DEFAULT_FONT_SIZE = 16f

    // 主题颜色映射
    private val themeColorMap = mapOf(
        "blue" to R.color.primary,
        "purple" to R.color.purple_500,
        "orange" to R.color.warning,
        "red" to R.color.error,
        "green" to R.color.success
    )

    /**
     * 获取当前主题颜色
     */
    fun getThemeColor(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR
    }

    /**
     * 获取当前字体大小
     */
    fun getFontSize(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }

    /**
     * 获取主题颜色资源ID
     */
    fun getThemeColorRes(context: Context): Int {
        val colorKey = getThemeColor(context)
        return themeColorMap[colorKey] ?: R.color.primary
    }

    /**
     * 应用主题颜色到工具栏
     */
    fun applyThemeColorToToolbar(toolbar: MaterialToolbar) {
        val colorRes = getThemeColorRes(toolbar.context)
        val color = ContextCompat.getColor(toolbar.context, colorRes)
        toolbar.setBackgroundColor(color)
    }

    /**
     * 应用字体大小到所有TextView
     */
    fun applyFontSizeToActivity(activity: Activity) {
        val fontSize = getFontSize(activity)
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        applyFontSizeToView(rootView, fontSize)
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
}
