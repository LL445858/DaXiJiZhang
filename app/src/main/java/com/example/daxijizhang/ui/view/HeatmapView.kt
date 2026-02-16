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

class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var heatmapData: HeatmapData? = null
    private var themeColor: Int = ThemeManager.getThemeColor()
    
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val baseGrayColor: Int by lazy {
        if (ThemeManager.isDarkMode(context)) {
            Color.parseColor("#33FFFFFF")
        } else {
            Color.parseColor("#E0E0E0")
        }
    }
    
    private val dotSizeDp = 12f
    private val horizontalSpacingDp = 16f
    private val verticalSpacingDp = 12f
    private val horizontalPaddingDp = 8f
    
    private var dotSizePx: Float = 0f
    private var horizontalSpacingPx: Float = 0f
    private var verticalSpacingPx: Float = 0f
    private var horizontalPaddingPx: Float = 0f
    
    private data class CachedDotPosition(
        val day: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val count: Int
    )
    
    private var cachedPositions: List<CachedDotPosition>? = null
    private var cachedMaxCount: Int = 1
    private var cachedYear: Int = 0
    private var cachedMonth: Int = 0
    private var cachedStartX: Float = 0f
    
    private val reusableCalendar = Calendar.getInstance()
    
    init {
        val density = context.resources.displayMetrics.density
        dotSizePx = dotSizeDp * density
        horizontalSpacingPx = horizontalSpacingDp * density
        verticalSpacingPx = verticalSpacingDp * density
        horizontalPaddingPx = horizontalPaddingDp * density
    }
    
    fun setData(data: HeatmapData?) {
        this.heatmapData = data
        cachedPositions = null
        requestLayout()
        invalidate()
    }
    
    fun setThemeColor(color: Int) {
        this.themeColor = color
        invalidate()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        
        val data = heatmapData
        val year = data?.year ?: reusableCalendar.get(Calendar.YEAR)
        val month = data?.month ?: (reusableCalendar.get(Calendar.MONTH) + 1)
        val rowsNeeded = calculateRowsNeeded(year, month)
        
        val totalDotHeight = rowsNeeded * dotSizePx
        val totalSpacingHeight = if (rowsNeeded > 1) (rowsNeeded - 1) * verticalSpacingPx else 0f
        val desiredHeight = (totalDotHeight + totalSpacingHeight + paddingTop + paddingBottom).toInt()
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
    
    private fun calculateRowsNeeded(year: Int, month: Int): Int {
        reusableCalendar.set(year, month - 1, 1)
        
        val daysInMonth = reusableCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = getMondayFirstDayOfWeek(reusableCalendar)
        
        val lastPosition = firstDayOfWeek + daysInMonth - 1
        return (lastPosition / 7) + 1
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
    
    private fun ensureCachedPositions() {
        val data = heatmapData ?: return
        
        val year = data.year
        val month = data.month
        
        if (cachedPositions != null && cachedYear == year && cachedMonth == month) {
            return
        }
        
        reusableCalendar.set(year, month - 1, 1)
        
        val daysInMonth = reusableCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = getMondayFirstDayOfWeek(reusableCalendar)
        
        val availableWidth = width - paddingLeft - paddingRight - 2 * horizontalPaddingPx
        val totalDotWidth = 7 * dotSizePx
        val totalSpacingWidth = 6 * horizontalSpacingPx
        val startX = paddingLeft + horizontalPaddingPx + (availableWidth - totalDotWidth - totalSpacingWidth) / 2
        
        val maxCount = max(1, data.maxCount)
        
        val positions = mutableListOf<CachedDotPosition>()
        
        for (day in 1..daysInMonth) {
            reusableCalendar.set(year, month - 1, day)
            val dayOfWeek = getMondayFirstDayOfWeek(reusableCalendar)
            
            val position = firstDayOfWeek + day - 1
            val row = position / 7
            val col = position % 7
            
            val left = startX + col * (dotSizePx + horizontalSpacingPx)
            val top = paddingTop + row * (dotSizePx + verticalSpacingPx)
            val right = left + dotSizePx
            val bottom = top + dotSizePx
            
            val count = data.getCount(day)
            
            positions.add(CachedDotPosition(day, left, top, right, bottom, count))
        }
        
        cachedPositions = positions
        cachedMaxCount = maxCount
        cachedYear = year
        cachedMonth = month
        cachedStartX = startX
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val data = heatmapData
        if (data == null) {
            drawEmptyState(canvas)
            return
        }
        
        ensureCachedPositions()
        
        val positions = cachedPositions ?: return
        
        for (pos in positions) {
            val color = if (pos.count > 0) {
                val ratio = pos.count.toFloat() / cachedMaxCount.toFloat()
                calculateHeatColor(ratio)
            } else {
                baseGrayColor
            }
            
            dotPaint.color = color
            val cornerRadius = dotSizePx * 0.15f
            canvas.drawRoundRect(pos.left, pos.top, pos.right, pos.bottom, cornerRadius, cornerRadius, dotPaint)
        }
    }
    
    private fun drawEmptyState(canvas: Canvas) {
        val year = reusableCalendar.get(Calendar.YEAR)
        val month = reusableCalendar.get(Calendar.MONTH) + 1
        
        reusableCalendar.set(year, month - 1, 1)
        
        val daysInMonth = reusableCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = getMondayFirstDayOfWeek(reusableCalendar)
        
        val availableWidth = width - paddingLeft - paddingRight - 2 * horizontalPaddingPx
        val totalDotWidth = 7 * dotSizePx
        val totalSpacingWidth = 6 * horizontalSpacingPx
        val startX = paddingLeft + horizontalPaddingPx + (availableWidth - totalDotWidth - totalSpacingWidth) / 2
        
        for (day in 1..daysInMonth) {
            reusableCalendar.set(year, month - 1, day)
            
            val position = firstDayOfWeek + day - 1
            val row = position / 7
            val col = position % 7
            
            val left = startX + col * (dotSizePx + horizontalSpacingPx)
            val top = paddingTop + row * (dotSizePx + verticalSpacingPx)
            val right = left + dotSizePx
            val bottom = top + dotSizePx
            
            dotPaint.color = baseGrayColor
            val cornerRadius = dotSizePx * 0.15f
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, dotPaint)
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
