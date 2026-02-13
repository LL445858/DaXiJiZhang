package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import com.example.daxijizhang.R
import com.example.daxijizhang.data.model.PeriodType
import com.example.daxijizhang.databinding.ActivityMoreSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.StatisticsStateManager
import com.example.daxijizhang.util.ViewUtil

class MoreSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityMoreSettingsBinding

    private val statisticsRangeOptions = listOf("月统计", "年统计", "自定义")
    private val statisticsRangeValues = listOf(
        PeriodType.MONTH,
        PeriodType.YEAR,
        PeriodType.CUSTOM
    )

    private val sortTypeOptions = listOf(
        "开始日期（降序）",
        "开始日期（升序）",
        "结束日期（降序）",
        "结束日期（升序）",
        "小区名（字母排序）",
        "金额（降序）",
        "金额（升序）"
    )
    private val sortTypeValues = listOf(
        "START_DATE_DESC",
        "START_DATE_ASC",
        "END_DATE_DESC",
        "END_DATE_ASC",
        "COMMUNITY_ASC",
        "AMOUNT_DESC",
        "AMOUNT_ASC"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
        setupSortTypeSpinner()
        setupStatisticsRangeSpinner()
    }

    override fun onResume() {
        super.onResume()
        setupSortTypeSpinner()
        setupStatisticsRangeSpinner()
    }

    private fun setupSortTypeSpinner() {
        val adapter = ArrayAdapter(this, R.layout.item_dropdown_dark_mode, sortTypeOptions)
        binding.spinnerDefaultSortType.setAdapter(adapter)
        
        val currentType = StatisticsStateManager.getDefaultSortType()
        val currentIndex = sortTypeValues.indexOf(currentType).takeIf { it >= 0 } ?: 0
        binding.spinnerDefaultSortType.setText(sortTypeOptions[currentIndex], false)
    }

    private fun setupStatisticsRangeSpinner() {
        val adapter = ArrayAdapter(this, R.layout.item_dropdown_dark_mode, statisticsRangeOptions)
        binding.spinnerDefaultStatisticsRange.setAdapter(adapter)
        
        val currentType = StatisticsStateManager.getDefaultPeriodType()
        val currentIndex = statisticsRangeValues.indexOf(currentType).takeIf { it >= 0 } ?: 0
        binding.spinnerDefaultStatisticsRange.setText(statisticsRangeOptions[currentIndex], false)
    }

    private fun setupBackPressHandler() {
        // Android 13+ 使用新的预测性返回手势API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            // 低版本使用OnBackPressedDispatcher
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    /**
     * 设置进入动画（前进动画）
     */
    private fun setupForwardTransition() {
        // Android 13+ 使用新的过渡API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    /**
     * 设置返回动画（后退动画）并结束Activity
     */
    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的API
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            // 低版本仍然需要使用旧API
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        finish()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 项目词典 - 跳转到项目词典管理页面
        binding.itemProjectDictionary.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(this, ProjectDictionaryActivity::class.java)
                startActivity(intent)
            }
        }

        // 默认排序方式选择
        binding.spinnerDefaultSortType.setOnItemClickListener { parent, _, position, _ ->
            parent?.let {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(binding.spinnerDefaultSortType.windowToken, 0)
            }

            val selectedType = sortTypeValues[position]
            StatisticsStateManager.setDefaultSortType(selectedType)
            binding.spinnerDefaultSortType.setText(sortTypeOptions[position], false)
            binding.spinnerDefaultSortType.clearFocus()
        }

        // 点击默认排序方式条目时，如果下拉菜单已打开则关闭它
        binding.itemDefaultSortType.setOnClickListener {
            if (binding.spinnerDefaultSortType.isPopupShowing) {
                binding.spinnerDefaultSortType.dismissDropDown()
            } else {
                binding.spinnerDefaultSortType.showDropDown()
            }
        }

        // 默认统计范围选择
        binding.spinnerDefaultStatisticsRange.setOnItemClickListener { parent, _, position, _ ->
            parent?.let {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(binding.spinnerDefaultStatisticsRange.windowToken, 0)
            }

            val selectedType = statisticsRangeValues[position]
            StatisticsStateManager.setDefaultPeriodType(selectedType)
            binding.spinnerDefaultStatisticsRange.setText(statisticsRangeOptions[position], false)
            binding.spinnerDefaultStatisticsRange.clearFocus()
        }

        // 点击默认统计范围条目时，如果下拉菜单已打开则关闭它
        binding.itemDefaultStatisticsRange.setOnClickListener {
            if (binding.spinnerDefaultStatisticsRange.isPopupShowing) {
                binding.spinnerDefaultStatisticsRange.dismissDropDown()
            } else {
                binding.spinnerDefaultStatisticsRange.showDropDown()
            }
        }
    }
}
