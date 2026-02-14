package com.example.daxijizhang.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.core.animation.doOnEnd
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

class CustomNumberPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var minValue: Int = 0
    private var maxValue: Int = 100
    private var value: Int = 0
    private var currentValue: Float = 0f
    private var wrapSelectorWheel: Boolean = false

    private var onValueChangedListener: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedTextSizeSp = 17.6f
    private val nearTextSizeSp = 14.4f
    private val farTextSizeSp = 11.2f
    private val itemHeightDp = 38.4f

    private var fontScale: Float = 1f

    private var lastTouchY: Float = 0f
    private var isDragging = false
    private var velocityTracker: android.view.VelocityTracker? = null
    private var currentAnimator: ValueAnimator? = null

    private val flingInterpolator: Interpolator = DecelerateInterpolator(1.0f)
    private val snapInterpolator: Interpolator = DecelerateInterpolator(1.5f)
    private val flingDuration = 300L
    private val snapDuration = 200L

    private var selectedTextColor: Int = Color.BLACK
    private var normalTextColor: Int = Color.GRAY
    private var themeColor: Int = Color.BLACK

    init {
        updateColors()
        updateFontScale()
    }

    private fun updateFontScale() {
        fontScale = context.resources.configuration.fontScale
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp * fontScale, resources.displayMetrics)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private fun updateColors() {
        val isDarkMode = when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        selectedTextColor = if (isDarkMode) Color.WHITE else Color.BLACK
        normalTextColor = if (isDarkMode) Color.GRAY else Color.DKGRAY
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (dpToPx(itemHeightDp) * 5).toInt()
        setMeasuredDimension(
            resolveSize(dpToPx(100f).toInt(), widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val itemHeightPx = dpToPx(itemHeightDp)

        val centerValue = currentValue
        val fractionOffset = centerValue - centerValue.toInt()

        for (offset in -3..3) {
            val itemValue = centerValue + offset
            val intValue = floor(itemValue).toInt()

            if (!isValidValue(intValue) && !wrapSelectorWheel) continue

            val distanceFromCenter = abs(offset - fractionOffset)
            if (distanceFromCenter > 3.5f) continue

            val itemY = centerY + offset * itemHeightPx - fractionOffset * itemHeightPx

            val (textSize, alpha) = calculateTextStyle(distanceFromCenter)

            paint.textSize = textSize
            paint.color = if (distanceFromCenter < 0.5f) themeColor else normalTextColor
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)

            val displayValue = if (wrapSelectorWheel) {
                wrapValue(intValue)
            } else {
                intValue
            }

            val text = displayValue.toString()
            paint.textAlign = Paint.Align.CENTER
            val textY = itemY + textSize / 3
            canvas.drawText(text, centerX, textY, paint)
        }
    }

    private fun calculateTextStyle(distanceFromCenter: Float): Pair<Float, Float> {
        return when {
            distanceFromCenter < 0.5f -> {
                val factor = distanceFromCenter * 2
                val size = spToPx(selectedTextSizeSp - (selectedTextSizeSp - nearTextSizeSp) * factor)
                val alpha = 1f - 0.2f * factor
                Pair(size, alpha)
            }
            distanceFromCenter < 1.5f -> {
                val factor = (distanceFromCenter - 0.5f)
                val size = spToPx(nearTextSizeSp - (nearTextSizeSp - farTextSizeSp) * factor)
                val alpha = 0.8f - 0.3f * factor
                Pair(size, alpha)
            }
            else -> {
                val factor = (distanceFromCenter - 1.5f).coerceAtMost(1f)
                val size = spToPx(farTextSizeSp * (1 - factor * 0.3f))
                val alpha = 0.5f - 0.2f * factor
                Pair(size, alpha.coerceAtLeast(0.2f))
            }
        }
    }

    private fun isValidValue(v: Int): Boolean {
        return v in minValue..maxValue
    }

    private fun wrapValue(v: Int): Int {
        val range = maxValue - minValue + 1
        return ((v - minValue) % range + range) % range + minValue
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false
                currentAnimator?.cancel()
                velocityTracker?.recycle()
                velocityTracker = android.view.VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val deltaY = event.y - lastTouchY

                if (!isDragging && abs(deltaY) > 10) {
                    isDragging = true
                }

                if (isDragging) {
                    val itemHeightPx = dpToPx(itemHeightDp)
                    currentValue -= deltaY / itemHeightPx

                    if (!wrapSelectorWheel) {
                        currentValue = currentValue.coerceIn(minValue.toFloat(), maxValue.toFloat())
                    }

                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocity = velocityTracker?.yVelocity ?: 0f

                if (isDragging) {
                    fling(velocity)
                } else {
                    handleClick(event.y)
                }

                velocityTracker?.recycle()
                velocityTracker = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleClick(y: Float) {
        val centerY = height / 2f
        val itemHeightPx = dpToPx(itemHeightDp)
        val clickedOffset = (y - centerY) / itemHeightPx
        val clickedValue = (currentValue + clickedOffset).roundToInt()

        if (isValidValue(clickedValue)) {
            animateToValue(clickedValue)
        }
    }

    private fun fling(velocity: Float) {
        val itemHeightPx = dpToPx(itemHeightDp)
        val velocityItems = velocity / itemHeightPx / 15

        var target = (currentValue - velocityItems).roundToInt()
        target = clampValue(target)

        animateToValue(target)
    }

    private fun animateToValue(targetValue: Int) {
        val currentIntValue = currentValue.roundToInt()
        if (targetValue == currentIntValue && abs(currentValue - targetValue) < 0.01f) {
            snapToInteger()
            return
        }

        currentAnimator?.cancel()

        val startValue = currentValue
        val target = targetValue.toFloat()

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = flingDuration
            interpolator = flingInterpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                currentValue = startValue + (target - startValue) * fraction
                invalidate()
            }
            doOnEnd {
                snapToInteger()
            }
            start()
        }
    }

    private fun snapToInteger() {
        val target = currentValue.roundToInt().toFloat()

        if (abs(currentValue - target) < 0.01f) {
            currentValue = target
            value = target.toInt()
            onValueChangedListener?.invoke(target.toInt())
            invalidate()
            return
        }

        currentAnimator?.cancel()

        val startValue = currentValue

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = snapDuration
            interpolator = snapInterpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                currentValue = startValue + (target - startValue) * fraction
                invalidate()
            }
            doOnEnd {
                currentValue = target
                value = target.toInt()
                onValueChangedListener?.invoke(target.toInt())
                invalidate()
            }
            start()
        }
    }

    private fun clampValue(v: Int): Int {
        return when {
            v < minValue -> if (wrapSelectorWheel) wrapValue(v) else minValue
            v > maxValue -> if (wrapSelectorWheel) wrapValue(v) else maxValue
            else -> v
        }
    }

    fun setMinValue(min: Int) {
        minValue = min
        if (value < minValue && !wrapSelectorWheel) {
            value = minValue
            currentValue = minValue.toFloat()
        }
        invalidate()
    }

    fun setMaxValue(max: Int) {
        maxValue = max
        if (value > maxValue && !wrapSelectorWheel) {
            value = maxValue
            currentValue = maxValue.toFloat()
        }
        invalidate()
    }

    fun getValue(): Int = value

    fun setValue(newValue: Int) {
        val clampedValue = clampValue(newValue)
        if (value != clampedValue) {
            value = clampedValue
            currentValue = clampedValue.toFloat()
            invalidate()
        }
    }

    fun setWrapSelectorWheel(wrap: Boolean) {
        wrapSelectorWheel = wrap
        invalidate()
    }

    fun setOnValueChangedListener(listener: (Int) -> Unit) {
        onValueChangedListener = listener
    }

    fun setThemeColor(color: Int) {
        themeColor = color
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateFontScale()
        updateColors()
        invalidate()
    }
}
