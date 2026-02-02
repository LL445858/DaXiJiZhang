package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityInfoSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ImagePickerUtil
import com.example.daxijizhang.util.ViewUtil

class InfoSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityInfoSettingsBinding
    private val prefs by lazy { getSharedPreferences("user_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        setupSliders()
        loadCurrentSettings()
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

        // 保存昵称
        binding.btnSaveNickname.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                saveNickname()
            }
        }
    }

    private fun setupSliders() {
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

    private fun loadCurrentSettings() {
        // 加载昵称
        val nickname = prefs.getString("nickname", "")
        binding.etNickname.setText(nickname)

        // 加载头像和背景预览
        loadAvatarPreview()
        loadBackgroundPreview()

        // 加载滑块值
        val blurValue = prefs.getInt("background_blur", 0)
        val overlayValue = prefs.getInt("background_overlay", 0)
        binding.sliderBlur.value = blurValue.toFloat()
        binding.sliderOverlay.value = overlayValue.toFloat()
    }

    private fun loadAvatarPreview() {
        // 加载自定义头像
        val avatarBitmap = ImagePickerUtil.loadAvatar(this)
        if (avatarBitmap != null) {
            binding.ivAvatarPreview.setImageBitmap(avatarBitmap)
        } else {
            binding.ivAvatarPreview.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun loadBackgroundPreview() {
        // 加载自定义背景
        val backgroundBitmap = ImagePickerUtil.loadBackground(this)
        if (backgroundBitmap != null) {
            binding.ivBackgroundPreview.setImageBitmap(backgroundBitmap)
        } else {
            binding.ivBackgroundPreview.setImageResource(R.drawable.default_user_background)
        }
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
                    // 仅更新头像预览
                    loadAvatarPreview()
                    
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
                    // 仅更新背景预览
                    loadBackgroundPreview()
                    
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

    private fun saveNickname() {
        val nickname = binding.etNickname.text.toString().trim()
        if (nickname.isEmpty()) {
            binding.tilNickname.error = getString(R.string.nickname_required)
            return
        }

        prefs.edit().putString("nickname", nickname).apply()

        Toast.makeText(this, R.string.nickname_saved, Toast.LENGTH_SHORT).show()
        onBackPressedDispatcher.onBackPressed()
    }
}