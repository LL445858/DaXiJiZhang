package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityInfoSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ImagePickerUtil
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil

class InfoSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityInfoSettingsBinding
    private val prefs by lazy { getSharedPreferences("user_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStatusBarPadding()
        initViews()
        setupClickListeners()
        setupSliders()
        setupNicknameAutoSave()
        loadCurrentSettings()
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            binding.statusBarPlaceholder.updateLayoutParams {
                height = statusBarInsets.top
            }
            
            binding.contentContainer.setPadding(
                binding.contentContainer.paddingLeft,
                binding.contentContainer.paddingTop,
                binding.contentContainer.paddingRight,
                navigationBarInsets.bottom + binding.contentContainer.paddingTop
            )
            
            windowInsets
        }
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupClickListeners() {
        // 头像设置 - 仅更新头像
        binding.itemAvatarSetting.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                ImagePickerUtil.pickAvatarFromGallery(this)
            }
        }

        // 背景图片设置 - 仅更新背景
        binding.itemBackgroundSetting.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                ImagePickerUtil.pickBackgroundFromGallery(this)
            }
        }
    }

    private fun setupSliders() {
        val themeColor = ThemeManager.getThemeColor()

        // 设置滑动条主题颜色
        binding.sliderBlur.trackActiveTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.sliderBlur.thumbTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.sliderBlur.tickTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.sliderOverlay.trackActiveTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.sliderOverlay.thumbTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.sliderOverlay.tickTintList = android.content.res.ColorStateList.valueOf(themeColor)

        // 高斯模糊滑块
        binding.sliderBlur.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putInt("background_blur", value.toInt()).apply()
            }
        }

        // 遮罩浓度滑块
        binding.sliderOverlay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putInt("background_overlay", value.toInt()).apply()
            }
        }
    }

    private fun setupNicknameAutoSave() {
        binding.etNickname.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.trim()?.let { nickname ->
                    if (nickname.isNotEmpty()) {
                        prefs.edit().putString("nickname", nickname).apply()
                    }
                }
            }
        })
    }

    private fun loadCurrentSettings() {
        // 加载昵称
        val nickname = prefs.getString("nickname", "")
        binding.etNickname.setText(nickname)

        // 加载滑块值
        val blurValue = prefs.getInt("background_blur", 0)
        val overlayValue = prefs.getInt("background_overlay", 0)
        binding.sliderBlur.value = blurValue.toFloat()
        binding.sliderOverlay.value = overlayValue.toFloat()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        ImagePickerUtil.handleActivityResult(
            this,
            requestCode,
            resultCode,
            data,
            object : ImagePickerUtil.ImagePickerCallback {
                override fun onAvatarPicked(avatarPath: String) {
                    // 保存设置标记
                    prefs.edit()
                        .putBoolean("has_custom_avatar", true)
                        .apply()
                    
                    Toast.makeText(
                        this@InfoSettingsActivity,
                        "头像已更新",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onBackgroundPicked(backgroundPath: String) {
                    // 保存设置标记
                    prefs.edit()
                        .putBoolean("has_custom_background", true)
                        .apply()
                    
                    Toast.makeText(
                        this@InfoSettingsActivity,
                        "背景图片已更新",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(message: String) {
                    Toast.makeText(
                        this@InfoSettingsActivity,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}
