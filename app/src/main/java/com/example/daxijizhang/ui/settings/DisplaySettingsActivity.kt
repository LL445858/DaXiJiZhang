package com.example.daxijizhang.ui.settings

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityDisplaySettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil
import com.google.android.material.slider.Slider

class DisplaySettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityDisplaySettingsBinding
    private lateinit var prefs: SharedPreferences

    private val themeColors = listOf(
        ThemeColor("blue", R.color.primary, R.drawable.circle_color_blue),
        ThemeColor("purple", R.color.purple_500, R.drawable.circle_color_purple),
        ThemeColor("orange", R.color.warning, R.drawable.circle_color_orange),
        ThemeColor("red", R.color.error, R.drawable.circle_color_red),
        ThemeColor("green", R.color.success, R.drawable.circle_color_green)
    )

    private var selectedColorKey = "blue"
    private var currentFontSize = 16f

    data class ThemeColor(
        val key: String,
        val colorRes: Int,
        val drawableRes: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisplaySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_settings", MODE_PRIVATE)

        initViews()
        setupClickListeners()
        loadCurrentSettings()
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
    }

    private fun setupClickListeners() {
        // 颜色选择
        binding.colorBlue.setOnClickListener { selectColor("blue") }
        binding.colorPurple.setOnClickListener { selectColor("purple") }
        binding.colorOrange.setOnClickListener { selectColor("orange") }
        binding.colorRed.setOnClickListener { selectColor("red") }
        binding.colorGreen.setOnClickListener { selectColor("green") }

        // 应用字体大小
        binding.btnApplyFontSize.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                applyFontSize()
            }
        }
    }

    private fun loadCurrentSettings() {
        // 加载主题颜色
        selectedColorKey = prefs.getString("theme_color", "blue") ?: "blue"
        updateColorSelection(selectedColorKey)

        // 加载字体大小
        currentFontSize = prefs.getFloat("font_size", 16f)
        binding.sliderFontSize.value = currentFontSize
        updateFontSizeDisplay(currentFontSize)
        
        // 显示设置页面的标题栏始终保持白色，不跟随主题色变化
        binding.toolbar.setBackgroundColor(Color.WHITE)
        binding.toolbar.setTitleTextColor(Color.BLACK)
        binding.toolbar.navigationIcon?.setTint(Color.BLACK)
    }

    private fun selectColor(colorKey: String) {
        selectedColorKey = colorKey
        updateColorSelection(colorKey)
        saveThemeColor(colorKey)
    }

    private fun updateColorSelection(colorKey: String) {
        // 隐藏所有选中标记
        binding.ivCheckBlue.visibility = View.GONE
        binding.ivCheckPurple.visibility = View.GONE
        binding.ivCheckOrange.visibility = View.GONE
        binding.ivCheckRed.visibility = View.GONE
        binding.ivCheckGreen.visibility = View.GONE

        // 显示当前选中的标记
        when (colorKey) {
            "blue" -> binding.ivCheckBlue.visibility = View.VISIBLE
            "purple" -> binding.ivCheckPurple.visibility = View.VISIBLE
            "orange" -> binding.ivCheckOrange.visibility = View.VISIBLE
            "red" -> binding.ivCheckRed.visibility = View.VISIBLE
            "green" -> binding.ivCheckGreen.visibility = View.VISIBLE
        }
    }

    private fun saveThemeColor(colorKey: String) {
        prefs.edit().putString("theme_color", colorKey).apply()

        // 显示设置页面的标题栏始终保持白色
        binding.toolbar.setBackgroundColor(Color.WHITE)
        binding.toolbar.setTitleTextColor(Color.BLACK)
        binding.toolbar.navigationIcon?.setTint(Color.BLACK)

        Toast.makeText(this, R.string.theme_color_applied, Toast.LENGTH_SHORT).show()
    }

    private fun updateFontSizeDisplay(size: Float) {
        binding.tvCurrentFontSize.text = "${size.toInt()}sp"
        binding.tvPreviewText.textSize = size
    }

    private fun applyFontSize() {
        prefs.edit().putFloat("font_size", currentFontSize).apply()
        Toast.makeText(this, R.string.font_size_applied, Toast.LENGTH_SHORT).show()

        // 立即应用字体大小到当前Activity
        ThemeManager.applyFontSizeToActivity(this)
    }
}
