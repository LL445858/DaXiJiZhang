package com.example.daxijizhang.ui.user

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.example.daxijizhang.ui.settings.RemoteSyncActivity
import com.example.daxijizhang.util.BlurUtil
import com.example.daxijizhang.util.ImagePickerUtil
import com.example.daxijizhang.util.ViewUtil

/**
 * 用户界面 - 作为一级导航界面
 * 包含用户头像、昵称展示和设置功能入口
 */
class UserFragment : Fragment() {

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

        // 远程同步
        binding.btnRemoteSync.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                val intent = Intent(requireContext(), RemoteSyncActivity::class.java)
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
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.feedback_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
            .apply {
                // 设置确认按钮颜色为主题色
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            }
    }

    private fun showDonationDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.donation_message)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
            .apply {
                // 设置确认按钮颜色为主题色
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            }
    }

    private fun loadUserData() {
        try {
            // 从SharedPreferences加载用户数据
            val prefs = requireContext().getSharedPreferences("user_settings", android.content.Context.MODE_PRIVATE)
            val nickname = prefs.getString("nickname", getString(R.string.default_nickname))
            binding.tvUserNickname.text = nickname

            // 加载头像
            val avatarBitmap = ImagePickerUtil.loadAvatar(requireContext())
            if (avatarBitmap != null) {
                binding.ivUserAvatar.setImageBitmap(avatarBitmap)
            } else {
                binding.ivUserAvatar.setImageResource(R.drawable.default_avatar)
            }

            // 加载背景
            val backgroundBitmap = ImagePickerUtil.loadBackground(requireContext())
            if (backgroundBitmap != null) {
                binding.ivBackground.setImageBitmap(backgroundBitmap)
                originalBackgroundBitmap = backgroundBitmap
            } else {
                binding.ivBackground.setImageResource(R.drawable.default_user_background)
                // 保存默认背景图片
                val drawable = binding.ivBackground.drawable
                if (drawable != null) {
                    originalBackgroundBitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(originalBackgroundBitmap!!)
                    drawable.draw(canvas)
                } else {
                    originalBackgroundBitmap = null
                }
            }

            // 应用背景效果设置
            applyBackgroundEffects(prefs)
        } catch (e: Exception) {
            e.printStackTrace()
            // 发生异常时，使用默认资源
            binding.ivUserAvatar.setImageResource(R.drawable.default_avatar)
            binding.ivBackground.setImageResource(R.drawable.default_user_background)
            originalBackgroundBitmap = null
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
     * 100 = 完全不透明（完全遮罩）
     */
    private fun applyOverlayEffect(overlayValue: Int) {
        try {
            // 计算遮罩透明度：0值对应0透明度（完全透明），100值对应200透明度（半透明黑色）
            val maxOverlayAlpha = 180 // 最大遮罩透明度（0-255）
            val alpha = (overlayValue * maxOverlayAlpha / 100)
            
            // 获取MaterialCardView内部的FrameLayout
            val innerFrameLayout = binding.userHeaderContainer.getChildAt(0) as? FrameLayout
            
            if (innerFrameLayout != null) {
                // 查找或创建遮罩层
                var overlayView = innerFrameLayout.findViewById<View>(R.id.view_overlay)
                
                if (overlayView == null) {
                    // 创建遮罩层
                    overlayView = View(requireContext()).apply {
                        id = R.id.view_overlay
                    }
                    // 插入到背景图片之后（索引2，因为索引0是ImageView，索引1是渐变遮罩）
                    innerFrameLayout.addView(overlayView, 2)
                }
                
                // 设置遮罩颜色和透明度
                val overlayColor = (alpha shl 24) or 0x000000
                overlayView.setBackgroundColor(overlayColor)
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
        // 回收原始背景图片
        originalBackgroundBitmap?.recycle()
        originalBackgroundBitmap = null
        _binding = null
    }
}
