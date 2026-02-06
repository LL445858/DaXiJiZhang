package com.example.daxijizhang.ui.settings

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityDisplaySettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener

class DisplaySettingsActivity : BaseActivity(), ColorPickerDialogListener {

    private lateinit var binding: ActivityDisplaySettingsBinding
    private lateinit var prefs: SharedPreferences

    private var currentFontSize = 16f
    private var currentHue = 210f // 默认蓝色色相
    private var selectedColor = Color.parseColor("#FF2196F3") // 默认蓝色
    private var tempSelectedColor = selectedColor // 临时选择的颜色

    // 深色模式选项
    private val darkModeOptions = listOf("始终关闭", "始终开启", "跟随系统")
    private val darkModeValues = listOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    companion object {
        private const val TAG = "DisplaySettingsActivity"
        private const val PREFS_NAME = "user_settings"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_THEME_COLOR_HUE = "theme_color_hue"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_FONT_SIZE = "font_size"
        private const val DEFAULT_HUE = 210f
        private const val DEFAULT_FONT_SIZE = 16f
        private const val DEFAULT_COLOR = 0xFF2196F3.toInt()
        private const val COLOR_PICKER_DIALOG_ID = 0

        // 旧版本颜色映射（用于兼容）
        private val legacyColorMap = mapOf(
            "blue" to 0xFF2196F3.toInt(),
            "purple" to 0xFF6200EE.toInt(),
            "orange" to 0xFFFF9800.toInt(),
            "red" to 0xFFF44336.toInt(),
            "green" to 0xFF4CAF50.toInt()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisplaySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        setupClickListeners()
        loadCurrentSettings()
    }

    override fun onResume() {
        super.onResume()
        // 确保adapter在深色模式切换后正确设置
        setupDarkModeSpinner()
    }

    private fun setupDarkModeSpinner() {
        // 设置深色模式下拉框 - 使用自定义布局来减少行间距
        val adapter = ArrayAdapter(this, R.layout.item_dropdown_dark_mode, darkModeOptions)
        binding.spinnerDarkMode.setAdapter(adapter)
        // 确保当前选中的值正确显示
        val darkMode = loadDarkModeSafely()
        val darkModeIndex = darkModeValues.indexOf(darkMode).takeIf { it >= 0 } ?: 0
        binding.spinnerDarkMode.setText(darkModeOptions[darkModeIndex], false)
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 设置字体大小滑动条监听
        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            currentFontSize = value
            updateFontSizeDisplay(value)
        }

        // 设置深色模式下拉框
        setupDarkModeSpinner()
    }

    private fun setupClickListeners() {
        // 主题颜色条目点击 - 显示颜色选择器
        binding.itemThemeColor.setOnClickListener {
            showColorPickerDialog()
        }

        // 深色模式选择 - 修复点击冲突和交互问题
        binding.spinnerDarkMode.setOnItemClickListener { parent, _, position, _ ->
            // 关闭下拉菜单
            parent?.let {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(binding.spinnerDarkMode.windowToken, 0)
            }

            val selectedMode = darkModeValues[position]
            saveDarkMode(selectedMode)
            applyDarkMode(selectedMode)
            // 更新显示文本但不触发重新弹窗
            binding.spinnerDarkMode.setText(darkModeOptions[position], false)
            // 清除焦点防止再次触发
            binding.spinnerDarkMode.clearFocus()
        }

        // 修复：点击深色模式条目时，如果下拉菜单已打开则关闭它
        binding.itemDarkMode.setOnClickListener {
            // 如果下拉菜单正在显示，则关闭它
            if (binding.spinnerDarkMode.isPopupShowing) {
                binding.spinnerDarkMode.dismissDropDown()
            } else {
                // 否则显示下拉菜单
                binding.spinnerDarkMode.showDropDown()
            }
        }

        // 应用字体大小
        binding.btnApplyFontSize.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                applyFontSize()
            }
        }
    }

    private fun showColorPickerDialog() {
        val dialog = ColorPickerDialog.newBuilder()
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setAllowPresets(true)
            .setAllowCustom(true)
            .setShowAlphaSlider(false)
            .setShowColorShades(true)
            .setColor(selectedColor)
            .setDialogTitle(R.string.select_theme_color)
            .setPresets(intArrayOf(
                Color.parseColor("#FF2196F3"), // 蓝色
                Color.parseColor("#FF6200EE"), // 紫色
                Color.parseColor("#FFFF9800"), // 橙色
                Color.parseColor("#FFF44336"), // 红色
                Color.parseColor("#FF4CAF50"), // 绿色
                Color.parseColor("#FF009688"), // 青色
                Color.parseColor("#FF3F51B5"), // 靛蓝
                Color.parseColor("#FFE91E63")  // 粉色
            ))
            .create()

        // 在Dialog显示时设置圆角背景
        dialog.setStyle(ColorPickerDialog.STYLE_NORMAL, R.style.ColorPickerDialogTheme)

        dialog.show(supportFragmentManager, "color-picker-dialog")
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == COLOR_PICKER_DIALOG_ID) {
            selectedColor = color
            currentHue = colorToHue(color)
            saveThemeColor(selectedColor, currentHue)
            updateThemeColorPreview(selectedColor)
            Toast.makeText(this, R.string.theme_color_applied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
        // 对话框关闭时的回调，不需要额外处理
    }

    private fun colorToHue(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun loadCurrentSettings() {
        // 加载主题颜色（带类型安全检查）
        selectedColor = loadThemeColorSafely()
        currentHue = try {
            prefs.getFloat(KEY_THEME_COLOR_HUE, DEFAULT_HUE)
        } catch (e: Exception) {
            DEFAULT_HUE
        }
        updateThemeColorPreview(selectedColor)

        // 加载深色模式（带类型安全检查）
        val darkMode = loadDarkModeSafely()
        val darkModeIndex = darkModeValues.indexOf(darkMode).takeIf { it >= 0 } ?: 0
        binding.spinnerDarkMode.setText(darkModeOptions[darkModeIndex], false)

        // 加载字体大小
        currentFontSize = try {
            prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        } catch (e: Exception) {
            DEFAULT_FONT_SIZE
        }
        binding.sliderFontSize.value = currentFontSize
        updateFontSizeDisplay(currentFontSize)

        // 设置页面标题栏跟随深色模式
        updateToolbarForDarkMode()
    }

    private fun updateToolbarForDarkMode() {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            binding.toolbar.setBackgroundColor(getColor(R.color.surface))
            binding.toolbar.setTitleTextColor(getColor(R.color.text_primary))
            binding.toolbar.navigationIcon?.setTint(getColor(R.color.text_primary))
        } else {
            binding.toolbar.setBackgroundColor(getColor(R.color.surface))
            binding.toolbar.setTitleTextColor(getColor(R.color.text_primary))
            binding.toolbar.navigationIcon?.setTint(getColor(R.color.text_primary))
        }
    }

    /**
     * 安全地加载主题颜色
     * 处理旧版本String类型和新版本Int类型的兼容问题
     */
    private fun loadThemeColorSafely(): Int {
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
     */
    private fun loadDarkModeSafely(): Int {
        return try {
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

    private fun updateThemeColorPreview(color: Int) {
        val drawable = binding.viewThemeColorPreview.background
        if (drawable is android.graphics.drawable.GradientDrawable) {
            drawable.setColor(color)
        }
    }

    private fun saveThemeColor(color: Int, hue: Float) {
        prefs.edit()
            .putInt(KEY_THEME_COLOR, color)
            .putFloat(KEY_THEME_COLOR_HUE, hue)
            .apply()

        // 更新全局主题色
        ThemeManager.setThemeColor(color)

        // 更新标题栏
        updateToolbarForDarkMode()
    }

    private fun saveDarkMode(mode: Int) {
        prefs.edit().putInt(KEY_DARK_MODE, mode).apply()
    }

    private fun applyDarkMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun updateFontSizeDisplay(size: Float) {
        binding.tvCurrentFontSize.text = "${size.toInt()}sp"
        binding.tvPreviewText.textSize = size
    }

    private fun applyFontSize() {
        prefs.edit().putFloat(KEY_FONT_SIZE, currentFontSize).apply()
        Toast.makeText(this, R.string.font_size_applied, Toast.LENGTH_SHORT).show()

        // 立即应用字体大小到当前Activity
        ThemeManager.applyFontSizeToActivity(this)
    }
}
