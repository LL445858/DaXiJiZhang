package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.databinding.ActivityDataMigrationBinding
import com.example.daxijizhang.databinding.DialogJianguoyunAccountBinding
import com.example.daxijizhang.databinding.ItemSyncLogBinding
import com.example.daxijizhang.util.ThemeManager
import com.example.daxijizhang.util.ViewUtil
import com.example.daxijizhang.util.WebDAVUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class DataMigrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataMigrationBinding
    private lateinit var repository: BillRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var syncLogAdapter: SyncLogAdapter

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                showImportPreview(uri)
            }
        }
    }

    private var pendingImportUri: Uri? = null
    private var pendingImportBills: List<ImportBillData>? = null
    private var pendingActualImportCount: Int = 0
    private val syncLogs = mutableListOf<SyncLogEntry>()

    data class SyncLogEntry(
        val timestamp: Long,
        val action: String,
        val status: String,
        val message: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataMigrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_settings", MODE_PRIVATE)

        initRepository()
        initViews()
        setupSyncLogRecycler()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
        loadSettings()
        updateAccountStatus()
        applyThemeColor()
    }

    /**
     * 应用主题颜色到开关
     */
    private fun applyThemeColor() {
        // 设置开关颜色
        binding.switchAutoLocalBackup.thumbTintList = ThemeManager.createSwitchThumbColorStateList()
        binding.switchAutoLocalBackup.trackTintList = ThemeManager.createSwitchTrackColorStateList()
        binding.switchAutoBackup.thumbTintList = ThemeManager.createSwitchThumbColorStateList()
        binding.switchAutoBackup.trackTintList = ThemeManager.createSwitchTrackColorStateList()
    }

    /**
     * 应用主题颜色到TextInputLayout
     */
    private fun applyInputLayoutThemeColor(textInputLayout: com.google.android.material.textfield.TextInputLayout, color: Int) {
        // 设置边框颜色
        textInputLayout.boxStrokeColor = color
        // 设置标题颜色
        textInputLayout.hintTextColor = android.content.res.ColorStateList.valueOf(color)
        // 设置光标颜色
        textInputLayout.editText?.textCursorDrawable = null
        textInputLayout.editText?.highlightColor = color
    }

    private fun setupBackPressHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    private fun setupForwardTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        finish()
    }

    private fun initRepository() {
        val database = AppDatabase.getDatabase(applicationContext)
        repository = BillRepository(
            database.billDao(),
            database.billItemDao(),
            database.paymentRecordDao()
        )
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 导出数据
        binding.itemExportData.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                startExport()
            }
        }

        // 导入数据
        binding.itemImportData.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                openFilePicker()
            }
        }

        // 取消导入
        binding.btnCancelImport.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                hideImportPreview()
            }
        }

        // 确认导入
        binding.btnConfirmImport.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                pendingImportUri?.let { uri ->
                    executeImport(uri)
                }
            }
        }

        // 自动本地备份开关
        binding.switchAutoLocalBackup.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_local_backup", isChecked).apply()
            val status = if (isChecked) "开启" else "关闭"
            addSyncLog("自动本地备份", "设置", "自动本地备份已$status")
        }

        // 坚果云账户设置
        binding.itemJianguoyunAccount.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                showJianguoyunAccountDialog()
            }
        }

        // 推送到云端
        binding.itemPushToCloud.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                startSync("push")
            }
        }

        // 从云端拉取
        binding.itemPullFromCloud.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                startSync("pull")
            }
        }

        // 自动云备份开关
        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_cloud_backup", isChecked).apply()
            val status = if (isChecked) "开启" else "关闭"
            addSyncLog("自动云备份", "设置", "自动云备份已$status")
        }

        // 查看同步日志
        binding.itemViewSyncLog.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                showSyncLogDialog()
            }
        }

        // 关闭日志弹窗
        binding.btnCloseLog.setOnClickListener {
            hideSyncLogDialog()
        }

        // 点击遮罩层关闭弹窗
        binding.layoutSyncLogOverlay.setOnClickListener {
            hideSyncLogDialog()
        }

        // 阻止弹窗内容区域点击事件穿透
        binding.cardSyncLog.setOnClickListener {
            // 不执行任何操作，仅阻止事件穿透
        }
    }

    private fun showJianguoyunAccountDialog() {
        val themeColor = ThemeManager.getThemeColor()
        val dialogBinding = DialogJianguoyunAccountBinding.inflate(LayoutInflater.from(this))

        val savedUsername = prefs.getString("webdav_username", "") ?: ""
        val savedPassword = prefs.getString("webdav_password", "") ?: ""
        dialogBinding.etUsername.setText(savedUsername)
        dialogBinding.etPassword.setText(savedPassword)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        // 应用主题颜色到输入框
        applyInputLayoutThemeColor(dialogBinding.tilUsername, themeColor)
        applyInputLayoutThemeColor(dialogBinding.tilPassword, themeColor)

        // 应用主题颜色到按钮
        dialogBinding.btnVerify.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        dialogBinding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)

        dialogBinding.btnVerify.setOnClickListener {
            val username = dialogBinding.etUsername.text.toString().trim()
            val password = dialogBinding.etPassword.text.toString().trim()

            if (username.isEmpty()) {
                dialogBinding.tilUsername.error = getString(R.string.username)
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                dialogBinding.tilPassword.error = getString(R.string.password_or_key)
                return@setOnClickListener
            }

            verifyConnectionInDialog(dialogBinding, username, password)
        }

        dialogBinding.btnSave.setOnClickListener {
            val username = dialogBinding.etUsername.text.toString().trim()
            val password = dialogBinding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                dialogBinding.tvVerifyResult.visibility = View.VISIBLE
                dialogBinding.tvVerifyResult.text = "请输入用户名和密码"
                dialogBinding.tvVerifyResult.setTextColor(getColor(R.color.error))
                return@setOnClickListener
            }

            prefs.edit().apply {
                putString("webdav_username", username)
                putString("webdav_password", password)
                apply()
            }
            updateAccountStatus()
            addSyncLog("账户设置", "成功", "坚果云账户配置已保存")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun verifyConnectionInDialog(
        dialogBinding: DialogJianguoyunAccountBinding,
        username: String,
        password: String
    ) {
        val serverUrl = "https://dav.jianguoyun.com/dav/"
        val config = WebDAVUtil.WebDAVConfig(serverUrl, username, password)

        dialogBinding.tvVerifyResult.visibility = View.VISIBLE
        dialogBinding.tvVerifyResult.text = getString(R.string.verifying)
        dialogBinding.tvVerifyResult.setTextColor(getColor(R.color.info))
        dialogBinding.btnVerify.isEnabled = false

        lifecycleScope.launch {
            val result = WebDAVUtil.verifyConnection(config)

            dialogBinding.btnVerify.isEnabled = true

            result.onSuccess {
                dialogBinding.tvVerifyResult.text = getString(R.string.connection_success)
                dialogBinding.tvVerifyResult.setTextColor(getColor(R.color.success))
                addSyncLog("验证连接", "成功", "坚果云连接验证成功")
            }.onFailure { e ->
                dialogBinding.tvVerifyResult.text = getString(R.string.connection_failed)
                dialogBinding.tvVerifyResult.setTextColor(getColor(R.color.error))
                addSyncLog("验证连接", "失败", "坚果云连接验证失败: ${e.message}")
            }
        }
    }

    private fun updateAccountStatus() {
        val username = prefs.getString("webdav_username", "") ?: ""
        val password = prefs.getString("webdav_password", "") ?: ""

        if (username.isNotEmpty() && password.isNotEmpty()) {
            binding.tvAccountStatus.text = getString(R.string.account_configured)
            binding.tvAccountStatus.setTextColor(getColor(R.color.success))
        } else {
            binding.tvAccountStatus.text = getString(R.string.account_not_configured)
            binding.tvAccountStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun loadSettings() {
        val autoLocalBackup = prefs.getBoolean("auto_local_backup", false)
        binding.switchAutoLocalBackup.isChecked = autoLocalBackup

        val autoBackup = prefs.getBoolean("auto_cloud_backup", false)
        binding.switchAutoBackup.isChecked = autoBackup
    }

    private fun setupSyncLogRecycler() {
        syncLogAdapter = SyncLogAdapter(syncLogs)
        binding.recyclerSyncLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerSyncLog.adapter = syncLogAdapter

        loadSyncLogs()
    }

    private fun loadSyncLogs() {
        val logsJson = prefs.getString("sync_logs", "")
        if (!logsJson.isNullOrEmpty()) {
        }

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

    private fun showSyncLogDialog() {
        binding.layoutSyncLogOverlay.visibility = View.VISIBLE
        binding.cardSyncLog.visibility = View.VISIBLE
        syncLogAdapter.notifyDataSetChanged()
    }

    private fun hideSyncLogDialog() {
        binding.layoutSyncLogOverlay.visibility = View.GONE
        binding.cardSyncLog.visibility = View.GONE
    }

    private fun startSync(direction: String) {
        val serverUrl = "https://dav.jianguoyun.com/dav/"
        val username = prefs.getString("webdav_username", "") ?: ""
        val password = prefs.getString("webdav_password", "") ?: ""

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.please_configure_server_first, Toast.LENGTH_SHORT).show()
            return
        }

        val config = WebDAVUtil.WebDAVConfig(serverUrl, username, password)

        binding.layoutSyncProgress.visibility = View.VISIBLE
        binding.tvSyncResult.visibility = View.GONE
        binding.itemPushToCloud.isEnabled = false
        binding.itemPullFromCloud.isEnabled = false

        val actionText = if (direction == "push") "推送到云端" else "从云端拉取"
        binding.tvSyncStatus.text = getString(R.string.syncing_with_action, actionText)

        lifecycleScope.launch {
            val result = if (direction == "push") {
                pushToCloud(config)
            } else {
                pullFromCloud(config)
            }

            binding.layoutSyncProgress.visibility = View.GONE
            binding.itemPushToCloud.isEnabled = true
            binding.itemPullFromCloud.isEnabled = true

            result.onSuccess { message ->
                binding.tvSyncResult.visibility = View.VISIBLE
                binding.tvSyncResult.text = message
                binding.tvSyncResult.setTextColor(getColor(R.color.success))
                addSyncLog(actionText, "成功", message)
                Toast.makeText(this@DataMigrationActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                binding.tvSyncResult.visibility = View.VISIBLE
                binding.tvSyncResult.text = getString(R.string.sync_failed)
                binding.tvSyncResult.setTextColor(getColor(R.color.error))
                addSyncLog(actionText, "失败", "同步失败: ${e.message}")
                Toast.makeText(this@DataMigrationActivity, R.string.sync_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun pushToCloud(config: WebDAVUtil.WebDAVConfig): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在检查云端目录..."
                }

                val ensureResult = WebDAVUtil.ensureDirectoryExists(config)
                if (ensureResult.isFailure) {
                    throw ensureResult.exceptionOrNull() ?: Exception("创建目录失败")
                }

                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在准备数据..."
                }

                val exportData = createExportData()
                val jsonContent = exportData.toString(2)
                val fileName = WebDAVUtil.generateManualPushFileName()
                val remotePath = "daxijizhang/$fileName"

                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在上传文件..."
                }

                val uploadResult = WebDAVUtil.uploadFile(config, remotePath, jsonContent) { status ->
                    runOnUiThread {
                        binding.tvSyncStatus.text = status.message
                    }
                }

                if (uploadResult.isFailure) {
                    throw uploadResult.exceptionOrNull() ?: Exception("上传失败")
                }

                val billCount = exportData.optJSONArray("bills")?.length() ?: 0
                Result.success("成功推送 $billCount 条记录到云端")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun pullFromCloud(config: WebDAVUtil.WebDAVConfig): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在获取云端文件列表..."
                }

                val listResult = WebDAVUtil.listFiles(config) { status ->
                    runOnUiThread {
                        binding.tvSyncStatus.text = status.message
                    }
                }

                if (listResult.isFailure) {
                    throw listResult.exceptionOrNull() ?: Exception("获取文件列表失败")
                }

                val files = listResult.getOrNull() ?: emptyList()
                if (files.isEmpty()) {
                    return@withContext Result.success("云端没有备份文件")
                }

                withContext(Dispatchers.Main) {
                    showCloudFileListDialog(config, files)
                }

                Result.success("请选择要导入的文件")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun showCloudFileListDialog(config: WebDAVUtil.WebDAVConfig, files: List<WebDAVUtil.RemoteFile>) {
        // 按修改时间降序排列
        val sortedFiles = files.sortedByDescending { it.modifiedTime }

        val dialogBinding = com.example.daxijizhang.databinding.DialogCloudFilesBinding.inflate(LayoutInflater.from(this))

        val adapter = CloudFileRecyclerAdapter(
            sortedFiles,
            onFileClick = { file ->
                showCloudFileImportDialog(config, file)
            },
            onDeleteClick = { file ->
                showDeleteCloudFileConfirmDialog(config, file, dialogBinding)
            }
        )

        dialogBinding.recyclerCloudFiles.layoutManager = LinearLayoutManager(this)
        dialogBinding.recyclerCloudFiles.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setNegativeButton("取消", null)
            .setView(dialogBinding.root)
            .create()

        // 设置透明背景以显示 MaterialCardView 的圆角
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()
    }

    private fun showDeleteCloudFileConfirmDialog(
        config: WebDAVUtil.WebDAVConfig,
        file: WebDAVUtil.RemoteFile,
        dialogBinding: com.example.daxijizhang.databinding.DialogCloudFilesBinding
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("删除云端备份")
            .setMessage("确定要删除文件：${file.name}？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                deleteCloudFile(config, file, dialogBinding)
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        dialog.show()
    }

    private fun deleteCloudFile(
        config: WebDAVUtil.WebDAVConfig,
        file: WebDAVUtil.RemoteFile,
        dialogBinding: com.example.daxijizhang.databinding.DialogCloudFilesBinding
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                WebDAVUtil.deleteFile(config, file.path)
            }

            result.onSuccess {
                Toast.makeText(this@DataMigrationActivity, "删除成功", Toast.LENGTH_SHORT).show()
                addSyncLog("删除云端文件", "成功", "删除文件: ${file.name}")
                // 刷新文件列表
                refreshCloudFileList(config, dialogBinding)
            }.onFailure { e ->
                Toast.makeText(this@DataMigrationActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                addSyncLog("删除云端文件", "失败", "删除失败: ${e.message}")
            }
        }
    }

    private fun refreshCloudFileList(
        config: WebDAVUtil.WebDAVConfig,
        dialogBinding: com.example.daxijizhang.databinding.DialogCloudFilesBinding
    ) {
        lifecycleScope.launch {
            val listResult = withContext(Dispatchers.IO) {
                WebDAVUtil.listFiles(config) { }
            }

            listResult.onSuccess { files ->
                val sortedFiles = files.sortedByDescending { it.modifiedTime }
                val adapter = CloudFileRecyclerAdapter(
                    sortedFiles,
                    onFileClick = { file ->
                        showCloudFileImportDialog(config, file)
                    },
                    onDeleteClick = { file ->
                        showDeleteCloudFileConfirmDialog(config, file, dialogBinding)
                    }
                )
                dialogBinding.recyclerCloudFiles.adapter = adapter
            }
        }
    }

    private fun showCloudFileImportDialog(config: WebDAVUtil.WebDAVConfig, file: WebDAVUtil.RemoteFile) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("导入备份文件")
            .setMessage("确定要导入文件：${file.name}？")
            .setPositiveButton("导入") { _, _ ->
                importCloudFile(config, file)
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置圆角背景
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

        dialog.show()
    }

    private fun importCloudFile(config: WebDAVUtil.WebDAVConfig, file: WebDAVUtil.RemoteFile) {
        lifecycleScope.launch {
            binding.layoutSyncProgress.visibility = View.VISIBLE
            binding.tvSyncStatus.text = "正在导入文件..."

            val result = withContext(Dispatchers.IO) {
                try {
                    val downloadResult = WebDAVUtil.downloadFile(config, file.path)
                    if (downloadResult.isFailure) {
                        throw downloadResult.exceptionOrNull() ?: Exception("下载文件失败")
                    }

                    val content = downloadResult.getOrNull() ?: ""
                    val json = org.json.JSONObject(content)
                    val billsArray = json.optJSONArray("bills")
                    val billCount = billsArray?.length() ?: 0

                    if (billCount == 0) {
                        return@withContext Result.failure(Exception("文件中没有账单数据"))
                    }

                    val importBills = parseImportBills(billsArray)
                    val importResult = importBillsToDatabase(importBills)

                    Result.success("成功导入 ${importResult.importedCount} 条账单")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Result.failure(e)
                }
            }

            binding.layoutSyncProgress.visibility = View.GONE

            result.onSuccess { message ->
                binding.tvSyncResult.visibility = View.VISIBLE
                binding.tvSyncResult.text = message
                binding.tvSyncResult.setTextColor(getColor(R.color.success))
                addSyncLog("从云端拉取", "成功", message)
                Toast.makeText(this@DataMigrationActivity, message, Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                binding.tvSyncResult.visibility = View.VISIBLE
                binding.tvSyncResult.text = "导入失败: ${e.message}"
                binding.tvSyncResult.setTextColor(getColor(R.color.error))
                addSyncLog("从云端拉取", "失败", "导入失败: ${e.message}")
                Toast.makeText(this@DataMigrationActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class CloudFileRecyclerAdapter(
        private val files: List<WebDAVUtil.RemoteFile>,
        private val onFileClick: (WebDAVUtil.RemoteFile) -> Unit,
        private val onDeleteClick: (WebDAVUtil.RemoteFile) -> Unit
    ) : RecyclerView.Adapter<CloudFileRecyclerAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: com.example.daxijizhang.databinding.ItemCloudFileBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val binding = com.example.daxijizhang.databinding.ItemCloudFileBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.binding.tvFileName.text = file.name

            holder.itemView.setOnClickListener {
                onFileClick(file)
            }

            holder.binding.btnDelete.setOnClickListener {
                onDeleteClick(file)
            }
        }

        override fun getItemCount() = files.size
    }

    private fun addSyncLog(action: String, status: String, message: String) {
        if (!::syncLogAdapter.isInitialized) return
        val entry = SyncLogEntry(System.currentTimeMillis(), action, status, message)
        syncLogs.add(0, entry)
        syncLogAdapter.notifyItemInserted(0)
        binding.recyclerSyncLog.scrollToPosition(0)
        saveSyncLogs()
    }

    private fun saveSyncLogs() {
        while (syncLogs.size > 50) {
            syncLogs.removeAt(syncLogs.size - 1)
        }
    }

    private fun startExport() {
        lifecycleScope.launch {
            showExportProgress()

            val result = withContext(Dispatchers.IO) {
                try {
                    exportDataToFile()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            hideExportProgress()

            if (result != null) {
                showExportSuccess(result)
            } else {
                showExportFailed()
            }
        }
    }

    private suspend fun exportDataToFile(): String? {
        return try {
            val exportData = createExportData()

            val fileName = "manual_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val jsonContent = exportData.toString(2)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveFileUsingMediaStore(fileName, jsonContent)
            } else {
                saveFileLegacy(fileName, jsonContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun createExportData(): JSONObject {
        val exportData = JSONObject()

        val billsWithItems = repository.getAllBillsWithItemsList()
        val billsArray = JSONArray()

        billsWithItems.forEach { billWithItems ->
            val billJson = createBillJson(billWithItems)
            billsArray.put(billJson)
        }

        exportData.put("bills", billsArray)
        exportData.put("billCount", billsArray.length())
        exportData.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        return exportData
    }

    private suspend fun createBillJson(billWithItems: BillWithItems): JSONObject {
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

            val itemsArray = JSONArray()
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
            val recordsArray = JSONArray()
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

    private fun saveFileUsingMediaStore(fileName: String, content: String): String? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/大喜记账")
            }

            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(content)
                    }
                }
                "Download/大喜记账/$fileName"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveFileLegacy(fileName: String, content: String): String? {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadDir, "大喜记账")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            val exportFile = File(appDir, fileName)

            FileWriter(exportFile).use { writer ->
                writer.write(content)
            }

            exportFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.DIRECTORY_DOWNLOADS)
        }
        importFileLauncher.launch(intent)
    }

    private fun showImportPreview(uri: Uri) {
        lifecycleScope.launch {
            val parseResult = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val reader = BufferedReader(InputStreamReader(input))
                        val content = reader.readText()
                        val json = JSONObject(content)

                        val billsArray = json.optJSONArray("bills")
                        val billCount = billsArray?.length() ?: 0

                        if (billCount == 0) {
                            return@withContext null
                        }

                        val existingBills = repository.getAllBillsWithItemsList()
                        val existingKeys = existingBills.map { generateBillUniqueKey(it.bill) }.toSet()

                        val importBills = parseImportBills(billsArray)
                        val duplicateBills = importBills.count { existingKeys.contains(generateBillUniqueKey(it.bill)) }
                        val actualImportCount = importBills.size - duplicateBills

                        val fileInfo = "总账单数量: $billCount\n实际导入数量: $actualImportCount\n重复账单数量: $duplicateBills\n导出时间: ${json.optString("exportDate", "未知")}"
                        Triple(fileInfo, importBills, actualImportCount)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (parseResult != null && parseResult.second.isNotEmpty()) {
                pendingImportUri = uri
                pendingImportBills = parseResult.second
                pendingActualImportCount = parseResult.third
                binding.tvPreviewInfo.text = parseResult.first
                binding.cardImportPreview.visibility = View.VISIBLE
                binding.tvImportResult.visibility = View.GONE
            } else {
                Toast.makeText(this@DataMigrationActivity, R.string.invalid_import_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseImportBills(billsArray: JSONArray?): List<ImportBillData> {
        val importBills = mutableListOf<ImportBillData>()
        if (billsArray == null) return importBills

        for (i in 0 until billsArray.length()) {
            try {
                val billJson = billsArray.getJSONObject(i)

                val bill = Bill(
                    id = 0,
                    communityName = billJson.optString("communityName", ""),
                    phase = billJson.optString("phase", "").takeIf { it.isNotEmpty() },
                    buildingNumber = billJson.optString("buildingNumber", "").takeIf { it.isNotEmpty() },
                    roomNumber = billJson.optString("roomNumber", "").takeIf { it.isNotEmpty() },
                    startDate = parseDate(billJson.optString("startDate")),
                    endDate = parseDate(billJson.optString("endDate")),
                    totalAmount = billJson.optDouble("totalAmount", 0.0),
                    paidAmount = billJson.optDouble("paidAmount", 0.0),
                    waivedAmount = billJson.optDouble("waivedAmount", 0.0),
                    remark = billJson.optString("remark", "").takeIf { it.isNotEmpty() },
                    createdAt = parseDate(billJson.optString("createdAt"))
                )

                val itemsArray = billJson.optJSONArray("items")
                val items = mutableListOf<BillItem>()
                itemsArray?.let {
                    for (j in 0 until it.length()) {
                        val itemJson = it.getJSONObject(j)
                        items.add(
                            BillItem(
                                id = 0,
                                billId = 0,
                                projectName = itemJson.optString("projectName", ""),
                                unitPrice = itemJson.optDouble("unitPrice", 0.0),
                                quantity = itemJson.optDouble("quantity", 0.0),
                                totalPrice = itemJson.optDouble("totalPrice", 0.0)
                            )
                        )
                    }
                }

                val recordsArray = billJson.optJSONArray("paymentRecords")
                val paymentRecords = mutableListOf<PaymentRecord>()
                recordsArray?.let {
                    for (j in 0 until it.length()) {
                        val recordJson = it.getJSONObject(j)
                        paymentRecords.add(
                            PaymentRecord(
                                id = 0,
                                billId = 0,
                                paymentDate = parseDate(recordJson.optString("paymentDate")),
                                amount = recordJson.optDouble("amount", 0.0)
                            )
                        )
                    }
                }

                importBills.add(ImportBillData(bill, items, paymentRecords))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return importBills
    }

    private fun parseDate(dateString: String): Date {
        if (dateString.isBlank()) return Date()
        return try {
            if (dateString.contains(" ")) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateString)
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
            }
        } catch (e: Exception) {
            Date()
        } ?: Date()
    }

    private fun hideImportPreview() {
        binding.cardImportPreview.visibility = View.GONE
        pendingImportUri = null
        pendingImportBills = null
        pendingActualImportCount = 0
    }

    private fun executeImport(uri: Uri) {
        lifecycleScope.launch {
            showImportProgress()

            val importData = pendingImportBills
            if (importData.isNullOrEmpty()) {
                hideImportProgress()
                showImportFailed("没有有效的账单数据")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    importBillsToDatabase(importData)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ImportResult(false, 0, e.message)
                }
            }

            hideImportProgress()
            hideImportPreview()

            if (result.success) {
                showImportSuccess(result.importedCount)
            } else {
                showImportFailed(result.errorMessage)
            }
        }
    }

    private fun generateBillUniqueKey(bill: Bill): String {
        val sb = StringBuilder()
        sb.append(bill.communityName)
        if (!bill.phase.isNullOrBlank()) {
            sb.append("_").append(bill.phase)
        } else {
            sb.append("_")
        }
        if (!bill.buildingNumber.isNullOrBlank()) {
            sb.append("_").append(bill.buildingNumber)
        } else {
            sb.append("_")
        }
        if (!bill.roomNumber.isNullOrBlank()) {
            sb.append("_").append(bill.roomNumber)
        } else {
            sb.append("_")
        }
        return sb.toString()
    }

    private suspend fun importBillsToDatabase(importBills: List<ImportBillData>): ImportResult {
        var importedCount = 0
        var duplicateCount = 0
        var failedCount = 0

        try {
            val existingBills = repository.getAllBillsWithItemsList()
            val existingKeys = existingBills.map { generateBillUniqueKey(it.bill) }.toMutableSet()

            for (importBill in importBills) {
                try {
                    val uniqueKey = generateBillUniqueKey(importBill.bill)

                    if (existingKeys.contains(uniqueKey)) {
                        duplicateCount++
                        continue
                    }

                    val billId = repository.saveBillWithItems(
                        importBill.bill,
                        importBill.items,
                        importBill.paymentRecords,
                        importBill.bill.waivedAmount
                    )

                    if (billId > 0) {
                        importedCount++
                        existingKeys.add(uniqueKey)
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    failedCount++
                }
            }

            return if (importedCount > 0) {
                val errorMsg = when {
                    duplicateCount > 0 && failedCount > 0 -> "成功导入 $importedCount 条，重复 $duplicateCount 条，失败 $failedCount 条"
                    duplicateCount > 0 -> "成功导入 $importedCount 条，重复 $duplicateCount 条"
                    failedCount > 0 -> "成功导入 $importedCount 条，失败 $failedCount 条"
                    else -> null
                }
                ImportResult(true, importedCount, errorMsg)
            } else {
                val errorMsg = when {
                    duplicateCount > 0 -> "所有账单重复，未导入任何数据"
                    failedCount > 0 -> "所有账单导入失败"
                    else -> "没有有效的账单数据"
                }
                ImportResult(false, 0, errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(false, importedCount, e.message)
        }
    }

    data class ImportBillData(
        val bill: Bill,
        val items: List<BillItem>,
        val paymentRecords: List<PaymentRecord>
    )

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val errorMessage: String?
    )

    private fun showExportProgress() {
        binding.layoutExportProgress.visibility = View.VISIBLE
        binding.layoutExportResult.visibility = View.GONE
        binding.itemExportData.isEnabled = false
    }

    private fun hideExportProgress() {
        binding.layoutExportProgress.visibility = View.GONE
        binding.itemExportData.isEnabled = true
    }

    private fun showExportSuccess(filePath: String) {
        binding.layoutExportResult.visibility = View.VISIBLE
        binding.tvExportResult.text = getString(R.string.export_success)
        binding.tvExportPath.text = getString(R.string.export_path, filePath)
        Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
    }

    private fun showExportFailed() {
        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
    }

    private fun showImportProgress() {
        binding.layoutImportProgress.visibility = View.VISIBLE
        binding.tvImportResult.visibility = View.GONE
    }

    private fun hideImportProgress() {
        binding.layoutImportProgress.visibility = View.GONE
    }

    private fun showImportSuccess(importedCount: Int) {
        binding.tvImportResult.visibility = View.VISIBLE
        val successMessage = getString(R.string.import_success_with_count, importedCount)
        binding.tvImportResult.text = successMessage
        binding.tvImportResult.setTextColor(getColor(R.color.success))
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
    }

    private fun showImportFailed(errorMessage: String? = null) {
        binding.tvImportResult.visibility = View.VISIBLE
        val failedMessage = errorMessage?.let {
            getString(R.string.import_failed_with_reason, it)
        } ?: getString(R.string.import_failed)
        binding.tvImportResult.text = failedMessage
        binding.tvImportResult.setTextColor(getColor(R.color.error))
        Toast.makeText(this, failedMessage, Toast.LENGTH_LONG).show()
    }

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

            val statusColor = when (log.status) {
                "成功" -> R.color.success
                "失败" -> R.color.error
                else -> R.color.info
            }
            holder.binding.tvLogStatus.setTextColor(getColor(statusColor))
        }

        override fun getItemCount() = logs.size
    }

    companion object {
        const val AUTO_BACKUP_FILE_PREFIX = "auto_backup_"
        const val AUTO_CLOUD_BACKUP_PREFIX = "auto_backup_"
    }
}
