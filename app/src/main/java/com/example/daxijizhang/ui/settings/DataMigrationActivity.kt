package com.example.daxijizhang.ui.settings

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
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
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.example.daxijizhang.R
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.databinding.ActivityDataMigrationBinding
import com.example.daxijizhang.util.ViewUtil
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataMigrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRepository()
        initViews()
        setupClickListeners()
        setupBackPressHandler()
        setupForwardTransition()
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

                        // 解析账单数据用于后续导入
                        val importBills = parseImportBills(billsArray)

                        val fileInfo = "账单数量: $billCount\n导出时间: ${json.optString("exportDate", "未知")}"
                        Pair(fileInfo, importBills)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (parseResult != null && parseResult.second.isNotEmpty()) {
                pendingImportUri = uri
                pendingImportBills = parseResult.second
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
     * 将导入的账单数据写入数据库 - 追加模式
     */
    private suspend fun importBillsToDatabase(importBills: List<ImportBillData>): ImportResult {
        var importedCount = 0
        var failedCount = 0

        try {
            for (importBill in importBills) {
                try {
                    // 使用repository的saveBillWithItems方法保存账单及其项目
                    // id=0确保是新记录，不会覆盖现有数据
                    val billId = repository.saveBillWithItems(
                        importBill.bill,
                        importBill.items,
                        importBill.paymentRecords,
                        importBill.bill.waivedAmount
                    )

                    if (billId > 0) {
                        importedCount++
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    failedCount++
                }
            }

            return if (importedCount > 0) {
                val errorMsg = if (failedCount > 0) "成功导入 $importedCount 条，失败 $failedCount 条" else null
                ImportResult(true, importedCount, errorMsg)
            } else {
                ImportResult(false, 0, "所有账单导入失败")
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

}
