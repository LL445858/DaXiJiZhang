package com.example.daxijizhang.ui.base

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.os.BuildCompat
import com.example.daxijizhang.DaxiApplication
import com.example.daxijizhang.R
import com.example.daxijizhang.util.ThemeManager

/**
 * 基础Activity类
 * 统一处理返回按钮动画和全局字体缩放
 */
abstract class BaseActivity : AppCompatActivity(), DaxiApplication.OnFontScaleChangeListener {

    override fun attachBaseContext(newBase: Context) {
        // 在attachBaseContext中应用字体缩放，确保所有Activity都能正确应用
        val scaledContext = applyFontScaleToContext(newBase)
        super.attachBaseContext(scaledContext)
    }

    /**
     * 应用字体缩放到Context
     * 关键：必须在attachBaseContext中应用，确保所有视图都能正确获取字体缩放
     */
    private fun applyFontScaleToContext(context: Context): Context {
        // 从SharedPreferences读取字体缩放值（存储的是百分比值，如100.0）
        val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        val scale = try {
            val percent = prefs.getFloat("font_size_percent", 100f)
            // 将百分比转换为缩放比例 (100% -> 1.0f)
            (percent / 100f).coerceIn(0.5f, 1.5f)
        } catch (e: Exception) {
            1.0f
        }

        // 创建新的Configuration并应用字体缩放
        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = scale
        
        // 创建新的Context配置
        val newContext = context.createConfigurationContext(configuration)
        
        // 同时更新ThemeManager中的字体缩放值，确保一致性
        ThemeManager.setFontScale(scale)
        
        return newContext
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackPressHandler()
        setupForwardTransition()
        // 注册字体缩放监听器
        (application as? DaxiApplication)?.addFontScaleListener(this)
    }

    /**
     * 字体缩放变化回调
     */
    override fun onFontScaleChanged(scale: Float) {
        // 重新创建Activity以应用新的字体缩放
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注销字体缩放监听器
        (application as? DaxiApplication)?.removeFontScaleListener(this)
    }

    private fun setupBackPressHandler() {
        // Android 13+ 使用新的预测性返回手势API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            // 低版本使用OnBackPressedDispatcher
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    /**
     * 设置进入动画（前进动画）
     */
    private fun setupForwardTransition() {
        // Android 13+ 使用新的过渡API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
        // 低版本在startActivity时使用ActivityOptionsCompat
    }

    /**
     * 设置返回动画（后退动画）并结束Activity
     */
    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的API
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            // 低版本仍然需要使用旧API
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        finish()
    }

    /**
     * 启动Activity并应用前进动画
     */
    protected fun startActivityWithForwardAnimation(intent: android.content.Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 已经在setupForwardTransition中设置了过渡动画
            startActivity(intent)
        } else {
            // 低版本使用ActivityOptionsCompat
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }
    }

    /**
     * 兼容旧版本的finish动画（供子类在特殊情况下使用）
     */
    protected fun finishWithSlideAnimation() {
        finishWithAnimation()
    }
}
