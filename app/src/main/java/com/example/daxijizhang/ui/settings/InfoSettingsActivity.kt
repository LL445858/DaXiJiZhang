package com.example.daxijizhang.ui.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityInfoSettingsBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ViewUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class InfoSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityInfoSettingsBinding

    // 使用SAF选择图片，无需权限申请
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            handleImageSelection(it, isAvatar = pendingAction == PendingAction.AVATAR)
        }
    }

    private var pendingAction: PendingAction? = null

    private enum class PendingAction {
        AVATAR, BACKGROUND
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        loadCurrentSettings()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupClickListeners() {
        // 头像设置 - 使用SAF，无需权限申请
        binding.itemAvatarSetting.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                pendingAction = PendingAction.AVATAR
                openImagePicker()
            }
        }

        // 背景图片设置 - 使用SAF，无需权限申请
        binding.itemBackgroundSetting.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                pendingAction = PendingAction.BACKGROUND
                openImagePicker()
            }
        }

        // 保存昵称
        binding.btnSaveNickname.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                saveNickname()
            }
        }
    }

    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences("user_settings", MODE_PRIVATE)

        // 加载昵称
        val nickname = prefs.getString("nickname", "")
        binding.etNickname.setText(nickname)

        // 加载头像和背景预览
        loadAvatarPreview()
        loadBackgroundPreview()
    }

    private fun loadAvatarPreview() {
        val avatarFile = File(filesDir, "user_avatar.jpg")
        if (avatarFile.exists()) {
            binding.ivAvatarPreview.setImageURI(Uri.fromFile(avatarFile))
        } else {
            binding.ivAvatarPreview.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun loadBackgroundPreview() {
        val backgroundFile = File(filesDir, "user_background.jpg")
        if (backgroundFile.exists()) {
            binding.ivBackgroundPreview.setImageURI(Uri.fromFile(backgroundFile))
        } else {
            binding.ivBackgroundPreview.setImageResource(R.drawable.default_user_background)
        }
    }

    /**
     * 使用SAF打开图片选择器 - 无需权限申请
     */
    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun handleImageSelection(uri: Uri, isAvatar: Boolean) {
        try {
            if (isAvatar) {
                // 裁剪并保存头像
                cropAndSaveAvatar(uri)
            } else {
                // 保存背景图片
                saveBackgroundImage(uri)
            }
        } catch (e: IOException) {
            Toast.makeText(this, R.string.image_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun cropAndSaveAvatar(uri: Uri) {
        // 简化处理：直接保存图片
        // 实际应用中可以使用uCrop等库进行裁剪
        contentResolver.openInputStream(uri)?.use { input ->
            val avatarFile = File(filesDir, "user_avatar.jpg")
            FileOutputStream(avatarFile).use { output ->
                input.copyTo(output)
            }
            binding.ivAvatarPreview.setImageURI(Uri.fromFile(avatarFile))
            Toast.makeText(this, R.string.avatar_updated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBackgroundImage(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { input ->
            val backgroundFile = File(filesDir, "user_background.jpg")
            FileOutputStream(backgroundFile).use { output ->
                input.copyTo(output)
            }
            binding.ivBackgroundPreview.setImageURI(Uri.fromFile(backgroundFile))
            Toast.makeText(this, R.string.background_updated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveNickname() {
        val nickname = binding.etNickname.text.toString().trim()
        if (nickname.isEmpty()) {
            binding.tilNickname.error = getString(R.string.nickname_required)
            return
        }

        val prefs = getSharedPreferences("user_settings", MODE_PRIVATE)
        prefs.edit().putString("nickname", nickname).apply()

        Toast.makeText(this, R.string.nickname_saved, Toast.LENGTH_SHORT).show()
        onBackPressedDispatcher.onBackPressed()
    }
}
