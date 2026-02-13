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
import androidx.fragment.app.activityViewModels
import com.example.daxijizhang.R
import com.example.daxijizhang.DaxiApplication
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

class StatisticsFragment : Fragment(), ThemeManager.OnThemeColorChangeListener {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatisticsViewModel by activityViewModels {
        val app = requireActivity().application as DaxiApplication
        val database = AppDatabase.getDatabase(app)
        StatisticsViewModelFactory(StatisticsRepository(database))
    }

    private var currentPeriodType = PeriodType.MONTH
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
    private var customStartDate: Calendar = Calendar.getInstance()
    private var customEndDate: Calendar = Calendar.getInstance()

    private var tempStartDate: Calendar = Calendar.getInstance()
    private var tempEndDate: Calendar = Calendar.getInstance()

    private val handler = Handler(Looper.getMainLooper())
    private var dateSelectorRunnable: Runnable? = null
    private val DATE_DEBOUNCE_DELAY = 150L

    private var periodSelectorDialog: AlertDialog? = null
    private var yearMonthPickerDialog: AlertDialog? = null
    private var yearPickerDialog: AlertDialog? = null
    private var isDialogsPreloaded = false

    private lateinit var llPeriodSelector: LinearLayout
    private lateinit var tvPeriodType: TextView
    private lateinit var ivPeriodArrow: View
    private lateinit var llDateSelector: LinearLayout
    private lateinit var tvDateDisplay: TextView
    private lateinit var llCustomDateRange: LinearLayout
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView

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

        restoreStateFromManager()

        setupStatusBarPadding()
        initViews()
        setupClickListeners()
        setupObservers()
        updateDateDisplay()

        preloadDialogs()

        loadStatisticsData()
        
        viewModel.observeDataChanges(viewLifecycleOwner)
        
        ThemeManager.addThemeColorChangeListener(this)
    }

    override fun onThemeColorChanged(color: Int) {
        applyThemeColor()
        applyHeaderThemeColor()
        viewModel.statisticsData.value?.let { updateStatisticsUI(it) }
    }

    private fun applyThemeColor() {
        val themeColor = ThemeManager.getThemeColor()
        yearlyIncomeChart?.setThemeColor(themeColor)
        yearlyHeatmapView?.setThemeColor(themeColor)
        heatmapView?.setThemeColor(themeColor)
        updatePaymentListThemeColor()
    }
    
    private fun updatePaymentListThemeColor() {
        val themeColor = ThemeManager.getThemeColor()
        for (i in 0 until llPaymentList.childCount) {
            val child = llPaymentList.getChildAt(i)
            val tvAmount = child.findViewById<TextView>(R.id.tv_payment_amount)
            tvAmount?.setTextColor(themeColor)
        }
    }

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
        val headerView = binding.root.findViewById<View>(R.id.statistics_header)
        llPeriodSelector = headerView.findViewById(R.id.ll_period_selector)
        tvPeriodType = headerView.findViewById(R.id.tv_period_type)
        ivPeriodArrow = headerView.findViewById(R.id.iv_period_arrow)
        llDateSelector = headerView.findViewById(R.id.ll_date_selector)
        tvDateDisplay = headerView.findViewById(R.id.tv_date_display)
        llCustomDateRange = headerView.findViewById(R.id.ll_custom_date_range)
        tvStartDate = headerView.findViewById(R.id.tv_start_date)
        tvEndDate = headerView.findViewById(R.id.tv_end_date)

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

        applyHeaderThemeColor()

        updatePeriodTypeDisplay()
        
        updateHeatmapVisibility()
    }

    private fun updatePeriodTypeDisplay() {
        tvPeriodType.text = when (currentPeriodType) {
            PeriodType.MONTH -> "月统计"
            PeriodType.YEAR -> "年统计"
            PeriodType.CUSTOM -> "自定义"
        }
    }

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
        llPeriodSelector.setOnClickListener {
            showPeriodTypeDialog()
        }

        llDateSelector.setOnClickListener {
            debounceDateClick {
                when (currentPeriodType) {
                    PeriodType.MONTH -> showMonthPickerDialog()
                    PeriodType.YEAR -> showYearPickerDialog()
                    PeriodType.CUSTOM -> {
                    }
                }
            }
        }

        tvStartDate.setOnClickListener {
            debounceDateClick {
                showDatePickerDialog(isStartDate = true)
            }
        }

        tvEndDate.setOnClickListener {
            debounceDateClick {
                showDatePickerDialog(isStartDate = false)
            }
        }
    }

    private fun debounceDateClick(action: () -> Unit) {
        dateSelectorRunnable?.let { handler.removeCallbacks(it) }
        val newRunnable = Runnable { action() }
        dateSelectorRunnable = newRunnable
        handler.postDelayed(newRunnable, DATE_DEBOUNCE_DELAY)
    }

    private fun loadStatisticsData() {
        when (currentPeriodType) {
            PeriodType.MONTH -> viewModel.loadMonthStatistics(selectedYear, selectedMonth)
            PeriodType.YEAR -> viewModel.loadYearStatistics(selectedYear)
            PeriodType.CUSTOM -> viewModel.loadCustomStatistics(customStartDate.time, customEndDate.time)
        }
    }

    private fun updateStatisticsUI(data: StatisticsData) {
        val primaryColor = ThemeManager.getThemeColor()

        tvWorkStatsLine1.text = createSpannableWithColoredNumbers(
            "在此${data.periodDays}天期间，共开始了${data.startedProjects}家活，结束了${data.endedProjects}家活",
            listOf(data.periodDays.toString(), data.startedProjects.toString(), data.endedProjects.toString()),
            primaryColor
        )

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

        updatePaymentList(data.topPayments)
    }

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

    private fun updatePaymentList(payments: List<PaymentWithBillInfo>) {
        llPaymentList.removeAllViews()

        if (payments.isEmpty()) {
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

    private fun preloadDialogs() {
        if (isDialogsPreloaded) return
        
        handler.post {
            try {
                initPeriodSelectorDialog()
                initYearMonthPickerDialog()
                initYearPickerDialog()
                isDialogsPreloaded = true
            } catch (e: Exception) {
                android.util.Log.e("StatisticsFragment", "Error preloading dialogs", e)
            }
        }
    }

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

    private fun showPeriodTypeDialog() {
        if (periodSelectorDialog == null) {
            initPeriodSelectorDialog()
        }
        
        periodSelectorDialog?.apply {
            window?.setWindowAnimations(R.style.DialogAnimationFast)
            show()
            
            window?.let { window ->
                val displayMetrics = resources.displayMetrics
                val width = (displayMetrics.widthPixels * 0.7).toInt()
                window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun initYearMonthPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_year_month_picker, null)

        val npYear = dialogView.findViewById<CustomNumberPicker>(R.id.np_year)
        val npMonth = dialogView.findViewById<CustomNumberPicker>(R.id.np_month)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.setMinValue(2000)
        npYear.setMaxValue(currentYear)
        npYear.setWrapSelectorWheel(false)
        npYear.setValue(selectedYear)

        npMonth.setMinValue(1)
        if (selectedYear == currentYear) {
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
            npMonth.setMaxValue(currentMonth)
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

    private fun showMonthPickerDialog() {
        if (yearMonthPickerDialog == null) {
            initYearMonthPickerDialog()
        }

        val npYear = yearMonthPickerDialog?.findViewById<CustomNumberPicker>(R.id.np_year)
        val npMonth = yearMonthPickerDialog?.findViewById<CustomNumberPicker>(R.id.np_month)
        val btnCancel = yearMonthPickerDialog?.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = yearMonthPickerDialog?.findViewById<MaterialButton>(R.id.btn_confirm)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear?.setValue(selectedYear)
        updateMonthPickerRange(npMonth, selectedYear, currentYear)
        npMonth?.setValue(selectedMonth)
        
        val primaryColor = ThemeManager.getThemeColor()
        btnCancel?.setTextColor(primaryColor)
        btnConfirm?.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)

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
                it.setMaxValue(12)
                if (it.getValue() > 12) {
                    it.setValue(12)
                }
            }
            it.invalidate()
        }
    }

    private fun initYearPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_year_picker, null)

        val npYear = dialogView.findViewById<CustomNumberPicker>(R.id.np_year)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.setMinValue(2000)
        npYear.setMaxValue(currentYear)
        npYear.setWrapSelectorWheel(false)
        npYear.setValue(selectedYear)

        yearPickerDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        yearPickerDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

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

    private fun showYearPickerDialog() {
        if (yearPickerDialog == null) {
            initYearPickerDialog()
        }

        val npYear = yearPickerDialog?.findViewById<CustomNumberPicker>(R.id.np_year)
        val btnCancel = yearPickerDialog?.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnConfirm = yearPickerDialog?.findViewById<MaterialButton>(R.id.btn_confirm)
        
        npYear?.setValue(selectedYear)
        
        val primaryColor = ThemeManager.getThemeColor()
        btnCancel?.setTextColor(primaryColor)
        btnConfirm?.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)

        yearPickerDialog?.show()
    }

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
            datePicker.maxDate = customEndDate.timeInMillis
        } else {
            datePicker.minDate = customStartDate.timeInMillis
        }

        datePickerDialog.setOnShowListener {
            val positiveButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)
            val negativeButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)
            val primaryColor = ThemeManager.getThemeColor()
            positiveButton.setTextColor(primaryColor)
            negativeButton.setTextColor(primaryColor)
        }

        datePickerDialog.show()
    }

    private fun validateCustomDates(isSelectingStartDate: Boolean): Boolean {
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

        if (startCal.timeInMillis == endCal.timeInMillis) {
            return true
        }

        if (isSelectingStartDate && startCal.after(endCal)) {
            showSnackbar("起始日期需要小于或等于结束日期")
            return false
        }

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
        ThemeManager.removeThemeColorChangeListener(this)
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
