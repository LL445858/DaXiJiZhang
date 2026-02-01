package com.example.daxijizhang

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.daxijizhang.databinding.ActivityMainBinding
import com.example.daxijizhang.util.ViewUtil.fadeIn

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        
        // 页面加载动画
        playEntranceAnimation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // 设置底部导航监听，用于处理导航事件
        binding.bottomNav.setOnItemSelectedListener { item ->
            // 添加触觉反馈
            binding.bottomNav.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            
            when (item.itemId) {
                R.id.navigation_bills -> {
                    navController.navigate(R.id.navigation_bills)
                    true
                }
                R.id.navigation_statistics -> {
                    navController.navigate(R.id.navigation_statistics)
                    true
                }
                R.id.navigation_user -> {
                    navController.navigate(R.id.navigation_user)
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * 播放页面进入动画
     */
    private fun playEntranceAnimation() {
        // 底部导航栏从底部滑入
        binding.bottomNav.translationY = 200f
        binding.bottomNav.alpha = 0f
        binding.bottomNav.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(100)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }
}
