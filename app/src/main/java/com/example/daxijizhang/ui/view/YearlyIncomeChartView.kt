package com.example.daxijizhang.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.daxijizhang.util.ThemeManager
import kotlin.math.max

data class YearlyIncomeData(
    val year: Int,
    val monthlyIncomes: Map<Int, Double>
) {
    fun getIncome(month: Int): Double = monthlyIncomes[month] ?: 0.0
    
    val maxIncome: Double
        get() = if (monthlyIncomes.isNotEmpty()) monthlyIncomes.values.maxOrNull() ?: 0.0 else 0.0
}

class YearlyIncomeChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: YearlyIncomeData? = null
    private var themeColor: Int = ThemeManager.getThemeColor()
    
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    
    private val baseBarWidthDp = 20f
    private val minBarSpacingDp = 4f
    private val topPaddingDp = 24f
    private val bottomPaddingDp = 24f
    private val horizontalPaddingDp = 8f
    private val baseLabelTextSizeSp = 12f
    private val baseValueTextSizeSp = 10f
    private val valueTopMarginDp = 6f
    
    private var barWidthPx: Float = 0f
    private var barSpacingPx: Float = 0f
    private var topPaddingPx: Float = 0f
    private var bottomPaddingPx: Float = 0f
    private var horizontalPaddingPx: Float = 0f
    private var valueTopMarginPx: Float = 0f
    
    private var textColor: Int = Color.BLACK
    
    init {
        updateTextSizeAndColor()
    }
    
    private fun updateTextSizeAndColor() {
        val density = context.resources.displayMetrics.density
        val fontScale = ThemeManager.getFontScale()
        
        topPaddingPx = topPaddingDp * density
        bottomPaddingPx = bottomPaddingDp * density
        horizontalPaddingPx = horizontalPaddingDp * density
        valueTopMarginPx = valueTopMarginDp * density
        
        labelTextPaint.textSize = baseLabelTextSizeSp * density * fontScale
        textPaint.textSize = baseValueTextSizeSp * density * fontScale
        
        textColor = if (ThemeManager.isDarkMode(context)) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        labelTextPaint.color = textColor
        textPaint.color = textColor
    }
    
    fun setData(data: YearlyIncomeData?) {
        this.data = data
        updateTextSizeAndColor()
        invalidate()
    }
    
    fun setThemeColor(color: Int) {
        this.themeColor = color
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val chartData = data
        val density = context.resources.displayMetrics.density
        
        val availableWidth = width - paddingLeft - paddingRight - 2 * horizontalPaddingPx
        val chartHeight = height - topPaddingPx - bottomPaddingPx
        
        val totalBars = 12
        val minBarWidthPx = 8f * density
        val maxBarWidthPx = baseBarWidthDp * density
        val minSpacingPx = minBarSpacingDp * density
        
        val totalMinSpacing = (totalBars - 1) * minSpacingPx
        val availableForBars = availableWidth - totalMinSpacing
        val calculatedBarWidth = availableForBars / totalBars
        
        barWidthPx = max(minBarWidthPx, calculatedBarWidth.coerceAtMost(maxBarWidthPx))
        
        val totalBarWidth = totalBars * barWidthPx
        val remainingForSpacing = availableWidth - totalBarWidth
        barSpacingPx = if (totalBars > 1) {
            max(minSpacingPx, remainingForSpacing / (totalBars - 1))
        } else {
            0f
        }
        
        val totalChartWidth = totalBarWidth + (totalBars - 1) * barSpacingPx
        val startX = paddingLeft + horizontalPaddingPx + (availableWidth - totalChartWidth) / 2
        
        val maxIncome = if (chartData != null && chartData.maxIncome > 0) {
            chartData.maxIncome
        } else {
            1.0
        }
        
        barPaint.color = themeColor
        
        for (month in 1..12) {
            val income = chartData?.getIncome(month) ?: 0.0
            
            val barHeight = if (income > 0) {
                (income / maxIncome * chartHeight).toFloat()
            } else {
                0f
            }
            
            val col = month - 1
            val barLeft = startX + col * (barWidthPx + barSpacingPx)
            val barRight = barLeft + barWidthPx
            val barBottom = height - bottomPaddingPx
            val barTop = barBottom - barHeight
            
            if (barHeight > 0) {
                val cornerRadius = barWidthPx * 0.15f
                canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, cornerRadius, cornerRadius, barPaint)
            }
            
            val centerX = barLeft + barWidthPx / 2
            canvas.drawText(month.toString(), centerX, height - bottomPaddingPx + labelTextPaint.textSize + 8f, labelTextPaint)
            
            if (income > 0) {
                val valueText = formatValue(income)
                canvas.drawText(valueText, centerX, barTop - valueTopMarginPx, textPaint)
            }
        }
    }
    
    private fun formatValue(value: Double): String {
        val rounded = kotlin.math.round(value * 100) / 100
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format("%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }
}
