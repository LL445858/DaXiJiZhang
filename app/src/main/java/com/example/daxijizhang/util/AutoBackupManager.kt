package com.example.daxijizhang.util

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.daxijizhang.data.database.AppDatabase
import com.example.daxijizhang.data.model.Bill
import com.example.daxijizhang.data.model.BillItem
import com.example.daxijizhang.data.model.BillWithItems
import com.example.daxijizhang.data.model.PaymentRecord
import com.example.daxijizhang.data.repository.BillRepository
import com.example.daxijizhang.data.repository.ProjectDictionaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class AutoBackupManager private constructor(private val context: Context) {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: BillRepository
    private lateinit var projectDictionaryRepository: ProjectDictionaryRepository
    private val TAG = "AutoBackupManager"
    
    private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val AUTO_BACKUP_FILE_PREFIX = "auto_backup_"
        const val AUTO_CLOUD_BACKUP_PREFIX = "auto_backup_"
        const val MAX_AUTO_BACKUP_COUNT = 1

        @Volatile
        private var instance: AutoBackupManager? = null

        fun getInstance(context: Context): AutoBackupManager {
            return instance ?: synchronized(this) {
                instance ?: AutoBackupManager(context.applicationContext).also {
                    it.init()
                    instance = it
                }
            }
        }
    }

    private fun init() {
        prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        val database = AppDatabase.getDatabase(context)
        repository = BillRepository(
            database.billDao(),
            database.billItemDao(),
            database.paymentRecordDao()
        )
        projectDictionaryRepository = ProjectDictionaryRepository(database.projectDictionaryDao())
    }

    fun performAutoBackupIfNeeded() {
        val autoLocalBackup = prefs.getBoolean("auto_local_backup", false)
        val autoCloudBackup = prefs.getBoolean("auto_cloud_backup", false)

        if (!autoLocalBackup && !autoCloudBackup) {
            return
        }

        backupScope.launch {
            try {
                if (autoLocalBackup) {
                    performAutoLocalBackup()
                }
                if (autoCloudBackup) {
                    performAutoCloudBackup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动备份失败", e)
            }
        }
    }

    private suspend fun performAutoLocalBackup() {
        withContext(Dispatchers.IO) {
            try {
                // 先创建新备份
                val exportData = createExportData()
                val fileName = "${AUTO_BACKUP_FILE_PREFIX}${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val jsonContent = exportData.toString(2)

                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveFileUsingMediaStore(fileName, jsonContent)
                } else {
                    saveFileLegacy(fileName, jsonContent)
                }

                if (result != null) {
                    Log.i(TAG, "自动本地备份成功: $result")
                    saveBackupLog("自动本地备份", "成功", "文件: $result")
                    // 新备份创建成功后，再删除旧备份
                    deleteOldAutoBackups()
                } else {
                    Log.e(TAG, "自动本地备份失败")
                    saveBackupLog("自动本地备份", "失败", "保存文件失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动本地备份异常", e)
                saveBackupLog("自动本地备份", "失败", "异常: ${e.message}")
            }
        }
    }

    private suspend fun performAutoCloudBackup() {
        withContext(Dispatchers.IO) {
            try {
                val username = prefs.getString("webdav_username", "") ?: ""
                val password = prefs.getString("webdav_password", "") ?: ""

                if (username.isEmpty() || password.isEmpty()) {
                    Log.w(TAG, "自动云备份失败: 未配置坚果云账户")
                    saveBackupLog("自动云备份", "失败", "未配置坚果云账户")
                    return@withContext
                }

                val serverUrl = "https://dav.jianguoyun.com/dav/"
                val config = WebDAVUtil.WebDAVConfig(serverUrl, username, password)

                val ensureResult = WebDAVUtil.ensureDirectoryExists(config)
                if (ensureResult.isFailure) {
                    throw ensureResult.exceptionOrNull() ?: Exception("创建目录失败")
                }

                // 先创建新备份
                val exportData = createExportData()
                val jsonContent = exportData.toString(2)
                val fileName = "${AUTO_CLOUD_BACKUP_PREFIX}${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val remotePath = "daxijizhang/$fileName"

                val uploadResult = WebDAVUtil.uploadFile(config, remotePath, jsonContent) { }

                if (uploadResult.isSuccess) {
                    val billCount = exportData.optJSONArray("bills")?.length() ?: 0
                    Log.i(TAG, "自动云备份成功: $billCount 条记录")
                    saveBackupLog("自动云备份", "成功", "推送 $billCount 条记录到云端")
                    // 新备份创建成功后，再删除旧备份
                    deleteOldAutoCloudBackups(username, password)
                } else {
                    Log.e(TAG, "自动云备份失败")
                    saveBackupLog("自动云备份", "失败", "上传失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动云备份异常", e)
                saveBackupLog("自动云备份", "失败", "异常: ${e.message}")
            }
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
        
        // 添加项目词典数据
        val projectDictionary = projectDictionaryRepository.getAllProjectsSync()
        val dictionaryArray = JSONArray()
        projectDictionary.forEach { project ->
            val projectJson = JSONObject().apply {
                put("name", project.name)
                put("usageCount", project.usageCount)
            }
            dictionaryArray.put(projectJson)
        }
        exportData.put("projectDictionary", dictionaryArray)
        exportData.put("dictionaryCount", dictionaryArray.length())
        
        exportData.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        exportData.put("isAutoBackup", true)

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
            billWithItems.items.sortedBy { it.orderIndex }.forEach { item ->
                val itemJson = JSONObject().apply {
                    put("projectName", item.projectName)
                    put("unitPrice", item.unitPrice)
                    put("quantity", item.quantity)
                    put("totalPrice", item.totalPrice)
                    put("orderIndex", item.orderIndex)
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

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
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

    private fun deleteOldAutoBackups() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                deleteOldAutoBackupsMediaStore()
            } else {
                deleteOldAutoBackupsLegacy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除旧自动备份失败", e)
        }
    }

    private fun deleteOldAutoBackupsMediaStore() {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$AUTO_BACKUP_FILE_PREFIX%", "%大喜记账%")

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                count++
                if (count > MAX_AUTO_BACKUP_COUNT) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    context.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    private fun deleteOldAutoBackupsLegacy() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadDir, "大喜记账")
        if (!appDir.exists()) return

        val backupFiles = appDir.listFiles { file ->
            file.name.startsWith(AUTO_BACKUP_FILE_PREFIX) && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }

        backupFiles?.let { files ->
            if (files.size > MAX_AUTO_BACKUP_COUNT) {
                files.drop(MAX_AUTO_BACKUP_COUNT).forEach { file ->
                    file.delete()
                }
            }
        }
    }

    private suspend fun deleteOldAutoCloudBackups(username: String, password: String) {
        try {
            val serverUrl = "https://dav.jianguoyun.com/dav/"
            val config = WebDAVUtil.WebDAVConfig(serverUrl, username, password)

            val listResult = WebDAVUtil.listFiles(config) { }
            if (listResult.isFailure) return

            val files = listResult.getOrNull() ?: return
            val autoBackupFiles = files.filter { it.name.startsWith(AUTO_CLOUD_BACKUP_PREFIX) }
                .sortedByDescending { it.modifiedTime }

            if (autoBackupFiles.size > MAX_AUTO_BACKUP_COUNT) {
                autoBackupFiles.drop(MAX_AUTO_BACKUP_COUNT).forEach { file ->
                    WebDAVUtil.deleteFile(config, file.path)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除旧云端自动备份失败", e)
        }
    }

    private fun saveBackupLog(action: String, status: String, message: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val logEntry = "$timestamp|$action|$status|$message"
            val logs = prefs.getString("auto_backup_logs", "")?.split("\n")?.toMutableList() ?: mutableListOf()
            logs.add(0, logEntry)
            while (logs.size > 20) {
                logs.removeAt(logs.size - 1)
            }
            prefs.edit().putString("auto_backup_logs", logs.joinToString("\n")).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存备份日志失败", e)
        }
    }
    
    fun shutdown() {
        try {
            backupScope.cancel()
            Log.i(TAG, "AutoBackupManager shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
