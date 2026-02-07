package com.example.daxijizhang

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.daxijizhang.databinding.ActivityMainBinding
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.WebDAVUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 应用主Activity
 * 管理底部导航栏和Fragment导航
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: BillRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupStatusBar()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_settings", MODE_PRIVATE)

        initRepository()
        setupNavigation()
        setupBackPressHandler()

        playEntranceAnimation()

        // 注册到Application
        (application as? DaxiApplication)?.registerMainActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 从Application注销
        (application as? DaxiApplication)?.unregisterMainActivity()
        performAutoSync()
    }

    private fun initRepository() {
        val database = AppDatabase.getDatabase(applicationContext)
        repository = BillRepository(
            database.billDao(),
            database.billItemDao(),
            database.paymentRecordDao()
        )
    }
    
    /**
     * 设置状态栏颜色，根据深色模式动态调整
     */
    private fun setupStatusBar() {
        window.apply {
            val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            // 根据深色模式设置状态栏颜色
            statusBarColor = if (isDarkMode) {
                ContextCompat.getColor(this@MainActivity, R.color.status_bar)
            } else {
                Color.WHITE
            }

            // 设置状态栏图标颜色
            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode
            }

            // 允许内容延伸到状态栏下方
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
    }

    /**
     * 根据目标页面更新状态栏颜色
     * 用户界面使用纯黑色，其他页面使用浅黑色
     */
    private fun updateStatusBarForDestination(destinationId: Int) {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.apply {
            statusBarColor = when {
                destinationId == R.id.navigation_user -> {
                    // 用户界面：深色模式使用纯黑色，浅色模式使用浅灰色（背景色）
                    if (isDarkMode) Color.BLACK else ContextCompat.getColor(this@MainActivity, R.color.background)
                }
                isDarkMode -> ContextCompat.getColor(this@MainActivity, R.color.status_bar) // 其他页面深色模式使用浅黑色
                else -> Color.WHITE // 其他页面浅色模式使用白色
            }
        }
    }

    /**
     * 设置底部导航栏导航逻辑
     */
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // 设置底部导航栏选中颜色为主题色
        applyBottomNavThemeColor()

        // 设置底部导航监听，用于处理导航事件
        binding.bottomNav.setOnItemSelectedListener { item ->
            // 添加触觉反馈
            binding.bottomNav.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

            when (item.itemId) {
                R.id.navigation_bills -> {
                    updateStatusBarForDestination(R.id.navigation_bills)
                    navController.navigate(R.id.navigation_bills)
                    true
                }
                R.id.navigation_statistics -> {
                    updateStatusBarForDestination(R.id.navigation_statistics)
                    navController.navigate(R.id.navigation_statistics)
                    true
                }
                R.id.navigation_user -> {
                    updateStatusBarForDestination(R.id.navigation_user)
                    navController.navigate(R.id.navigation_user)
                    true
                }
                else -> false
            }
        }

        // 监听导航变化，更新状态栏颜色
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateStatusBarForDestination(destination.id)
        }
    }

    /**
     * 应用底部导航栏主题颜色
     */
    private fun applyBottomNavThemeColor() {
        val themeColor = ThemeManager.getThemeColor()
        // 创建颜色状态列表
        val colorStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                themeColor,
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        )
        binding.bottomNav.itemIconTintList = colorStateList
        binding.bottomNav.itemTextColor = colorStateList
    }

    /**
     * 重新应用主题颜色（当主题颜色变化时调用）
     */
    fun reapplyThemeColor() {
        applyBottomNavThemeColor()
    }
    
    /**
     * 字体缩放变化回调
     * 显式重写以确保底部导航栏字体正确响应变化
     * 注意：先应用字体缩放，再调用super.recreate()进行重建
     */
    override fun onFontScaleChanged(scale: Float) {
        // 先应用底部导航栏字体缩放，然后再重建Activity
        applyBottomNavFontScale(scale)
        // 延迟一点时间再重建，确保字体缩放已应用
        binding.root.postDelayed({
            super.onFontScaleChanged(scale)
        }, 100)
    }
    
    /**
     * 应用底部导航栏字体缩放
     * Material Design的BottomNavigationView需要特殊处理才能正确响应字体缩放
     */
    private fun applyBottomNavFontScale(scale: Float) {
        binding.bottomNav.post {
            try {
                // 强制刷新底部导航栏的文字大小
                val menuView = binding.bottomNav.getChildAt(0) as? ViewGroup
                menuView?.let { menu ->
                    for (i in 0 until menu.childCount) {
                        val item = menu.getChildAt(i)
                        // 查找并更新文字视图
                        findAndUpdateTextViews(item, scale)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 递归查找并更新所有TextView的字体大小
     */
    private fun findAndUpdateTextViews(view: View, scale: Float) {
        when (view) {
            is android.widget.TextView -> {
                // 获取原始字体大小并应用缩放
                val originalSize = view.textSize / view.resources.displayMetrics.scaledDensity
                view.textSize = originalSize * scale
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    findAndUpdateTextViews(view.getChildAt(i), scale)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 确保每次恢复时都应用最新的字体缩放
        val scale = ThemeManager.getFontScale()
        if (scale != 1.0f) {
            applyBottomNavFontScale(scale)
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

    private fun performAutoSync() {
        val syncStrategy = prefs.getString("sync_strategy", "manual")
        if (syncStrategy != "auto") {
            return
        }

        val serverUrl = prefs.getString("webdav_server_url", "") ?: ""
        val username = prefs.getString("webdav_username", "") ?: ""
        val password = prefs.getString("webdav_password", "") ?: ""

        if (serverUrl.isEmpty()) {
            return
        }

        val config = WebDAVUtil.WebDAVConfig(serverUrl, username, password)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ensureResult = WebDAVUtil.ensureDirectoryExists(config)
                if (ensureResult.isFailure) {
                    return@launch
                }

                val exportData = createExportData()
                val jsonContent = exportData.toString(2)
                val fileName = WebDAVUtil.generateAutoPushFileName()
                val remotePath = "daxijizhang/$fileName"

                WebDAVUtil.uploadFile(config, remotePath, jsonContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun createExportData(): JSONObject {
        val exportData = JSONObject()

        val billsWithItems = repository.getAllBillsWithItemsList()
        val billsArray = org.json.JSONArray()

        billsWithItems.forEach { billWithItems ->
            val billJson = createBillJson(billWithItems)
            billsArray.put(billJson)
        }

        exportData.put("bills", billsArray)
        exportData.put("billCount", billsArray.length())
        exportData.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        return exportData
    }

    private suspend fun createBillJson(billWithItems: com.example.daxijizhang.data.model.BillWithItems): JSONObject {
        val bill = billWithItems.bill
        return JSONObject().apply {
            put("communityName", bill.communityName)
            put("phase", bill.phase ?: "")
            put("buildingNumber", bill.buildingNumber ?: "")
            put("roomNumber", bill.roomNumber ?: "")
            put("startDate", formatDate(bill.startDate))
            put("endDate", formatDate(bill.endDate))
            put("totalAmount", bill.totalAmount)
            put("paidAmount", bill.paidAmount)
            put("waivedAmount", bill.waivedAmount)
            put("remark", bill.remark ?: "")
            put("createdAt", formatDate(bill.createdAt))

            val itemsArray = org.json.JSONArray()
            billWithItems.items.forEach { item ->
                val itemJson = JSONObject().apply {
                    put("projectName", item.projectName)
                    put("unitPrice", item.unitPrice)
                    put("quantity", item.quantity)
                    put("totalPrice", item.totalPrice)
                }
                itemsArray.put(itemJson)
            }
            put("items", itemsArray)

            val paymentRecords = repository.getPaymentRecordsByBillIdList(bill.id)
            val recordsArray = org.json.JSONArray()
            paymentRecords.forEach { record ->
                val recordJson = JSONObject().apply {
                    put("paymentDate", formatDate(record.paymentDate))
                    put("amount", record.amount)
                }
                recordsArray.put(recordJson)
            }
            put("paymentRecords", recordsArray)
        }
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
    }
}
