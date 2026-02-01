package com.example.daxijizhang.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import android.view.View
import com.example.daxijizhang.R

/**
 * Activity过渡动画工具类
 * 使用AndroidX ActivityOptions和Android 13+新API替代弃用的overridePendingTransition
 */
object ActivityTransitionUtil {

    /**
     * 启动Activity并应用从右向左滑入的动画（前进）
     * 使用ActivityOptionsCompat.makeCustomAnimation实现
     */
    fun startActivityWithSlideRight(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context is Activity) {
            // Android 13+ 使用新的API在Activity中设置过渡动画
            context.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            context.startActivity(intent)
        } else {
            // 低版本使用ActivityOptionsCompat
            val options = ActivityOptionsCompat.makeCustomAnimation(
                context,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            context.startActivity(intent, options.toBundle())
        }
    }

    /**
     * 启动Activity并应用从右向左滑入的动画（前进）- 带共享元素
     */
    fun startActivityWithSlideRight(context: Context, intent: Intent, sharedElement: View, sharedElementName: String) {
        val pair = Pair(sharedElement, sharedElementName)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            context as Activity,
            pair
        )
        context.startActivity(intent, options.toBundle())
    }

    /**
     * 结束Activity并应用从左向右滑出的动画（返回）
     * Android 13+ 使用overrideActivityTransition，低版本使用overridePendingTransition
     */
    fun finishWithSlideLeft(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的API
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            // 低版本仍然需要使用旧API
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        activity.finish()
    }

    /**
     * 在Fragment中启动Activity并应用前进动画
     */
    fun startActivityFromFragment(fragment: androidx.fragment.app.Fragment, intent: Intent) {
        val context = fragment.requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context is Activity) {
            // Android 13+ 使用新的API
            context.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            fragment.startActivity(intent)
        } else {
            // 低版本使用ActivityOptionsCompat
            val options = ActivityOptionsCompat.makeCustomAnimation(
                context,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            fragment.startActivity(intent, options.toBundle())
        }
    }

    /**
     * 获取前进动画的ActivityOptions
     */
    fun getForwardTransitionOptions(context: Context): ActivityOptionsCompat {
        return ActivityOptionsCompat.makeCustomAnimation(
            context,
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
    }

    /**
     * 获取返回动画的ActivityOptions
     */
    fun getBackwardTransitionOptions(context: Context): ActivityOptionsCompat {
        return ActivityOptionsCompat.makeCustomAnimation(
            context,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }

    /**
     * 为Activity设置进入动画（在onCreate中调用）
     * 适用于Android 13+，在startActivity前设置过渡动画
     */
    fun setupEnterTransition(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    /**
     * 为Activity设置退出动画（在finish前调用）
     * 适用于Android 13+
     */
    fun setupExitTransition(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
    }
}
