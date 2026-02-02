package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.window.OnBackInvokedDispatcher
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
import com.example.daxijizhang.databinding.ItemSyncLogBinding
import com.example.daxijizhang.util.ViewUtil
import com.example.daxijizhang.util.WebDAVUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
        loadSettings()
        setupSyncLogRecycler()
    }

    private fun setupBackPressHandler() {
        // Android 13+ 使用新的预测性返回手势API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finishWithAnimation()
            }
        } else {
            // 低版本使用OnBackPressedDispatcher
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithAnimation()
                }
            })
        }
    }

    /**
     * 设置进入动画（前进动画）
     */
    private fun setupForwardTransition() {
        // Android 13+ 使用新的过渡API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    /**
     * 设置返回动画（后退动画）并结束Activity
     */
    private fun finishWithAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的API
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            // 低版本仍然需要使用旧API
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
        // 导出数据 - 使用MediaStore API，无需权限申请
        binding.btnExportData.setOnClickListener {
            ViewUtil.applyClickAnimation(it) {
                startExport()
            }
        }

        // 导入数据 - 使用SAF，无需权限申请
        binding.btnImportData.setOnClickListener {
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
        val serverUrl = "https://dav.jianguoyun.com/dav/"
        val username = prefs.getString("webdav_username", "")
        val password = prefs.getString("webdav_password", "")

        binding.etUsername.setText(username)
        binding.etPassword.setText(password)

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
        val serverUrl = "https://dav.jianguoyun.com/dav/"
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty()) {
            binding.tilUsername.error = getString(R.string.username)
            return
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.password_or_key)
            return
        }

        val config = WebDAVUtil.WebDAVConfig(serverUrl, username, password)

        binding.layoutConnectionStatus.visibility = View.VISIBLE
        binding.ivConnectionStatus.setImageResource(R.drawable.ic_sync)
        binding.ivConnectionStatus.setColorFilter(getColor(R.color.info))
        binding.tvConnectionStatus.text = getString(R.string.verifying)
        binding.tvConnectionStatus.setTextColor(getColor(R.color.info))
        binding.btnVerifyConnection.isEnabled = false

        lifecycleScope.launch {
            val result = WebDAVUtil.verifyConnection(config)

            binding.btnVerifyConnection.isEnabled = true

            result.onSuccess {
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_check)
                binding.ivConnectionStatus.setColorFilter(getColor(R.color.success))
                binding.tvConnectionStatus.text = getString(R.string.connection_success)
                binding.tvConnectionStatus.setTextColor(getColor(R.color.success))
                Toast.makeText(this@DataMigrationActivity, R.string.connection_success, Toast.LENGTH_SHORT).show()
                
                prefs.edit().apply {
                    putString("webdav_username", username)
                    putString("webdav_password", password)
                    apply()
                }
                
                addSyncLog("验证连接", "成功", "坚果云连接验证成功")
            }.onFailure { e ->
                binding.ivConnectionStatus.setImageResource(R.drawable.ic_close)
                binding.ivConnectionStatus.setColorFilter(getColor(R.color.error))
                binding.tvConnectionStatus.text = getString(R.string.connection_failed)
                binding.tvConnectionStatus.setTextColor(getColor(R.color.error))
                Toast.makeText(this@DataMigrationActivity, R.string.connection_failed, Toast.LENGTH_SHORT).show()
                
                addSyncLog("验证连接", "失败", "坚果云连接验证失败: ${e.message}")
            }
        }
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
        binding.btnPushToCloud.isEnabled = false
        binding.btnPullFromCloud.isEnabled = false

        val actionText = if (direction == "push") "推送到云端" else "从云端拉取"
        binding.tvSyncStatus.text = getString(R.string.syncing_with_action, actionText)

        lifecycleScope.launch {
            val result = if (direction == "push") {
                pushToCloud(config)
            } else {
                pullFromCloud(config)
            }

            binding.layoutSyncProgress.visibility = View.GONE
            binding.btnPushToCloud.isEnabled = true
            binding.btnPullFromCloud.isEnabled = true

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
                // 更新状态：检查目录
                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在检查云端目录..."
                }
                
                val ensureResult = WebDAVUtil.ensureDirectoryExists(config)
                if (ensureResult.isFailure) {
                    throw ensureResult.exceptionOrNull() ?: Exception("创建目录失败")
                }

                // 更新状态：导出数据
                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在准备数据..."
                }
                
                val exportData = createExportData()
                val jsonContent = exportData.toString(2)
                val fileName = WebDAVUtil.generateManualPushFileName()
                val remotePath = "daxijizhang/$fileName"

                // 更新状态：上传文件
                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在上传文件..."
                }
                
                val uploadResult = WebDAVUtil.uploadFile(config, remotePath, jsonContent) { status ->
                    // 在UI线程更新状态
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
                // 更新状态：获取文件列表
                withContext(Dispatchers.Main) {
                    binding.tvSyncStatus.text = "正在获取云端文件列表..."
                }
                
                val listResult = WebDAVUtil.listFiles(config) { status ->
                    // 在UI线程更新状态
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

                // 在UI线程显示对话框
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
        val listView = android.widget.ListView(this)
        val adapter = CloudFileAdapter(files) { file ->
            showCloudFileImportDialog(config, file)
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val file = files[position]
            showCloudFileImportDialog(config, file)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("选择云端备份文件")
            .setNegativeButton("取消", null)
            .setView(listView)
            .create()
        dialog.show()
    }

    private fun showCloudFileImportDialog(config: WebDAVUtil.WebDAVConfig, file: WebDAVUtil.RemoteFile) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("导入备份文件")
            .setMessage("确定要导入文件：${file.name}？")
            .setPositiveButton("导入") { _, _ ->
                importCloudFile(config, file)
            }
            .setNegativeButton("取消", null)
            .create()
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

    inner class CloudFileAdapter(
        private val files: List<WebDAVUtil.RemoteFile>,
        private val onFileClick: (WebDAVUtil.RemoteFile) -> Unit
    ) : android.widget.ArrayAdapter<WebDAVUtil.RemoteFile>(
        this@DataMigrationActivity,
        android.R.layout.simple_list_item_2,
        files
    ) {
        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = convertView ?: android.view.LayoutInflater.from(context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)

            val file = files[position]
            val titleText = view.findViewById<android.widget.TextView>(android.R.id.text1)
            val subtitleText = view.findViewById<android.widget.TextView>(android.R.id.text2)

            titleText.text = file.name
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            subtitleText.text = "修改时间: ${dateFormat.format(file.modifiedTime)}"

            return view
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

            // 保存到文件 - 使用MediaStore API，无需权限申请
            val fileName = "大喜记账备份_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val jsonContent = exportData.toString(2)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用MediaStore
                saveFileUsingMediaStore(fileName, jsonContent)
            } else {
                // Android 9及以下使用传统方式
                saveFileLegacy(fileName, jsonContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 创建导出数据JSON对象 - 只包含账单数据，不包含APP信息
     */
    private suspend fun createExportData(): JSONObject {
        val exportData = JSONObject()

        // 从数据库读取实际数据 - 使用同步查询
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

    /**
     * 创建单个账单的JSON对象
     */
    private suspend fun createBillJson(billWithItems: BillWithItems): JSONObject {
        val bill = billWithItems.bill
        return JSONObject().apply {
            // 账单基本信息
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

            // 账单装修项目
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

            // 结付记录
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

    /**
     * 使用MediaStore保存文件 - Android 10+ 无需权限
     */
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
                // 返回文件路径
                "Download/大喜记账/$fileName"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 传统方式保存文件 - Android 9及以下
     */
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

    /**
     * 解析导入的账单数据
     */
    private fun parseImportBills(billsArray: JSONArray?): List<ImportBillData> {
        val importBills = mutableListOf<ImportBillData>()
        if (billsArray == null) return importBills

        for (i in 0 until billsArray.length()) {
            try {
                val billJson = billsArray.getJSONObject(i)

                // 解析账单基本信息 - 使用新ID避免冲突
                val bill = Bill(
                    id = 0, // 新数据，使用自增ID，避免覆盖现有数据
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

                // 解析账单项目
                val itemsArray = billJson.optJSONArray("items")
                val items = mutableListOf<BillItem>()
                itemsArray?.let {
                    for (j in 0 until it.length()) {
                        val itemJson = it.getJSONObject(j)
                        items.add(
                            BillItem(
                                id = 0,
                                billId = 0, // 将在保存时更新
                                projectName = itemJson.optString("projectName", ""),
                                unitPrice = itemJson.optDouble("unitPrice", 0.0),
                                quantity = itemJson.optDouble("quantity", 0.0),
                                totalPrice = itemJson.optDouble("totalPrice", 0.0)
                            )
                        )
                    }
                }

                // 解析结付记录
                val recordsArray = billJson.optJSONArray("paymentRecords")
                val paymentRecords = mutableListOf<PaymentRecord>()
                recordsArray?.let {
                    for (j in 0 until it.length()) {
                        val recordJson = it.getJSONObject(j)
                        paymentRecords.add(
                            PaymentRecord(
                                id = 0,
                                billId = 0, // 将在保存时更新
                                paymentDate = parseDate(recordJson.optString("paymentDate")),
                                amount = recordJson.optDouble("amount", 0.0)
                            )
                        )
                    }
                }

                importBills.add(ImportBillData(bill, items, paymentRecords))
            } catch (e: Exception) {
                e.printStackTrace()
                // 跳过解析失败的账单，继续处理其他账单
            }
        }

        return importBills
    }

    /**
     * 解析日期字符串
     */
    private fun parseDate(dateString: String): Date {
        if (dateString.isBlank()) return Date()
        return try {
            if (dateString.contains(" ")) {
                // 完整日期时间格式
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateString)
            } else {
                // 仅日期格式
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
            }
        } catch (e: Exception) {
            Date() // 解析失败返回当前时间
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

    /**
     * 生成账单的唯一标识符（小区名+期数+楼栋号+门牌号）
     */
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

    /**
     * 将导入的账单数据写入数据库 - 追加模式，过滤重复账单
     */
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

    /**
     * 导入数据封装类
     */
    data class ImportBillData(
        val bill: Bill,
        val items: List<BillItem>,
        val paymentRecords: List<PaymentRecord>
    )

    /**
     * 导入结果封装类
     */
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val errorMessage: String?
    )

    private fun showExportProgress() {
        binding.layoutExportProgress.visibility = View.VISIBLE
        binding.layoutExportResult.visibility = View.GONE
        binding.btnExportData.isEnabled = false
    }

    private fun hideExportProgress() {
        binding.layoutExportProgress.visibility = View.GONE
        binding.btnExportData.isEnabled = true
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
