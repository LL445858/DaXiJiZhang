package com.example.daxijizhang.ui.statistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.PaymentWithBillInfo
import com.example.daxijizhang.data.model.PeriodType
import com.example.daxijizhang.data.model.StatisticsData
import com.example.daxijizhang.data.repository.StatisticsRepository
import com.example.daxijizhang.databinding.FragmentStatisticsBinding
import com.example.daxijizhang.ui.view.CustomNumberPicker
import com.example.daxijizhang.ui.view.HeatmapView
import com.example.daxijizhang.ui.view.YearlyHeatmapView
import com.example.daxijizhang.ui.view.YearlyIncomeChartView
import com.example.daxijizhang.util.StatisticsStateManager
import com.example.daxijizhang.util.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: StatisticsViewModel

    // 当前状态
    private var currentPeriodType = PeriodType.MONTH
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
    private var customStartDate: Calendar = Calendar.getInstance()
    private var customEndDate: Calendar = Calendar.getInstance()

    // 临时日期（用于自定义模式的双向选择）
    private var tempStartDate: Calendar = Calendar.getInstance()
    private var tempEndDate: Calendar = Calendar.getInstance()

    // 防抖处理
    private val handler = Handler(Looper.getMainLooper())
    private var periodSelectorRunnable: Runnable? = null
    private var dateSelectorRunnable: Runnable? = null
    private val DEBOUNCE_DELAY = 300L

    // 预加载的弹窗视图
    private var periodSelectorDialog: AlertDialog? = null
    private var yearMonthPickerDialog: AlertDialog? = null
    private var yearPickerDialog: AlertDialog? = null

    // 视图引用 - 标题栏
    private lateinit var llPeriodSelector: LinearLayout
    private lateinit var tvPeriodType: TextView
    private lateinit var ivPeriodArrow: View
    private lateinit var llDateSelector: LinearLayout
    private lateinit var tvDateDisplay: TextView
    private lateinit var llCustomDateRange: LinearLayout
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView

    // 视图引用 - 内容区域
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollContent: View
    private lateinit var tvWorkStatsLine1: TextView
    private lateinit var tvWorkStatsLine2: TextView
    private lateinit var tvWorkStatsLine3: TextView
    private lateinit var llPaymentList: LinearLayout
    private lateinit var cardHeatmap: View
    private lateinit var heatmapView: HeatmapView
    private lateinit var cardYearlyIncome: View
    private lateinit var yearlyIncomeChart: YearlyIncomeChartView
    private lateinit var cardYearlyHeatmap: View
    private lateinit var yearlyHeatmapView: YearlyHeatmapView

    // 日期格式化
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel
        val database = AppDatabase.getDatabase(requireContext())
        val repository = StatisticsRepository(database)
        viewModel = ViewModelProvider(this, StatisticsViewModelFactory(repository))
            .get(StatisticsViewModel::class.java)

        // 从状态管理器恢复状态
        restoreStateFromManager()

        setupStatusBarPadding()
        initViews()
        setupClickListeners()
        setupObservers()
        updateDateDisplay()

        // 预加载弹窗
        preloadDialogs()

        // 加载数据
        loadStatisticsData()
    }

    /**
     * 从状态管理器恢复状态
     */
    private fun restoreStateFromManager() {
        StatisticsStateManager.initIfNeeded()
        currentPeriodType = StatisticsStateManager.currentPeriodType
        selectedYear = StatisticsStateManager.selectedYear
        selectedMonth = StatisticsStateManager.selectedMonth
        customStartDate.timeInMillis = StatisticsStateManager.customStartDate.timeInMillis
        customEndDate.timeInMillis = StatisticsStateManager.customEndDate.timeInMillis
        tempStartDate.timeInMillis = customStartDate.timeInMillis
        tempEndDate.timeInMillis = customEndDate.timeInMillis
    }

    /**
     * 保存状态到状态管理器
     */
    private fun saveStateToManager() {
        when (currentPeriodType) {
            PeriodType.MONTH -> StatisticsStateManager.saveMonthState(selectedYear, selectedMonth)
            PeriodType.YEAR -> StatisticsStateManager.saveYearState(selectedYear)
            PeriodType.CUSTOM -> StatisticsStateManager.saveCustomState(customStartDate, customEndDate)
        }
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.statusBarPlaceholder.updateLayoutParams {
                height = insets.top
            }
            windowInsets
        }
    }

    private fun initViews() {
        // 获取标题栏视图引用
        val headerView = binding.root.findViewById<View>(R.id.statistics_header)
        llPeriodSelector = headerView.findViewById(R.id.ll_period_selector)
        tvPeriodType = headerView.findViewById(R.id.tv_period_type)
        ivPeriodArrow = headerView.findViewById(R.id.iv_period_arrow)
        llDateSelector = headerView.findViewById(R.id.ll_date_selector)
        tvDateDisplay = headerView.findViewById(R.id.tv_date_display)
        llCustomDateRange = headerView.findViewById(R.id.ll_custom_date_range)
        tvStartDate = headerView.findViewById(R.id.tv_start_date)
        tvEndDate = headerView.findViewById(R.id.tv_end_date)

        // 获取内容区域视图引用
        val contentView = binding.root.findViewById<View>(R.id.statistics_content)
        progressBar = contentView.findViewById(R.id.progress_bar)
        scrollContent = contentView.findViewById(R.id.scroll_content)
        tvWorkStatsLine1 = contentView.findViewById(R.id.tv_work_stats_line1)
        tvWorkStatsLine2 = contentView.findViewById(R.id.tv_work_stats_line2)
        tvWorkStatsLine3 = contentView.findViewById(R.id.tv_work_stats_line3)
        llPaymentList = contentView.findViewById(R.id.ll_payment_list)
        cardHeatmap = contentView.findViewById(R.id.card_heatmap)
        heatmapView = contentView.findViewById(R.id.heatmap_view)
        cardYearlyIncome = contentView.findViewById(R.id.card_yearly_income)
        yearlyIncomeChart = contentView.findViewById(R.id.yearly_income_chart)
        cardYearlyHeatmap = contentView.findViewById(R.id.card_yearly_heatmap)
        yearlyHeatmapView = contentView.findViewById(R.id.yearly_heatmap_view)

        // 应用主题颜色到标题栏日期文本
        applyHeaderThemeColor()

        // 更新周期类型显示（从状态管理器恢复后）
        updatePeriodTypeDisplay()
        
        // 更新热力图可见性
        updateHeatmapVisibility()
    }

    /**
     * 更新周期类型显示
     */
    private fun updatePeriodTypeDisplay() {
        tvPeriodType.text = when (currentPeriodType) {
            PeriodType.MONTH -> "月统计"
            PeriodType.YEAR -> "年统计"
            PeriodType.CUSTOM -> "自定义"
        }
    }

    /**
     * 应用主题颜色到标题栏日期文本
     */
    private fun applyHeaderThemeColor() {
        val primaryColor = ThemeManager.getThemeColor()
        tvDateDisplay.setTextColor(primaryColor)
        tvStartDate.setTextColor(primaryColor)
        tvEndDate.setTextColor(primaryColor)
    }

    private fun setupObservers() {
        viewModel.statisticsData.observe(viewLifecycleOwner) { data ->
            updateStatisticsUI(data)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (!isLoading) {
                scrollContent.visibility = View.VISIBLE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                showSnackbar(it)
                viewModel.clearError()
            }
        }
        
        viewModel.heatmapData.observe(viewLifecycleOwner) { data ->
            heatmapView.setThemeColor(ThemeManager.getThemeColor())
            heatmapView.setData(data)
        }
        
        viewModel.yearlyIncomeData.observe(viewLifecycleOwner) { data ->
            yearlyIncomeChart.setThemeColor(ThemeManager.getThemeColor())
            yearlyIncomeChart.setData(data)
        }
        
        viewModel.yearlyHeatmapData.observe(viewLifecycleOwner) { data ->
            yearlyHeatmapView.setThemeColor(ThemeManager.getThemeColor())
            yearlyHeatmapView.setData(data)
        }
    }

    private fun setupClickListeners() {
        // 左侧周期选择器点击
        llPeriodSelector.setOnClickListener {
            debounceClick(periodSelectorRunnable) {
                showPeriodTypeDialog()
            }
        }

        // 右侧日期选择器点击
        llDateSelector.setOnClickListener {
            debounceClick(dateSelectorRunnable) {
                when (currentPeriodType) {
                    PeriodType.MONTH -> showMonthPickerDialog()
                    PeriodType.YEAR -> showYearPickerDialog()
                    PeriodType.CUSTOM -> {
                        // 自定义模式下不处理整体点击，由子视图处理
                    }
                }
            }
        }

        // 自定义模式下的起始日期点击
        tvStartDate.setOnClickListener {
            debounceClick(dateSelectorRunnable) {
                showDatePickerDialog(isStartDate = true)
            }
        }

        // 自定义模式下的结束日期点击
        tvEndDate.setOnClickListener {
            debounceClick(dateSelectorRunnable) {
                showDatePickerDialog(isStartDate = false)
            }
        }
    }

    private fun debounceClick(runnable: Runnable?, action: () -> Unit) {
        runnable?.let { handler.removeCallbacks(it) }
        val newRunnable = Runnable { action() }
        when (runnable) {
            periodSelectorRunnable -> periodSelectorRunnable = newRunnable
            dateSelectorRunnable -> dateSelectorRunnable = newRunnable
        }
        handler.postDelayed(newRunnable, DEBOUNCE_DELAY)
    }

    /**
     * 加载统计数据
     */
    private fun loadStatisticsData() {
        when (currentPeriodType) {
            PeriodType.MONTH -> viewModel.loadMonthStatistics(selectedYear, selectedMonth)
            PeriodType.YEAR -> viewModel.loadYearStatistics(selectedYear)
            PeriodType.CUSTOM -> viewModel.loadCustomStatistics(customStartDate.time, customEndDate.time)
        }
    }

    /**
     * 更新统计UI
     */
    private fun updateStatisticsUI(data: StatisticsData) {
        val primaryColor = ThemeManager.getThemeColor()

        // 更新干活统计 - 使用主题颜色高亮数字
        // 第一行：期间天数、开始和结束的项目数
        tvWorkStatsLine1.text = createSpannableWithColoredNumbers(
            "在此${data.periodDays}天期间，共开始了${data.startedProjects}家活，结束了${data.endedProjects}家活",
            listOf(data.periodDays.toString(), data.startedProjects.toString(), data.endedProjects.toString()),
            primaryColor
        )

        // 平均每家用时：整个数字（包括小数点）均使用主题色
        val averageDaysStr = String.format("%.1f", data.averageDays)
        tvWorkStatsLine2.text = createSpannableWithColoredNumbers(
            "共从头到尾完成了${data.completedProjects}家活，平均每家用时${averageDaysStr}天",
            listOf(data.completedProjects.toString(), averageDaysStr),
            primaryColor
        )

        tvWorkStatsLine3.text = createSpannableWithColoredNumbers(
            "共收到了${data.totalPayments}笔支付，总计${String.format("%.2f", data.totalPaymentAmount)}元",
            listOf(data.totalPayments.toString(), String.format("%.2f", data.totalPaymentAmount)),
            primaryColor
        )

        // 更新支付列表
        updatePaymentList(data.topPayments)
    }

    /**
     * 创建带颜色数字的SpannableString
     */
    private fun createSpannableWithColoredNumbers(
        text: String,
        numbers: List<String>,
        color: Int
    ): SpannableString {
        val spannable = SpannableString(text)
        var startIndex = 0

        numbers.forEach { number ->
            val index = text.indexOf(number, startIndex)
            if (index >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    index,
                    index + number.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = index + number.length
            }
        }

        return spannable
    }

    /**
     * 创建带颜色数字的SpannableString（用于平均每家用时）
     * 小数点前使用主题色，小数点及小数点后使用黑色
     */
    private fun createSpannableWithColoredNumbersForAverageDays(
        text: String,
        completedProjects: String,
        integerPart: String,
        decimalPart: String,
        primaryColor: Int,
        blackColor: Int
    ): SpannableString {
        val spannable = SpannableString(text)

        // 高亮完成项目数（主题色）
        val completedIndex = text.indexOf(completedProjects)
        if (completedIndex >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(primaryColor),
                completedIndex,
                completedIndex + completedProjects.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 高亮平均每家用时的整数部分（主题色）
        val integerIndex = text.indexOf(integerPart, completedIndex + completedProjects.length)
        if (integerIndex >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(primaryColor),
                integerIndex,
                integerIndex + integerPart.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 高亮小数点及小数部分（黑色）
        if (decimalPart.isNotEmpty()) {
            val decimalIndex = text.indexOf(decimalPart, integerIndex + integerPart.length)
            if (decimalIndex >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(blackColor),
                    decimalIndex,
                    decimalIndex + decimalPart.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }

    /**
     * 更新支付列表
     */
    private fun updatePaymentList(payments: List<PaymentWithBillInfo>) {
        llPaymentList.removeAllViews()

        if (payments.isEmpty()) {
            // 显示空提示
            val emptyView = TextView(requireContext()).apply {
                text = "暂无支付记录"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_hint, null))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            llPaymentList.addView(emptyView)
            return
        }

        payments.forEach { payment ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_payment_statistics, llPaymentList, false)

            val tvDate = itemView.findViewById<TextView>(R.id.tv_payment_date)
            val tvAmount = itemView.findViewById<TextView>(R.id.tv_payment_amount)
            val tvAddress = itemView.findViewById<TextView>(R.id.tv_payment_address)

            tvDate.text = dateFormat.format(payment.paymentDate)
            tvAmount.text = "¥${String.format("%.2f", payment.amount)}"
            tvAmount.setTextColor(ThemeManager.getThemeColor())
            tvAddress.text = payment.getFormattedAddress()

            llPaymentList.addView(itemView)
        }
    }

    /**
     * 预加载弹窗以提高响应速度
     */
    private fun preloadDialogs() {
        initPeriodSelectorDialog()
        initYearMonthPickerDialog()
        initYearPickerDialog()
    }

    /**
     * 初始化周期选择弹窗
     */
    private fun initPeriodSelectorDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_period_selector, null)

        val tvMonthOption = dialogView.findViewById<TextView>(R.id.tv_month_option)
        val tvYearOption = dialogView.findViewById<TextView>(R.id.tv_year_option)
        val tvCustomOption = dialogView.findViewById<TextView>(R.id.tv_custom_option)

        periodSelectorDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        periodSelectorDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        tvMonthOption.setOnClickListener {
            setPeriodType(PeriodType.MONTH)
            periodSelectorDialog?.dismiss()
        }

        tvYearOption.setOnClickListener {
            setPeriodType(PeriodType.YEAR)
            periodSelectorDialog?.dismiss()
        }

        tvCustomOption.setOnClickListener {
            setPeriodType(PeriodType.CUSTOM)
            periodSelectorDialog?.dismiss()
        }
    }

    /**
     * 显示统计周期类型选择弹窗
     */
    private fun showPeriodTypeDialog() {
        if (periodSelectorDialog == null) {
            initPeriodSelectorDialog()
        }
        periodSelectorDialog?.show()

        // 设置弹窗宽度为屏幕宽度的70%
        periodSelectorDialog?.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.7).toInt()
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * 初始化年月选择弹窗
     */
    private fun initYearMonthPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_year_month_picker, null)

        val npYear = dialogView.findViewById<CustomNumberPicker>(R.id.np_year)
        val npMonth = dialogView.findViewById<CustomNumberPicker>(R.id.np_month)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // 先设置min/max，再设置当前值
        npYear.setMinValue(2000)
        npYear.setMaxValue(currentYear)
        npYear.setWrapSelectorWheel(false)
        npYear.setValue(selectedYear)

        // 根据当前选中的年份设置月份范围
        npMonth.setMinValue(1)
        if (selectedYear == currentYear) {
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
            npMonth.setMaxValue(currentMonth)
            // 如果当前选中的月份大于当前月份，重置为当前月份
            if (selectedMonth > currentMonth) {
                selectedMonth = currentMonth
            }
        } else {
            npMonth.setMaxValue(12)
        }
        npMonth.setWrapSelectorWheel(false)
        npMonth.setValue(selectedMonth)

        yearMonthPickerDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        yearMonthPickerDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 应用主题颜色到按钮
        val primaryColor = ThemeManager.getThemeColor()
        btnCancel.setTextColor(primaryColor)
        btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)

        btnCancel.setOnClickListener {
            yearMonthPickerDialog?.dismiss()
        }

        btnConfirm.setOnClickListener {
            selectedYear = npYear.getValue()
            selectedMonth = npMonth.getValue()
            saveStateToManager()
            updateDateDisplay()
            loadStatisticsData()
            yearMonthPickerDialog?.dismiss()
        }

        npYear.setOnValueChangedListener { newYear ->
            updateMonthPickerRange(npMonth, newYear, currentYear)
        }
    }

    /**
     * 显示年月选择弹窗（月统计模式）
     */
    private fun showMonthPickerDialog() {
        if (yearMonthPickerDialog == null) {
            initYearMonthPickerDialog()
        }

        val npYear = yearMonthPickerDialog?.findViewById<CustomNumberPicker>(R.id.np_year)
        val npMonth = yearMonthPickerDialog?.findViewById<CustomNumberPicker>(R.id.np_month)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear?.setValue(selectedYear)
        updateMonthPickerRange(npMonth, selectedYear, currentYear)
        npMonth?.setValue(selectedMonth)

        yearMonthPickerDialog?.show()
    }

    private fun updateMonthPickerRange(npMonth: CustomNumberPicker?, selectedYear: Int, currentYear: Int) {
        npMonth?.let {
            if (selectedYear == currentYear) {
                val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                it.setMaxValue(currentMonth)
                if (it.getValue() > currentMonth) {
                    it.setValue(currentMonth)
                }
            } else {
                // 非当前年份，显示完整的12个月
                it.setMaxValue(12)
                // 如果之前被限制在较小的月份范围，重置为1月或保持当前值
                if (it.getValue() > 12) {
                    it.setValue(12)
                }
            }
            // 强制刷新显示
            it.invalidate()
        }
    }

    /**
     * 初始化年份选择弹窗
     */
    private fun initYearPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_year_picker, null)

        val npYear = dialogView.findViewById<CustomNumberPicker>(R.id.np_year)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // 先设置min/max，再设置当前值
        npYear.setMinValue(2000)
        npYear.setMaxValue(currentYear)
        npYear.setWrapSelectorWheel(false)
        npYear.setValue(selectedYear)

        yearPickerDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        yearPickerDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 应用主题颜色到按钮
        val primaryColor = ThemeManager.getThemeColor()
        btnCancel.setTextColor(primaryColor)
        btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)

        btnCancel.setOnClickListener {
            yearPickerDialog?.dismiss()
        }

        btnConfirm.setOnClickListener {
            selectedYear = npYear.getValue()
            saveStateToManager()
            updateDateDisplay()
            loadStatisticsData()
            yearPickerDialog?.dismiss()
        }
    }

    /**
     * 显示年份选择弹窗（年统计模式）
     */
    private fun showYearPickerDialog() {
        if (yearPickerDialog == null) {
            initYearPickerDialog()
        }

        val npYear = yearPickerDialog?.findViewById<CustomNumberPicker>(R.id.np_year)
        npYear?.setValue(selectedYear)

        yearPickerDialog?.show()
    }

    /**
     * 设置统计周期类型
     */
    private fun setPeriodType(type: PeriodType) {
        currentPeriodType = type
        tvPeriodType.text = when (type) {
            PeriodType.MONTH -> "月统计"
            PeriodType.YEAR -> "年统计"
            PeriodType.CUSTOM -> "自定义"
        }
        
        if (type == PeriodType.CUSTOM) {
            StatisticsStateManager.ensureCustomDateInitialized()
            customStartDate.timeInMillis = StatisticsStateManager.customStartDate.timeInMillis
            customEndDate.timeInMillis = StatisticsStateManager.customEndDate.timeInMillis
            tempStartDate.timeInMillis = customStartDate.timeInMillis
            tempEndDate.timeInMillis = customEndDate.timeInMillis
        }
        
        updateHeatmapVisibility()
        saveStateToManager()
        updateDateDisplay()
        loadStatisticsData()
    }
    
    private fun updateHeatmapVisibility() {
        cardHeatmap.visibility = if (currentPeriodType == PeriodType.MONTH) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        cardYearlyIncome.visibility = if (currentPeriodType == PeriodType.YEAR) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        cardYearlyHeatmap.visibility = if (currentPeriodType == PeriodType.YEAR) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * 更新日期显示
     */
    private fun updateDateDisplay() {
        when (currentPeriodType) {
            PeriodType.MONTH -> {
                tvDateDisplay.visibility = View.VISIBLE
                llCustomDateRange.visibility = View.GONE
                tvDateDisplay.text = String.format("%d年%02d月", selectedYear, selectedMonth)
            }
            PeriodType.YEAR -> {
                tvDateDisplay.visibility = View.VISIBLE
                llCustomDateRange.visibility = View.GONE
                tvDateDisplay.text = String.format("%d年", selectedYear)
            }
            PeriodType.CUSTOM -> {
                tvDateDisplay.visibility = View.GONE
                llCustomDateRange.visibility = View.VISIBLE
                updateCustomDateDisplay()
            }
        }
    }

    /**
     * 更新自定义日期范围显示
     */
    private fun updateCustomDateDisplay() {
        val startYear = customStartDate.get(Calendar.YEAR)
        val startMonth = customStartDate.get(Calendar.MONTH) + 1
        val startDay = customStartDate.get(Calendar.DAY_OF_MONTH)
        tvStartDate.text = String.format("%d年%02d月%02d日", startYear, startMonth, startDay)

        val endYear = customEndDate.get(Calendar.YEAR)
        val endMonth = customEndDate.get(Calendar.MONTH) + 1
        val endDay = customEndDate.get(Calendar.DAY_OF_MONTH)
        tvEndDate.text = String.format("%d年%02d月%02d日", endYear, endMonth, endDay)
    }

    /**
     * 显示日期选择弹窗（自定义模式）
     */
    private fun showDatePickerDialog(isStartDate: Boolean) {
        tempStartDate.timeInMillis = customStartDate.timeInMillis
        tempEndDate.timeInMillis = customEndDate.timeInMillis

        val calendar = if (isStartDate) tempStartDate else tempEndDate
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val newCalendar = Calendar.getInstance()
                newCalendar.set(selectedYear, selectedMonth, selectedDay)

                if (isStartDate) {
                    tempStartDate.timeInMillis = newCalendar.timeInMillis
                } else {
                    tempEndDate.timeInMillis = newCalendar.timeInMillis
                }

                if (validateCustomDates(isStartDate)) {
                    customStartDate.timeInMillis = tempStartDate.timeInMillis
                    customEndDate.timeInMillis = tempEndDate.timeInMillis
                    saveStateToManager()
                    updateCustomDateDisplay()
                    loadStatisticsData()
                }
            },
            year, month, day
        )

        val datePicker = datePickerDialog.datePicker
        if (isStartDate) {
            // 起始日期可以等于结束日期，所以maxDate不设限制或设为结束日期
            datePicker.maxDate = customEndDate.timeInMillis
        } else {
            // 结束日期不能早于起始日期
            datePicker.minDate = customStartDate.timeInMillis
        }

        // 设置按钮颜色为主题色
        datePickerDialog.setOnShowListener {
            val positiveButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)
            val negativeButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)
            val primaryColor = ThemeManager.getThemeColor()
            positiveButton.setTextColor(primaryColor)
            negativeButton.setTextColor(primaryColor)
        }

        datePickerDialog.show()
    }

    /**
     * 验证自定义日期范围
     * @param isSelectingStartDate 是否正在选择起始日期
     * @return true 验证通过，false 验证失败
     */
    private fun validateCustomDates(isSelectingStartDate: Boolean): Boolean {
        // 清除时间部分，只比较日期
        val startCal = tempStartDate.clone() as Calendar
        val endCal = tempEndDate.clone() as Calendar

        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        endCal.set(Calendar.HOUR_OF_DAY, 0)
        endCal.set(Calendar.MINUTE, 0)
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)

        // 允许同一天
        if (startCal.timeInMillis == endCal.timeInMillis) {
            return true
        }

        // 如果正在选择起始日期，且起始日期大于结束日期，显示相应提示
        if (isSelectingStartDate && startCal.after(endCal)) {
            showSnackbar("起始日期需要小于或等于结束日期")
            return false
        }

        // 如果正在选择结束日期，且结束日期小于起始日期，显示相应提示
        if (!isSelectingStartDate && endCal.before(startCal)) {
            showSnackbar("结束日期需要大于或等于起始日期")
            return false
        }

        return true
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        periodSelectorDialog?.dismiss()
        periodSelectorDialog = null
        yearMonthPickerDialog?.dismiss()
        yearMonthPickerDialog = null
        yearPickerDialog?.dismiss()
        yearPickerDialog = null
        _binding = null
    }
}
