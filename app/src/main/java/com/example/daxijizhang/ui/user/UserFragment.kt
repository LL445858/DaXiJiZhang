package com.example.daxijizhang.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import com.example.daxijizhang.BuildConfig
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.FragmentUserBinding
import com.example.daxijizhang.ui.settings.DataMigrationActivity
import com.example.daxijizhang.ui.settings.DisplaySettingsActivity
import com.example.daxijizhang.ui.settings.InfoSettingsActivity
import com.example.daxijizhang.ui.settings.RemoteSyncActivity
import com.example.daxijizhang.util.ViewUtil

/**
 * 用户界面 - 作为一级导航界面
 * 包含用户头像、昵称展示和设置功能入口
 */
class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

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
        
        initViews()
        setupClickListeners()
        loadUserData()
    }

    private fun initViews() {
        // 设置版本号
        binding.tvAppVersion.text = BuildConfig.VERSION_NAME
    }

    private fun setupClickListeners() {
        // 头像点击 - 查看大图
        binding.ivUserAvatar.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                showAvatarFullscreen()
            }
        }

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
    }

    private fun loadUserData() {
        // 从SharedPreferences加载用户数据
        val prefs = requireContext().getSharedPreferences("user_settings", android.content.Context.MODE_PRIVATE)
        val nickname = prefs.getString("nickname", getString(R.string.default_nickname))
        binding.tvUserNickname.text = nickname

        // 加载头像和背景图片（如果有保存的自定义图片）
        // TODO: 实现从本地存储加载自定义头像和背景
    }

    private fun showAvatarFullscreen() {
        // TODO: 实现查看大图功能
        Toast.makeText(requireContext(), "查看大图功能", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // 重新加载用户数据，以反映可能的更改
        loadUserData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
