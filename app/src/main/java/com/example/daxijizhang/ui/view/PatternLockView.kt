package com.example.daxijizhang.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.daxijizhang.R
import com.example.daxijizhang.util.ThemeManager
import kotlin.math.sqrt

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_DOT_COUNT = 3
        private const val DOT_RADIUS_RATIO = 0.064f
        private const val INNER_DOT_RADIUS_RATIO = 0.032f
        private const val LINE_WIDTH_RATIO = 0.024f
    }

    private var dotCount = DEFAULT_DOT_COUNT
    private var dots = mutableListOf<Dot>()
    private var selectedDots = mutableListOf<Dot>()
    private var isDrawing = false
    private var currentX = 0f
    private var currentY = 0f

    private var dotRadius = 0f
    private var innerDotRadius = 0f
    private var lineWidth = 0f
    private var cellWidth = 0f
    private var padding = 0f

    private var themeColor: Int = Color.BLUE
    private var dotColor: Int = Color.GRAY
    private var dotSelectedColor: Int = Color.BLUE
    private var lineColor: Int = Color.BLUE
    private var errorColor: Int = Color.RED

    private var showError = false
    private var errorAnimator: ValueAnimator? = null
    private var isErrorAnimationActive = false
    private var isViewEnabled = true

    private var onPatternListener: OnPatternListener? = null

    private val dotPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    data class Dot(val row: Int, val col: Int, var x: Float = 0f, var y: Float = 0f, var selected: Boolean = false)

    interface OnPatternListener {
        fun onPatternStart()
        fun onPatternProgress(pattern: List<Dot>)
        fun onPatternComplete(pattern: List<Dot>)
        fun onPatternCleared() {}
    }

    init {
        initAttributes(context, attrs)
        initDots()
        isSaveEnabled = true
    }

    private fun initAttributes(context: Context, attrs: AttributeSet?) {
        themeColor = ThemeManager.getThemeColor()
        val isDarkMode = ThemeManager.isDarkMode(context)
        dotColor = if (isDarkMode) {
            Color.parseColor("#d0d0d0")
        } else {
            Color.parseColor("#505050")
        }
        dotSelectedColor = themeColor
        lineColor = themeColor
        errorColor = context.getColor(R.color.error)
    }

    private fun initDots() {
        dots.clear()
        for (row in 0 until dotCount) {
            for (col in 0 until dotCount) {
                dots.add(Dot(row, col))
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h)
        padding = size * 0.1f
        cellWidth = (size - 2 * padding) / (dotCount - 1)
        dotRadius = size * DOT_RADIUS_RATIO
        innerDotRadius = size * INNER_DOT_RADIUS_RATIO
        lineWidth = size * LINE_WIDTH_RATIO

        updateDotPositions()
    }

    private fun updateDotPositions() {
        for (dot in dots) {
            dot.x = padding + dot.col * cellWidth
            dot.y = padding + dot.row * cellWidth
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawDots(canvas)
        drawLines(canvas)
        drawCurrentLine(canvas)
    }

    private fun drawDots(canvas: Canvas) {
        for (dot in dots) {
            val color = when {
                showError -> errorColor
                dot.selected -> dotSelectedColor
                else -> dotColor
            }

            dotPaint.color = color
            canvas.drawCircle(dot.x, dot.y, dotRadius, dotPaint)

            if (dot.selected) {
                dotPaint.color = Color.WHITE
                canvas.drawCircle(dot.x, dot.y, innerDotRadius, dotPaint)
            }
        }
    }

    private fun drawLines(canvas: Canvas) {
        if (selectedDots.size < 2) return

        linePaint.strokeWidth = lineWidth
        linePaint.color = if (showError) errorColor else getTransparentColor(lineColor, 0.6f)

        for (i in 0 until selectedDots.size - 1) {
            val start = selectedDots[i]
            val end = selectedDots[i + 1]
            canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
        }
    }

    private fun drawCurrentLine(canvas: Canvas) {
        if (!isDrawing || selectedDots.isEmpty()) return

        linePaint.strokeWidth = lineWidth
        linePaint.color = if (showError) errorColor else getTransparentColor(lineColor, 0.6f)

        val lastDot = selectedDots.last()
        canvas.drawLine(lastDot.x, lastDot.y, currentX, currentY, linePaint)
    }

    private fun getTransparentColor(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt()
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(a, r, g, b)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isViewEnabled || isErrorAnimationActive) {
            return false
        }

        currentX = event.x
        currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleActionMove()
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleActionUp()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handleActionUp()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun handleActionDown() {
        clearPattern()
        isDrawing = true
        showError = false

        val hitDot = findDotAt(currentX, currentY)
        if (hitDot != null) {
            selectDot(hitDot)
            onPatternListener?.onPatternStart()
        }
    }

    private fun handleActionMove() {
        if (!isDrawing) return

        val hitDot = findDotAt(currentX, currentY)
        if (hitDot != null && !hitDot.selected) {
            selectDot(hitDot)
            onPatternListener?.onPatternProgress(selectedDots.toList())
        }

        invalidate()
    }

    private fun handleActionUp() {
        if (!isDrawing) return

        isDrawing = false
        onPatternListener?.onPatternComplete(selectedDots.toList())
        invalidate()
    }

    private fun findDotAt(x: Float, y: Float): Dot? {
        val hitRadius = dotRadius * 1.5f
        for (dot in dots) {
            val distance = sqrt((x - dot.x) * (x - dot.x) + (y - dot.y) * (y - dot.y))
            if (distance <= hitRadius) {
                return dot
            }
        }
        return null
    }

    private fun selectDot(dot: Dot) {
        dot.selected = true
        if (!selectedDots.contains(dot)) {
            selectedDots.add(dot)
        }
    }

    fun clearPattern() {
        for (dot in dots) {
            dot.selected = false
        }
        selectedDots.clear()
        showError = false
        isDrawing = false
        invalidate()
    }

    fun setShowError(show: Boolean) {
        showError = show
        if (show) {
            startErrorAnimation()
        }
        invalidate()
    }

    private fun startErrorAnimation() {
        isErrorAnimationActive = true
        errorAnimator?.cancel()
        errorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isErrorAnimationActive = false
                    showError = false
                    clearPattern()
                    onPatternListener?.onPatternCleared()
                }
            })
            addUpdateListener {
                invalidate()
            }
            start()
        }
    }

    private fun cancelErrorAnimation() {
        isErrorAnimationActive = false
        errorAnimator?.cancel()
        errorAnimator = null
        showError = false
    }

    fun setOnPatternListener(listener: OnPatternListener) {
        onPatternListener = listener
    }

    fun getPattern(): List<Dot> {
        return selectedDots.toList()
    }

    fun getPatternString(): String {
        return selectedDots.joinToString(",") { "${it.row * dotCount + it.col}" }
    }

    fun setPatternFromString(patternString: String): Boolean {
        if (patternString.isBlank()) return false

        return try {
            val indices = patternString.split(",").mapNotNull { 
                it.trim().toIntOrNull() 
            }
            if (indices.isEmpty()) return false
            
            clearPattern()
            for (index in indices) {
                if (index in 0 until dots.size) {
                    selectDot(dots[index])
                }
            }
            invalidate()
            true
        } catch (e: Exception) {
            clearPattern()
            false
        }
    }

    fun updateThemeColor() {
        themeColor = ThemeManager.getThemeColor()
        dotSelectedColor = themeColor
        lineColor = themeColor
        val isDarkMode = ThemeManager.isDarkMode(context)
        dotColor = if (isDarkMode) {
            Color.parseColor("#d0d0d0")
        } else {
            Color.parseColor("#505050")
        }
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isViewEnabled = enabled
        if (!enabled) {
            clearPattern()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelErrorAnimation()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            this.patternString = this@PatternLockView.getPatternString()
            this.isDrawing = this@PatternLockView.isDrawing
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            if (state.patternString.isNotEmpty()) {
                setPatternFromString(state.patternString)
            }
            isDrawing = state.isDrawing
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    class SavedState : BaseSavedState {
        var patternString: String = ""
        var isDrawing: Boolean = false

        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            patternString = parcel.readString() ?: ""
            isDrawing = parcel.readByte() != 0.toByte()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(patternString)
            out.writeByte(if (isDrawing) 1 else 0)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
}
