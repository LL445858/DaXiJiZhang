package com.example.daxijizhang

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.daxijizhang.databinding.ActivityMainBinding
import com.example.daxijizhang.util.ThemeManager

/**
 * 应用主Activity
 * 管理底部导航栏和Fragment导航
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBackPressHandler()
        
        // 页面加载动画
        playEntranceAnimation()
        
        // 应用主题设置
        ThemeManager.applyTheme(this)
    }

    /**
     * 设置底部导航栏导航逻辑
     */
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
     * 设置返回键处理逻辑
     * 当在用户界面时，返回键退出应用
     * 当在其他界面时，返回键切换到账单界面（首页）
     */
    private fun setupBackPressHandler() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestinationId = navController.currentDestination?.id
                
                when (currentDestinationId) {
                    R.id.navigation_user -> {
                        // 在用户界面时，退出应用
                        finish()
                    }
                    R.id.navigation_bills -> {
                        // 在账单界面时，退出应用
                        finish()
                    }
                    else -> {
                        // 在其他界面时，返回账单界面
                        navController.navigate(R.id.navigation_bills)
                        binding.bottomNav.selectedItemId = R.id.navigation_bills
                    }
                }
            }
        })
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
