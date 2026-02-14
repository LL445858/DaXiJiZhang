package com.example.daxijizhang.ui.user

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.example.daxijizhang.BuildConfig
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.FragmentUserBinding
import com.example.daxijizhang.ui.settings.DataMigrationActivity
import com.example.daxijizhang.ui.settings.DisplaySettingsActivity
import com.example.daxijizhang.ui.settings.InfoSettingsActivity
import com.example.daxijizhang.ui.settings.MoreSettingsActivity
import com.example.daxijizhang.util.BlurUtil
import com.example.daxijizhang.util.ImagePickerUtil
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil

/**
 * 用户界面 - 作为一级导航界面
 * 包含用户头像、昵称展示和设置功能入口
 */
class UserFragment : Fragment(), ThemeManager.OnThemeColorChangeListener {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!
    private var originalBackgroundBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStatusBarPadding()
        initViews()
        setupClickListeners()
        loadUserData()
        applyThemeColor()

        // 注册主题颜色变化监听器
        ThemeManager.addThemeColorChangeListener(this)
    }

    /**
     * 主题颜色变化回调
     */
    override fun onThemeColorChanged(color: Int) {
        // 实时更新图标颜色
        applyThemeColor()
    }

    /**
     * 应用主题颜色到图标
     */
    private fun applyThemeColor() {
        val themeColor = ThemeManager.getThemeColor()
        // 设置四个设置图标颜色
        binding.ivInfoSettings.setColorFilter(themeColor)
        binding.ivDisplaySettings.setColorFilter(themeColor)
        binding.ivDataMigration.setColorFilter(themeColor)
        binding.ivRemoteSync.setColorFilter(themeColor)
    }

    private fun setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.statusBarPlaceholder.updateLayoutParams {
                height = insets.top
            }
            windowInsets
        }
    }

    private fun initViews() {
        // 设置版本号
        binding.tvAppVersion.text = BuildConfig.VERSION_NAME
    }

    private fun setupClickListeners() {
        // 信息设置
        binding.btnInfoSettings.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(requireContext(), InfoSettingsActivity::class.java)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    requireContext(),
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                startActivity(intent, options.toBundle())
            }
        }

        // 显示设置
        binding.btnDisplaySettings.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(requireContext(), DisplaySettingsActivity::class.java)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    requireContext(),
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                startActivity(intent, options.toBundle())
            }
        }

        // 数据迁移
        binding.btnDataMigration.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(requireContext(), DataMigrationActivity::class.java)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    requireContext(),
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                startActivity(intent, options.toBundle())
            }
        }

        // 更多设置
        binding.btnRemoteSync.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(requireContext(), MoreSettingsActivity::class.java)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    requireContext(),
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                startActivity(intent, options.toBundle())
            }
        }

        // 反馈和建议 - 使用防抖点击
        ViewUtil.setOnSingleClickListener(binding.itemFeedback, debounceTime = 500) {
            showFeedbackDialog()
        }

        // 致谢和赞赏 - 使用防抖点击
        ViewUtil.setOnSingleClickListener(binding.itemDonation, debounceTime = 500) {
            showDonationDialog()
        }
    }

    private fun showFeedbackDialog() {
        val themeColor = ThemeManager.getThemeColor()
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage(R.string.feedback_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.show()
        // 设置确认按钮颜色为主题色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(themeColor)
    }

    private fun showDonationDialog() {
        val themeColor = ThemeManager.getThemeColor()
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage(R.string.donation_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.show()
        // 设置确认按钮颜色为主题色
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(themeColor)
    }

    private fun loadUserData() {
        val prefs = requireContext().getSharedPreferences("user_settings", android.content.Context.MODE_PRIVATE)
        val nickname = prefs.getString("nickname", getString(R.string.default_nickname))
        binding.tvUserNickname.text = nickname

        loadAvatar()
        loadBackground(prefs)
    }
    
    private fun loadAvatar() {
        try {
            val avatarBitmap = ImagePickerUtil.loadAvatar(requireContext())
            if (avatarBitmap != null) {
                binding.ivUserAvatar.setImageBitmap(avatarBitmap)
            } else {
                binding.ivUserAvatar.setImageResource(R.drawable.default_avatar)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.ivUserAvatar.setImageResource(R.drawable.default_avatar)
        }
    }
    
    private fun loadBackground(prefs: android.content.SharedPreferences) {
        try {
            val backgroundBitmap = ImagePickerUtil.loadBackground(requireContext())
            if (backgroundBitmap != null) {
                binding.ivBackground.setImageBitmap(backgroundBitmap)
                originalBackgroundBitmap = backgroundBitmap
            } else {
                binding.ivBackground.setImageResource(R.drawable.default_user_background)
                originalBackgroundBitmap = loadDefaultBackgroundBitmap()
            }
            applyBackgroundEffects(prefs)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.ivBackground.setImageResource(R.drawable.default_user_background)
            originalBackgroundBitmap = null
        }
    }
    
    private fun loadDefaultBackgroundBitmap(): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.default_user_background)
            if (drawable != null) {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyBackgroundEffects(prefs: android.content.SharedPreferences) {
        // 获取设置值
        val blurValue = prefs.getInt("background_blur", 0)
        val overlayValue = prefs.getInt("background_overlay", 0)

        // 应用高斯模糊效果 - 使用RenderScript实现真正的模糊
        applyBlurEffect(blurValue)

        // 应用遮罩浓度 - 0表示完全透明（无遮罩），100表示完全不透明
        applyOverlayEffect(overlayValue)
    }

    /**
     * 应用高斯模糊效果
     * 使用RenderScript实现真正的高斯模糊
     */
    private fun applyBlurEffect(blurValue: Int) {
        try {
            if (blurValue > 0) {
                // 使用保存的原始背景图片
                val original = originalBackgroundBitmap
                if (original != null) {
                    // 转换模糊值为RenderScript半径
                    val radius = BlurUtil.convertBlurValueToRadius(blurValue)
                    
                    // 使用RenderScript应用高斯模糊
                    val blurredBitmap = BlurUtil.blurBitmap(requireContext(), original, radius)
                    
                    // 设置模糊后的图片
                    binding.ivBackground.setImageBitmap(blurredBitmap)
                }
            } else {
                // 无模糊效果 - 恢复原状
                originalBackgroundBitmap?.let {
                    binding.ivBackground.setImageBitmap(it)
                }
                binding.ivBackground.scaleX = 1f
                binding.ivBackground.scaleY = 1f
                binding.ivBackground.alpha = 1f
            }
        } catch (e: Exception) {
            // 发生异常时，恢复默认背景
            e.printStackTrace()
            binding.ivBackground.setImageResource(R.drawable.default_user_background)
            binding.ivBackground.scaleX = 1f
            binding.ivBackground.scaleY = 1f
            binding.ivBackground.alpha = 1f
        }
    }

    /**
     * 应用遮罩效果
     * 0 = 完全透明（无遮罩，完全显示原图）
     * 100 = 完全不透明（完全遮罩，显示灰色）
     */
    private fun applyOverlayEffect(overlayValue: Int) {
        try {
            val overlayView = binding.viewOverlayMask
            
            if (overlayValue <= 0) {
                // 浓度为0，隐藏遮罩层，完全显示原图
                overlayView.visibility = View.GONE
            } else {
                // 显示遮罩层
                overlayView.visibility = View.VISIBLE
                
                // 计算透明度：overlayValue范围0-100，映射到alpha范围0-255
                // 0 = 完全透明，100 = 完全不透明
                val alpha = (overlayValue * 255 / 100)
                
                // 设置深灰色遮罩，颜色为深灰色#303030，更接近黑色但不是纯黑色
                // 透明度由alpha控制
                val darkGrayColor = (alpha shl 24) or 0x00303030
                overlayView.setBackgroundColor(darkGrayColor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // 重新加载用户数据，以反映可能的更改
        loadUserData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 注销主题颜色变化监听器
        ThemeManager.removeThemeColorChangeListener(this)
        // 回收原始背景图片
        originalBackgroundBitmap?.recycle()
        originalBackgroundBitmap = null
        _binding = null
    }
}
