package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.window.OnBackInvokedDispatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.daxijizhang.R
import com.example.daxijizhang.data.model.PeriodType
import com.example.daxijizhang.databinding.ActivityMoreSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.PatternLockManager
import com.example.daxijizhang.util.StatisticsStateManager
import com.example.daxijizhang.util.ThemeManager
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

    private var isUpdatingSwitch = false

    private val setPatternLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isUpdatingSwitch = true
        try {
            if (result.resultCode == PatternLockActivity.RESULT_SUCCESS) {
                binding.switchAppLock.isChecked = true
                Toast.makeText(this, getString(R.string.app_lock) + "已开启", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchAppLock.isChecked = false
            }
        } catch (e: Exception) {
            Log.e("MoreSettingsActivity", "Error handling set pattern result", e)
        } finally {
            isUpdatingSwitch = false
        }
    }

    private val verifyDisableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isUpdatingSwitch = true
        try {
            if (result.resultCode == PatternLockActivity.RESULT_SUCCESS) {
                PatternLockManager.clearPattern()
                binding.switchAppLock.isChecked = false
                Toast.makeText(this, getString(R.string.app_lock) + "已关闭", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchAppLock.isChecked = true
            }
        } catch (e: Exception) {
            Log.e("MoreSettingsActivity", "Error handling verify disable result", e)
        } finally {
            isUpdatingSwitch = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            PatternLockManager.init(this)
        } catch (e: Exception) {
            Log.e("MoreSettingsActivity", "Failed to initialize PatternLockManager", e)
        }

        setupStatusBarPadding()
        initViews()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
        setupSortTypeSpinner()
        setupStatisticsRangeSpinner()
        loadAppLockState()
        applyThemeColor()
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.statusBarPlaceholder.updateLayoutParams {
                height = statusBarInsets.top
            }

            binding.contentContainer.setPadding(
                binding.contentContainer.paddingLeft,
                binding.contentContainer.paddingTop,
                binding.contentContainer.paddingRight,
                navigationBarInsets.bottom + binding.contentContainer.paddingTop
            )

            windowInsets
        }
    }

    override fun onResume() {
        super.onResume()
        setupSortTypeSpinner()
        setupStatisticsRangeSpinner()
        loadAppLockState()
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

    private fun loadAppLockState() {
        isUpdatingSwitch = true
        binding.switchAppLock.isChecked = PatternLockManager.isLockEnabled()
        isUpdatingSwitch = false
    }

    private fun applyThemeColor() {
        binding.switchAppLock.thumbTintList = ThemeManager.createSwitchThumbColorStateList()
        binding.switchAppLock.trackTintList = ThemeManager.createSwitchTrackColorStateList()
    }

    private fun setupBackPressHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    private fun setupForwardTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
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
        binding.itemProjectDictionary.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(this, ProjectDictionaryActivity::class.java)
                startActivity(intent)
            }
        }

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

        binding.itemDefaultSortType.setOnClickListener {
            if (binding.spinnerDefaultSortType.isPopupShowing) {
                binding.spinnerDefaultSortType.dismissDropDown()
            } else {
                binding.spinnerDefaultSortType.showDropDown()
            }
        }

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

        binding.itemDefaultStatisticsRange.setOnClickListener {
            if (binding.spinnerDefaultStatisticsRange.isPopupShowing) {
                binding.spinnerDefaultStatisticsRange.dismissDropDown()
            } else {
                binding.spinnerDefaultStatisticsRange.showDropDown()
            }
        }

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener

            try {
                if (isChecked) {
                    val intent = PatternLockActivity.createSetIntent(this)
                    setPatternLauncher.launch(intent)
                } else {
                    if (PatternLockManager.hasPattern()) {
                        val intent = PatternLockActivity.createVerifyDisableIntent(this)
                        verifyDisableLauncher.launch(intent)
                    } else {
                        PatternLockManager.setLockEnabled(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("MoreSettingsActivity", "Error handling switch change", e)
                isUpdatingSwitch = true
                binding.switchAppLock.isChecked = !isChecked
                isUpdatingSwitch = false
                Toast.makeText(this, "操作失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }

        binding.itemAppLock.setOnClickListener {
            binding.switchAppLock.isChecked = !binding.switchAppLock.isChecked
        }
    }
}
