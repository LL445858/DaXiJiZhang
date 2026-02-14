package com.example.daxijizhang.ui.view

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.example.daxijizhang.R
import com.example.daxijizhang.util.ThemeManager
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class ModernDatePickerDialog(
    context: Context,
    private val initialDate: Calendar = Calendar.getInstance(),
    private val minDate: Calendar? = null,
    private val maxDate: Calendar? = null,
    private val onDateSelected: (Calendar) -> Unit
) : Dialog(context) {

    private lateinit var npYear: CustomNumberPicker
    private lateinit var npMonth: CustomNumberPicker
    private lateinit var npDay: CustomNumberPicker
    private lateinit var tvTitle: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnConfirm: MaterialButton

    private var currentYear: Int = initialDate.get(Calendar.YEAR)
    private var currentMonth: Int = initialDate.get(Calendar.MONTH) + 1
    private var currentDay: Int = initialDate.get(Calendar.DAY_OF_MONTH)

    private val themeColorListener = object : ThemeManager.OnThemeColorChangeListener {
        override fun onThemeColorChanged(color: Int) {
            applyThemeColor(color)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_date_picker)
        
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setWindowAnimations(R.style.DialogAnimationFast)
        }

        initViews()
        setupPickers()
        setupListeners()
        applyThemeColor(ThemeManager.getThemeColor())
        updateSelectedDateDisplay()
        
        ThemeManager.addThemeColorChangeListener(themeColorListener)
    }

    private fun initViews() {
        npYear = findViewById(R.id.np_year)
        npMonth = findViewById(R.id.np_month)
        npDay = findViewById(R.id.np_day)
        tvTitle = findViewById(R.id.tv_title)
        tvSelectedDate = findViewById(R.id.tv_selected_date)
        btnCancel = findViewById(R.id.btn_cancel)
        btnConfirm = findViewById(R.id.btn_confirm)
    }

    private fun setupPickers() {
        val currentYearNow = Calendar.getInstance().get(Calendar.YEAR)
        
        npYear.setMinValue(minDate?.get(Calendar.YEAR) ?: 2000)
        npYear.setMaxValue(maxDate?.get(Calendar.YEAR) ?: currentYearNow + 10)
        npYear.setValue(currentYear)
        npYear.setWrapSelectorWheel(false)
        
        npMonth.setMinValue(1)
        npMonth.setMaxValue(12)
        npMonth.setValue(currentMonth)
        npMonth.setWrapSelectorWheel(false)
        
        updateDayPickerRange()
        npDay.setValue(currentDay)
        npDay.setWrapSelectorWheel(false)
        
        npYear.setOnValueChangedListener { newValue ->
            currentYear = newValue
            updateDayPickerRange()
            updateSelectedDateDisplay()
        }
        
        npMonth.setOnValueChangedListener { newValue ->
            currentMonth = newValue
            updateDayPickerRange()
            updateSelectedDateDisplay()
        }
        
        npDay.setOnValueChangedListener { newValue ->
            currentDay = newValue
            updateSelectedDateDisplay()
        }
    }

    private fun updateDayPickerRange() {
        val maxDay = when (currentMonth) {
            2 -> if (currentYear % 4 == 0 && (currentYear % 100 != 0 || currentYear % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        
        npDay.setMinValue(1)
        npDay.setMaxValue(maxDay)
        
        if (currentDay > maxDay) {
            currentDay = maxDay
            npDay.setValue(maxDay)
        }
        
        npDay.invalidate()
    }

    private fun updateSelectedDateDisplay() {
        val displayText = String.format("%d年%02d月%02d日", currentYear, currentMonth, currentDay)
        tvSelectedDate.text = displayText
    }

    private fun setupListeners() {
        btnCancel.setOnClickListener {
            dismiss()
        }
        
        btnConfirm.setOnClickListener {
            val selectedCalendar = Calendar.getInstance().apply {
                set(currentYear, currentMonth - 1, currentDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (minDate != null && selectedCalendar.before(minDate)) {
                return@setOnClickListener
            }
            
            if (maxDate != null && selectedCalendar.after(maxDate)) {
                return@setOnClickListener
            }
            
            onDateSelected(selectedCalendar)
            dismiss()
        }
    }

    private fun applyThemeColor(color: Int) {
        btnCancel.setTextColor(color)
        btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        npYear.setThemeColor(color)
        npMonth.setThemeColor(color)
        npDay.setThemeColor(color)
    }

    override fun show() {
        super.show()
        applyThemeColor(ThemeManager.getThemeColor())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeThemeColorChangeListener(themeColorListener)
    }

    companion object {
        fun show(
            context: Context,
            initialDate: Calendar = Calendar.getInstance(),
            minDate: Calendar? = null,
            maxDate: Calendar? = null,
            onDateSelected: (Calendar) -> Unit
        ): ModernDatePickerDialog {
            val dialog = ModernDatePickerDialog(context, initialDate, minDate, maxDate, onDateSelected)
            dialog.show()
            return dialog
        }
    }
}
