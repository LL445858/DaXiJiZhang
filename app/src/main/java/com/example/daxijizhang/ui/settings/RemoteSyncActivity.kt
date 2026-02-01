package com.example.daxijizhang.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.databinding.ActivityRemoteSyncBinding
import com.example.daxijizhang.databinding.ItemSyncLogBinding
import com.example.daxijizhang.ui.base.BaseActivity
import com.example.daxijizhang.util.ViewUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RemoteSyncActivity : BaseActivity() {

    private lateinit var binding: ActivityRemoteSyncBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var syncLogAdapter: SyncLogAdapter

    private val syncLogs = mutableListOf<SyncLogEntry>()

    data class SyncLogEntry(
        val timestamp: Long,
        val action: String,
        val status: String,
        val message: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteSyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_settings", MODE_PRIVATE)

        initViews()
        setupClickListeners()
        loadSettings()
        setupSyncLogRecycler()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 设置同步策略单选按钮监听
        binding.rgSyncStrategy.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_manual_sync -> {
                    prefs.edit().putString("sync_strategy", "manual").apply()
                    binding.tvAutoSyncDescription.visibility = View.GONE
                }
                R.id.rb_auto_sync -> {
                    prefs.edit().putString("sync_strategy", "auto").apply()
                    binding.tvAutoSyncDescription.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupClickListeners() {
        // 验证连接
        binding.btnVerifyConnection.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                verifyConnection()
            }
        }

        // 推送到云端
        binding.btnPushToCloud.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                startSync("push")
            }
        }

        // 从云端拉取
        binding.btnPullFromCloud.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                startSync("pull")
            }
        }

        // 查看同步日志
        binding.btnViewSyncLog.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                toggleSyncLogVisibility()
            }
        }

        // 关闭日志
        binding.btnCloseLog.setOnClickListener {
            binding.cardSyncLog.visibility = View.GONE
        }
    }

    private fun loadSettings() {
        // 加载WebDAV设置
        val serverUrl = prefs.getString("webdav_server_url", "")
        val username = prefs.getString("webdav_username", "")
        val password = prefs.getString("webdav_password", "")

        binding.etServerUrl.setText(serverUrl)
        binding.etUsername.setText(username)
        binding.etPassword.setText(password)

        // 加载同步策略
        val syncStrategy = prefs.getString("sync_strategy", "manual")
        if (syncStrategy == "auto") {
            binding.rbAutoSync.isChecked = true
            binding.tvAutoSyncDescription.visibility = View.VISIBLE
        } else {
            binding.rbManualSync.isChecked = true
            binding.tvAutoSyncDescription.visibility = View.GONE
        }
    }

    private fun setupSyncLogRecycler() {
        syncLogAdapter = SyncLogAdapter(syncLogs)
        binding.recyclerSyncLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerSyncLog.adapter = syncLogAdapter

        // 加载历史日志
        loadSyncLogs()
    }

    private fun loadSyncLogs() {
        // 从SharedPreferences加载历史同步日志
        val logsJson = prefs.getString("sync_logs", "")
        if (!logsJson.isNullOrEmpty()) {
            // TODO: 解析并加载历史日志
        }

        // 如果没有日志，添加一些示例日志
        if (syncLogs.isEmpty()) {
            syncLogs.add(SyncLogEntry(
                System.currentTimeMillis(),
                "初始化",
                "成功",
                "同步功能已就绪"
            ))
            syncLogAdapter.notifyDataSetChanged()
        }
    }

    private fun verifyConnection() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (serverUrl.isEmpty()) {
            binding.tilServerUrl.error = getString(R.string.server_url_required)
            return
        }

        // 保存设置
        prefs.edit().apply {
            putString("webdav_server_url", serverUrl)
            putString("webdav_username", username)
            putString("webdav_password", password)
            apply()
        }

        // 显示验证中状态
        binding.layoutConnectionStatus.visibility = View.VISIBLE
        binding.ivConnectionStatus.setImageResource(R.drawable.ic_sync)
        binding.ivConnectionStatus.setColorFilter(getColor(R.color.info))
        binding.tvConnectionStatus.text = getString(R.string.verifying)
        binding.tvConnectionStatus.setTextColor(getColor(R.color.info))

        // 模拟验证过程
        lifecycleScope.launch {
            delay(2000)

            // 模拟验证结果（实际应实现真实的WebDAV连接验证）
            val isSuccess = serverUrl.startsWith("http")

            if (isSuccess) {
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_check)
                binding.ivConnectionStatus.setColorFilter(getColor(R.color.success))
                binding.tvConnectionStatus.text = getString(R.string.connection_success)
                binding.tvConnectionStatus.setTextColor(getColor(R.color.success))
                Toast.makeText(this@RemoteSyncActivity, R.string.connection_success, Toast.LENGTH_SHORT).show()
            } else {
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_close)
                binding.ivConnectionStatus.setColorFilter(getColor(R.color.error))
                binding.tvConnectionStatus.text = getString(R.string.connection_failed)
                binding.tvConnectionStatus.setTextColor(getColor(R.color.error))
                Toast.makeText(this@RemoteSyncActivity, R.string.connection_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSync(direction: String) {
        // 检查是否已配置服务器
        val serverUrl = prefs.getString("webdav_server_url", "")
        if (serverUrl.isNullOrEmpty()) {
            Toast.makeText(this, R.string.please_configure_server_first, Toast.LENGTH_SHORT).show()
            return
        }

        // 显示同步进度
        binding.layoutSyncProgress.visibility = View.VISIBLE
        binding.tvSyncResult.visibility = View.GONE
        binding.btnPushToCloud.isEnabled = false
        binding.btnPullFromCloud.isEnabled = false

        val actionText = if (direction == "push") "推送到云端" else "从云端拉取"
        binding.tvSyncStatus.text = getString(R.string.syncing_with_action, actionText)

        // 模拟同步过程
        lifecycleScope.launch {
            delay(3000)

            binding.layoutSyncProgress.visibility = View.GONE
            binding.btnPushToCloud.isEnabled = true
            binding.btnPullFromCloud.isEnabled = true

            // 模拟同步结果
            val isSuccess = true
            val message = if (direction == "push") {
                "成功推送 15 条记录到云端"
            } else {
                "成功从云端拉取 15 条记录"
            }

            if (isSuccess) {
                binding.tvSyncResult.visibility = View.VISIBLE
                binding.tvSyncResult.text = message
                binding.tvSyncResult.setTextColor(getColor(R.color.success))

                // 添加到日志
                addSyncLog(actionText, "成功", message)

                Toast.makeText(this@RemoteSyncActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
            } else {
                binding.tvSyncResult.visibility = View.VISIBLE
                binding.tvSyncResult.text = getString(R.string.sync_failed)
                binding.tvSyncResult.setTextColor(getColor(R.color.error))

                addSyncLog(actionText, "失败", "同步过程中发生错误")

                Toast.makeText(this@RemoteSyncActivity, R.string.sync_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSyncLog(action: String, status: String, message: String) {
        val entry = SyncLogEntry(System.currentTimeMillis(), action, status, message)
        syncLogs.add(0, entry)
        syncLogAdapter.notifyItemInserted(0)
        binding.recyclerSyncLog.scrollToPosition(0)

        // 保存日志到SharedPreferences
        saveSyncLogs()
    }

    private fun saveSyncLogs() {
        // TODO: 将日志保存到SharedPreferences
        // 限制日志数量，只保留最近50条
        while (syncLogs.size > 50) {
            syncLogs.removeAt(syncLogs.size - 1)
        }
    }

    private fun toggleSyncLogVisibility() {
        if (binding.cardSyncLog.visibility == View.VISIBLE) {
            binding.cardSyncLog.visibility = View.GONE
        } else {
            binding.cardSyncLog.visibility = View.VISIBLE
            syncLogAdapter.notifyDataSetChanged()
        }
    }

    // 同步日志适配器
    inner class SyncLogAdapter(private val logs: List<SyncLogEntry>) :
        RecyclerView.Adapter<SyncLogAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemSyncLogBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSyncLogBinding.inflate(
                layoutInflater, parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

            holder.binding.tvLogTime.text = dateFormat.format(Date(log.timestamp))
            holder.binding.tvLogAction.text = log.action
            holder.binding.tvLogStatus.text = log.status
            holder.binding.tvLogMessage.text = log.message

            // 根据状态设置颜色
            val statusColor = when (log.status) {
                "成功" -> R.color.success
                "失败" -> R.color.error
                else -> R.color.info
            }
            holder.binding.tvLogStatus.setTextColor(getColor(statusColor))
        }

        override fun getItemCount() = logs.size
    }
}
