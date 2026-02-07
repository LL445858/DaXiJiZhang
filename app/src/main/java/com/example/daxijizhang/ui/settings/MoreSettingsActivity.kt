package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityMoreSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ViewUtil

class MoreSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityMoreSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
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

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 项目词典 - 跳转到项目词典管理页面
        binding.itemProjectDictionary.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(this, ProjectDictionaryActivity::class.java)
                startActivity(intent)
            }
        }
    }
}
