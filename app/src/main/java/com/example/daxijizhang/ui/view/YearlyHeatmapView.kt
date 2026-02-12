package com.example.daxijizhang.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.daxijizhang.data.model.HeatmapData
import com.example.daxijizhang.util.ThemeManager
import java.util.Calendar
import kotlin.math.max

data class YearlyHeatmapData(
    val year: Int,
    val monthlyHeatmaps: Map<Int, HeatmapData>
) {
    fun getMonthData(month: Int): HeatmapData? = monthlyHeatmaps[month]
}

class YearlyHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: YearlyHeatmapData? = null
    private var themeColor: Int = ThemeManager.getThemeColor()
    
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val monthLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    
    private val baseGrayColor: Int by lazy {
        if (ThemeManager.isDarkMode(context)) {
            Color.parseColor("#33FFFFFF")
        } else {
            Color.parseColor("#E0E0E0")
        }
    }
    
    private val baseMonthLabelSizeSp = 10f
    private val dotSizeDp = 4f
    private val monthHorizontalSpacingDp = 8f
    private val monthVerticalSpacingDp = 14f
    private val monthPaddingDp = 4f
    private val labelToDotsGapDp = 4f
    
    private var monthLabelSizePx: Float = 0f
    private var dotSizePx: Float = 0f
    private var dotHorizontalSpacingPx: Float = 0f
    private var dotVerticalSpacingPx: Float = 0f
    private var monthHorizontalSpacingPx: Float = 0f
    private var monthVerticalSpacingPx: Float = 0f
    private var monthPaddingPx: Float = 0f
    private var labelToDotsGapPx: Float = 0f
    
    private var textColor: Int = Color.BLACK
    
    init {
        updateTextSizeAndColor()
    }
    
    private fun updateTextSizeAndColor() {
        val density = context.resources.displayMetrics.density
        val fontScale = ThemeManager.getFontScale()
        
        monthLabelSizePx = baseMonthLabelSizeSp * density * fontScale
        dotSizePx = dotSizeDp * density
        monthHorizontalSpacingPx = monthHorizontalSpacingDp * density
        monthVerticalSpacingPx = monthVerticalSpacingDp * density
        monthPaddingPx = monthPaddingDp * density
        labelToDotsGapPx = labelToDotsGapDp * density
        
        monthLabelPaint.textSize = monthLabelSizePx
        
        textColor = if (ThemeManager.isDarkMode(context)) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        monthLabelPaint.color = textColor
    }
    
    fun setData(data: YearlyHeatmapData?) {
        this.data = data
        updateTextSizeAndColor()
        requestLayout()
        invalidate()
    }
    
    fun setThemeColor(color: Int) {
        this.themeColor = color
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        
        val availableWidth = desiredWidth - paddingLeft - paddingRight
        val totalMonthSpacing = 3 * monthHorizontalSpacingPx
        val availableMonthWidth = (availableWidth - totalMonthSpacing) / 4
        
        val dotsAreaWidth = availableMonthWidth - 2 * monthPaddingPx - monthLabelSizePx - labelToDotsGapPx
        dotHorizontalSpacingPx = max(1f, (dotsAreaWidth - 7 * dotSizePx) / 6)
        
        val dotsAreaHeight = dotsAreaWidth * 0.85f
        dotVerticalSpacingPx = max(1f, (dotsAreaHeight - 6 * dotSizePx) / 5)
        
        val monthHeight = monthPaddingPx + monthLabelSizePx + labelToDotsGapPx + 6 * dotSizePx + 5 * dotVerticalSpacingPx + monthPaddingPx
        val totalHeight = 3 * monthHeight + 2 * monthVerticalSpacingPx
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(totalHeight.toInt(), heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val chartData = data
        
        val availableWidth = width - paddingLeft - paddingRight
        val totalMonthSpacing = 3 * monthHorizontalSpacingPx
        val monthWidth = (availableWidth - totalMonthSpacing) / 4
        
        val monthHeight = monthPaddingPx + monthLabelSizePx + labelToDotsGapPx + 6 * dotSizePx + 5 * dotVerticalSpacingPx + monthPaddingPx
        
        val startX = paddingLeft
        
        val globalMaxCount = if (chartData != null) calculateGlobalMaxCount(chartData) else 1
        
        val year = chartData?.year ?: Calendar.getInstance().get(Calendar.YEAR)
        
        for (month in 1..12) {
            val row = (month - 1) / 4
            val col = (month - 1) % 4
            
            val monthLeft = startX + col * (monthWidth + monthHorizontalSpacingPx)
            val monthTop = row * (monthHeight + monthVerticalSpacingPx)
            
            canvas.drawText("${month}月", monthLeft + monthPaddingPx, monthTop + monthPaddingPx + monthLabelSizePx, monthLabelPaint)
            
            val monthData = chartData?.getMonthData(month)
            drawMonthHeatmap(canvas, year, month, monthLeft, monthTop, monthData, globalMaxCount)
        }
    }
    
    private fun calculateGlobalMaxCount(data: YearlyHeatmapData): Int {
        var maxCount = 0
        for (month in 1..12) {
            val monthData = data.getMonthData(month)
            if (monthData != null && monthData.maxCount > maxCount) {
                maxCount = monthData.maxCount
            }
        }
        return max(1, maxCount)
    }
    
    private fun drawMonthHeatmap(canvas: Canvas, year: Int, month: Int, monthLeft: Float, monthTop: Float, monthData: HeatmapData?, globalMaxCount: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = getMondayFirstDayOfWeek(calendar)
        
        val dotsStartX = monthLeft + monthPaddingPx + monthLabelSizePx + labelToDotsGapPx
        val dotsStartY = monthTop + monthPaddingPx + monthLabelSizePx + labelToDotsGapPx
        
        for (day in 1..daysInMonth) {
            calendar.set(year, month - 1, day)
            val dayOfWeek = getMondayFirstDayOfWeek(calendar)
            
            val position = firstDayOfWeek + day - 1
            val row = position / 7
            val col = position % 7
            
            val dotLeft = dotsStartX + col * (dotSizePx + dotHorizontalSpacingPx)
            val dotTop = dotsStartY + row * (dotSizePx + dotVerticalSpacingPx)
            
            val count = monthData?.getCount(day) ?: 0
            
            val color = if (count > 0) {
                val ratio = count.toFloat() / globalMaxCount.toFloat()
                calculateHeatColor(ratio)
            } else {
                baseGrayColor
            }
            
            dotPaint.color = color
            val cornerRadius = dotSizePx * 0.15f
            canvas.drawRoundRect(dotLeft, dotTop, dotLeft + dotSizePx, dotTop + dotSizePx, cornerRadius, cornerRadius, dotPaint)
        }
    }
    
    private fun getMondayFirstDayOfWeek(calendar: Calendar): Int {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            Calendar.SUNDAY -> 6
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0
        }
    }
    
    private fun calculateHeatColor(ratio: Float): Int {
        val alpha = (40 + 215 * ratio).toInt().coerceIn(0, 255)
        return Color.argb(
            alpha,
            Color.red(themeColor),
            Color.green(themeColor),
            Color.blue(themeColor)
        )
    }
}
