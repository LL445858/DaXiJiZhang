package com.example.daxijizhang.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import androidx.core.animation.doOnEnd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * View性能优化工具类
 * 提供防抖点击、点击动画、加载动画等功能
 */
object ViewUtil {

    private const val DEFAULT_DEBOUNCE_TIME = 300L
    private const val CLICK_SCALE = 0.95f
    private const val ANIMATION_DURATION = 150L
    private const val FADE_DURATION = 200L

    /**
     * 防抖点击监听器
     * @param debounceTime 防抖时间（毫秒）
     * @param onClick 点击回调
     */
    fun setOnSingleClickListener(
        view: View,
        debounceTime: Long = DEFAULT_DEBOUNCE_TIME,
        onClick: () -> Unit
    ) {
        var lastClickTime = 0L
        view.setOnClickListener { _ ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceTime) {
                lastClickTime = currentTime
                onClick()
            }
        }
    }

    /**
     * 带有点击反馈的防抖点击监听器
     * 包含缩放动画和触觉反馈
     */
    fun View.setOnOptimizedClickListener(
        debounceTime: Long = DEFAULT_DEBOUNCE_TIME,
        enableHaptic: Boolean = true,
        onClick: (View) -> Unit
    ) {
        var lastClickTime = 0L
        setOnClickListener { view ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceTime) {
                lastClickTime = currentTime

                // 触觉反馈
                if (enableHaptic) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }

                // 点击动画
                view.animate()
                    .scaleX(CLICK_SCALE)
                    .scaleY(CLICK_SCALE)
                    .setDuration(50)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    .start()

                onClick(view)
            }
        }
    }

    /**
     * 带按压效果的触摸监听器
     * 按下时缩小，松开时恢复
     */
    fun View.setPressEffect(scale: Float = CLICK_SCALE) {
        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(50)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
            false
        }
    }

    /**
     * 淡入动画
     * @param duration 动画时长
     * @param startDelay 延迟开始时间
     * @param onEnd 动画结束回调
     */
    fun View.fadeIn(
        duration: Long = FADE_DURATION,
        startDelay: Long = 0,
        onEnd: (() -> Unit)? = null
    ) {
        if (visibility == View.VISIBLE && alpha == 1f) return

        visibility = View.VISIBLE
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * 淡出动画
     * @param duration 动画时长
     * @param hideAfter 动画结束后是否隐藏View
     * @param onEnd 动画结束回调
     */
    fun View.fadeOut(
        duration: Long = FADE_DURATION,
        hideAfter: Boolean = true,
        onEnd: (() -> Unit)? = null
    ) {
        if (visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }

        animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (hideAfter) visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    /**
     * 从底部滑入动画
     */
    fun View.slideInFromBottom(
        duration: Long = 300,
        startDelay: Long = 0,
        onEnd: (() -> Unit)? = null
    ) {
        visibility = View.VISIBLE
        translationY = height.toFloat()
        alpha = 0f

        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * 向底部滑出动画
     */
    fun View.slideOutToBottom(
        duration: Long = 300,
        hideAfter: Boolean = true,
        onEnd: (() -> Unit)? = null
    ) {
        animate()
            .translationY(height.toFloat())
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                if (hideAfter) visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    /**
     * 缩放进入动画
     */
    fun View.scaleIn(
        duration: Long = 200,
        startDelay: Long = 0,
        onEnd: (() -> Unit)? = null
    ) {
        visibility = View.VISIBLE
        scaleX = 0f
        scaleY = 0f
        alpha = 0f

        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onEnd?.invoke() }
            .start()
    }

    /**
     * 缩放退出动画
     */
    fun View.scaleOut(
        duration: Long = 200,
        hideAfter: Boolean = true,
        onEnd: (() -> Unit)? = null
    ) {
        animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                if (hideAfter) visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    /**
     * 脉冲动画（用于提示）
     */
    fun View.pulseAnimation(repeatCount: Int = 2) {
        val scaleUp = ScaleAnimation(
            1f, 1.1f, 1f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 150
            repeatMode = Animation.REVERSE
            this.repeatCount = repeatCount
        }
        startAnimation(scaleUp)
    }

    /**
     * 摇晃动画（用于错误提示）
     */
    fun View.shakeAnimation() {
        val shake = ObjectAnimator.ofFloat(this, "translationX", 0f, 20f, -20f, 20f, -20f, 10f, -10f, 5f, -5f, 0f)
        shake.apply {
            duration = 500
            start()
        }
    }

    /**
     * 顺序显示多个View的动画
     * @param views 要显示的View列表
     * @param delayBetween 每个View之间的延迟
     */
    fun staggeredFadeIn(
        views: List<View>,
        duration: Long = FADE_DURATION,
        delayBetween: Long = 50
    ) {
        views.forEachIndexed { index, view ->
            view.fadeIn(duration, index * delayBetween)
        }
    }

    /**
     * 显示加载状态（旋转动画）
     * @param show 是否显示
     */
    fun View.showLoading(show: Boolean) {
        if (show) {
            visibility = View.VISIBLE
            animate()
                .rotationBy(360f)
                .setDuration(1000)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (visibility == View.VISIBLE) {
                        showLoading(true)
                    }
                }
                .start()
        } else {
            animate().cancel()
            visibility = View.GONE
        }
    }

    /**
     * 批量设置View的可见性
     */
    fun setViewsVisibility(views: List<View>, visibility: Int) {
        views.forEach { it.visibility = visibility }
    }

    /**
     * 批量设置View的启用状态
     */
    fun setViewsEnabled(views: List<View>, enabled: Boolean) {
        views.forEach { it.isEnabled = enabled }
    }

    /**
     * 应用点击动画并执行操作
     * 用于Activity中设置点击监听器
     */
    fun applyClickAnimation(view: View, action: () -> Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        view.animate()
            .scaleX(CLICK_SCALE)
            .scaleY(CLICK_SCALE)
            .setDuration(50)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    /**
     * 延迟执行操作（用于优化UI响应）
     */
    fun postDelayed(delayMillis: Long, action: () -> Unit): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            delay(delayMillis)
            action()
        }
    }

    /**
     * 优化RecyclerView滚动性能
     * 在滚动时暂停图片加载等操作
     */
    fun androidx.recyclerview.widget.RecyclerView.optimizeScrollPerformance() {
        addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE -> {
                        // 停止滚动，恢复所有操作
                    }
                    androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING,
                    androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_SETTLING -> {
                        // 正在滚动，暂停非必要操作
                    }
                }
            }
        })
    }

    /**
     * 创建平滑的过渡动画
     * 用于Activity或Fragment切换
     */
    fun createTransitionAnimation(
        enterDuration: Long = 300,
        exitDuration: Long = 200
    ): Pair<Animation, Animation> {
        val enterAnim = AlphaAnimation(0f, 1f).apply {
            duration = enterDuration
            interpolator = DecelerateInterpolator()
        }
        val exitAnim = AlphaAnimation(1f, 0f).apply {
            duration = exitDuration
            interpolator = DecelerateInterpolator()
        }
        return Pair(enterAnim, exitAnim)
    }
}

/**
 * 节流函数
 * 用于限制高频事件的触发频率
 */
class Throttler(private val delayMillis: Long = 300L) {
    private var lastExecutionTime = 0L

    fun throttle(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastExecutionTime >= delayMillis) {
            lastExecutionTime = currentTime
            action()
        }
    }
}

/**
 * 防抖函数
 * 用于延迟执行操作，如果在延迟期间再次触发则重新计时
 */
class Debouncer(private val delayMillis: Long = 300L) {
    private var job: Job? = null

    fun debounce(scope: CoroutineScope, action: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(delayMillis)
            action()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
