package com.example.daxijizhang

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.daxijizhang.databinding.ActivityMainBinding
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.ui.adapter.MainFragmentAdapter
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.WebDAVUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: BillRepository
    private lateinit var pagerAdapter: MainFragmentAdapter

    private var startX = 0f
    private var edgeExclusionWidth = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupStatusBar()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val displayMetrics = resources.displayMetrics
        edgeExclusionWidth = (displayMetrics.widthPixels * 0.05f).toInt()

        prefs = getSharedPreferences("user_settings", MODE_PRIVATE)

        initRepository()
        setupViewPager()
        setupBottomNavigation()
        setupBackPressHandler()
        playEntranceAnimation()

        (application as? DaxiApplication)?.registerMainActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
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
    
    private fun setupStatusBar() {
        window.apply {
            val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            statusBarColor = if (isDarkMode) {
                ContextCompat.getColor(this@MainActivity, R.color.status_bar)
            } else {
                Color.WHITE
            }

            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode
            }

            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
    }

    private fun updateStatusBarForPosition(position: Int) {
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.apply {
            statusBarColor = when {
                position == MainFragmentAdapter.PAGE_USER -> {
                    if (isDarkMode) Color.BLACK else ContextCompat.getColor(this@MainActivity, R.color.background)
                }
                isDarkMode -> ContextCompat.getColor(this@MainActivity, R.color.status_bar)
                else -> Color.WHITE
            }
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MainFragmentAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        
        binding.viewPager.offscreenPageLimit = 2
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateStatusBarForPosition(position)
                
                val menuId = pagerAdapter.getMenuIdForPosition(position)
                if (binding.bottomNav.selectedItemId != menuId) {
                    binding.bottomNav.menu.findItem(menuId)?.isChecked = true
                }
            }
        })
    }

    private fun setupBottomNavigation() {
        applyBottomNavThemeColor()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val position = pagerAdapter.getPagePosition(item.itemId)
            if (binding.viewPager.currentItem != position) {
                binding.viewPager.setCurrentItem(position, false)
            }
            true
        }
    }

    private fun applyBottomNavThemeColor() {
        val themeColor = ThemeManager.getThemeColor()
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

    fun reapplyThemeColor() {
        applyBottomNavThemeColor()
    }
    
    override fun onFontScaleChanged(scale: Float) {
        super.onFontScaleChanged(scale)
    }
    
    override fun onResume() {
        super.onResume()
        binding.bottomNav.post {
            applyBottomNavFontScale(ThemeManager.getFontScale())
        }
    }
    
    private fun applyBottomNavFontScale(scale: Float) {
        try {
            val menuView = binding.bottomNav.getChildAt(0) as? ViewGroup
            menuView?.let { menu ->
                for (i in 0 until menu.childCount) {
                    val item = menu.getChildAt(i)
                    findAndUpdateTextViews(item, scale)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun findAndUpdateTextViews(view: View, scale: Float) {
        when (view) {
            is android.widget.TextView -> {
                val baseSize = 13.8f
                view.textSize = baseSize * scale
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    findAndUpdateTextViews(view.getChildAt(i), scale)
                }
            }
        }
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }
    
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
