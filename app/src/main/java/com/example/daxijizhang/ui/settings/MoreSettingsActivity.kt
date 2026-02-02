package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityRemoteSyncBinding
import com.example.daxijizhang.util.ViewUtil

class MoreSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteSyncBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteSyncBinding.inflate(layoutInflater)
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
        // 项目词典 - 当前仅做界面展示
        binding.itemProjectDictionary.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                // TODO: 项目词典功能待实现
                Toast.makeText(this, "项目词典功能正在开发中", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
