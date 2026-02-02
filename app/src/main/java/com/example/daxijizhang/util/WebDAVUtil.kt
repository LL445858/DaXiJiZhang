package com.example.daxijizhang.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * WebDAV 工具类（坚果云）
 *
 * ✅ 已统一迁移为 OkHttp：支持 PROPFIND / MKCOL 等 WebDAV 方法
 * ✅ 统一的认证 / User-Agent / 超时 / 重试 / 日志
 *
 * 依赖：implementation("com.squareup.okhttp3:okhttp:4.12.0")
 */
object WebDAVUtil {

    private const val TAG = "WebDAV"
    private const val DEFAULT_FOLDER = "daxijizhang"
    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 1000L

    // 关键：坚果云常见需要自定义 UA，否则可能 403
    private const val USER_AGENT = "DaxiJizhang/1.0 (Android)"

    // 统一超时（与原代码一致）
    private const val CONNECT_TIMEOUT_SEC = 30L
    private const val READ_TIMEOUT_SEC = 30L
    private const val WRITE_TIMEOUT_SEC = 30L

    // --- OkHttpClient 单例复用 ---
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    data class WebDAVConfig(
        val serverUrl: String,
        val username: String,
        val password: String
    )

    data class RemoteFile(
        val name: String,
        val path: String,
        val size: Long,
        val modifiedTime: Date
    )

    data class SyncStatus(
        val isSuccess: Boolean,
        val message: String,
        val progress: Int = 0
    )

    // --- Basic Auth（沿用 android.util.Base64） ---
    private fun createAuthHeader(config: WebDAVConfig): String {
        val credentials = "${config.username}:${config.password}"
        return "Basic " + android.util.Base64.encodeToString(
            credentials.toByteArray(),
            android.util.Base64.NO_WRAP
        )
    }

    private fun normalizeServerUrl(serverUrl: String): String {
        var url = serverUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return url
    }

    private fun buildFullPath(config: WebDAVConfig, relativePath: String): String {
        val baseUrl = normalizeServerUrl(config.serverUrl)
        val cleanRelativePath = relativePath.removePrefix("/")
        return "$baseUrl$cleanRelativePath"
    }

    // --- 统一日志 ---
    private fun logRequest(method: String, url: String) {
        android.util.Log.d(TAG, "=== Request ===")
        android.util.Log.d(TAG, "Method: $method")
        android.util.Log.d(TAG, "URL: $url")
    }

    private fun logResponse(statusCode: Int, message: String, body: String? = null) {
        android.util.Log.d(TAG, "=== Response ===")
        android.util.Log.d(TAG, "Status Code: $statusCode")
        android.util.Log.d(TAG, "Status Message: $message")
        body?.let {
            android.util.Log.d(TAG, "Body: ${it.take(1000)}")
        }
    }

    // --- 统一构建 Request：自动带 Authorization/User-Agent/Connection: close ---
    private fun newRequestBuilder(config: WebDAVConfig, url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", createAuthHeader(config))
            .header("User-Agent", USER_AGENT)
            .header("Connection", "close")
    }

    /**
     * 统一执行请求 + 处理重试
     *
     * - 200..299 视作成功（但 WebDAV 的 PROPFIND 常返回 207，也视作成功）
     * - 401/403 直接失败（通常无需重试）
     */
    private suspend fun <T> executeWithRetry(
        actionName: String,
        onStatusUpdate: ((SyncStatus) -> Unit)? = null,
        block: () -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                val result = block()
                // 成功直接返回
                if (result.isSuccess) return@withContext result

                // 失败时：如果是权限类错误，通常不需要重试
                val exMsg = result.exceptionOrNull()?.message.orEmpty()
                if (exMsg.contains("401") || exMsg.contains("403") ||
                    exMsg.contains("认证失败") || exMsg.contains("访问拒绝")
                ) {
                    return@withContext result
                }

                if (attempt < MAX_RETRY_COUNT - 1) {
                    onStatusUpdate?.invoke(SyncStatus(false, "$actionName 失败，重试中(${attempt + 1}/${MAX_RETRY_COUNT})...", 0))
                    delay(RETRY_DELAY_MS)
                } else {
                    return@withContext result
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "$actionName error", e)
                if (attempt < MAX_RETRY_COUNT - 1) {
                    onStatusUpdate?.invoke(SyncStatus(false, "$actionName 异常，重试中(${attempt + 1}/${MAX_RETRY_COUNT})...", 0))
                    delay(RETRY_DELAY_MS)
                } else {
                    return@withContext Result.failure(e)
                }
            }
        }
        Result.failure(Exception("$actionName 失败"))
    }

    /**
     * 连接验证：OPTIONS 根路径
     */
    suspend fun verifyConnection(config: WebDAVConfig): Result<Boolean> = executeWithRetry("连接验证") {
        try {
            val url = normalizeServerUrl(config.serverUrl)
            val request = newRequestBuilder(config, url)
                .method("OPTIONS", null)
                .build()

            logRequest("OPTIONS", url)

            httpClient.newCall(request).execute().use { resp ->
                val code = resp.code
                logResponse(code, resp.message)

                return@use when (code) {
                    in 200..299 -> Result.success(true)
                    401 -> Result.failure(Exception("认证失败(401)，请检查用户名和密码/应用密码"))
                    403 -> Result.failure(Exception("访问被拒绝(403)，请检查账户权限或 User-Agent 策略"))
                    in 500..599 -> Result.failure(Exception("服务器错误($code)"))
                    else -> Result.failure(Exception("连接失败($code)"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 确保目录存在：先 OPTIONS 检测；404 则 MKCOL
     */
    suspend fun ensureDirectoryExists(
        config: WebDAVConfig,
        folderName: String = DEFAULT_FOLDER
    ): Result<Boolean> = executeWithRetry("检查/创建目录") {
        try {
            val folderUrl = buildFullPath(config, "$folderName/")

            // 1) OPTIONS 检测目录
            val checkReq = newRequestBuilder(config, folderUrl)
                .method("OPTIONS", null)
                .build()

            logRequest("OPTIONS", folderUrl)

            httpClient.newCall(checkReq).execute().use { resp ->
                val code = resp.code
                logResponse(code, resp.message)

                when (code) {
                    in 200..299 -> return@use Result.success(true)
                    404 -> {
                        // 2) 创建目录 MKCOL
                        return@use createDirectory(config, folderUrl)
                    }
                    401 -> return@use Result.failure(Exception("权限不足或认证失败(401)"))
                    403 -> return@use Result.failure(Exception("访问被拒绝(403)"))
                    else -> return@use Result.failure(Exception("无法访问目录($code)"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createDirectory(config: WebDAVConfig, folderUrl: String): Result<Boolean> {
        return try {
            val req = newRequestBuilder(config, folderUrl)
                .method("MKCOL", null)
                .build()

            logRequest("MKCOL", folderUrl)

            httpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                logResponse(code, resp.message)

                when (code) {
                    in 200..299, 201 -> Result.success(true)
                    405 -> Result.success(true) // 通常表示目录已存在
                    401 -> Result.failure(Exception("创建目录失败：认证失败(401)"))
                    403 -> Result.failure(Exception("创建目录失败：访问拒绝(403)"))
                    else -> Result.failure(Exception("创建目录失败: HTTP $code"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 上传文件：PUT
     */
    suspend fun uploadFile(
        config: WebDAVConfig,
        remotePath: String,
        content: String,
        onStatusUpdate: ((SyncStatus) -> Unit)? = null
    ): Result<String> = executeWithRetry("上传文件", onStatusUpdate) {
        try {
            onStatusUpdate?.invoke(SyncStatus(true, "正在上传...", 30))

            val fullPath = buildFullPath(config, remotePath)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = content.toRequestBody(mediaType)

            val request = newRequestBuilder(config, fullPath)
                .put(body)
                .build()

            logRequest("PUT", fullPath)

            httpClient.newCall(request).execute().use { resp ->
                val code = resp.code
                val respBody = resp.body?.string()
                logResponse(code, resp.message, respBody)

                return@use if (code in 200..299 || code == 201 || code == 204) {
                    onStatusUpdate?.invoke(SyncStatus(true, "上传成功", 100))
                    Result.success("上传成功")
                } else if (code == 401) {
                    Result.failure(Exception("上传失败：认证失败(401)"))
                } else if (code == 403) {
                    Result.failure(Exception("上传失败：访问拒绝(403)"))
                } else {
                    Result.failure(Exception("上传失败 Code: $code"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 下载文件：GET
     */
    suspend fun downloadFile(
        config: WebDAVConfig,
        remotePath: String
    ): Result<String> = executeWithRetry("下载文件") {
        try {
            val fullPath = buildFullPath(config, remotePath)

            val request = newRequestBuilder(config, fullPath)
                .get()
                .build()

            logRequest("GET", fullPath)

            httpClient.newCall(request).execute().use { resp ->
                val code = resp.code
                val bodyStr = resp.body?.string()

                logResponse(code, resp.message, bodyStr?.let { "Length: ${it.length}" })

                return@use when (code) {
                    in 200..299 -> Result.success(bodyStr.orEmpty())
                    404 -> Result.failure(Exception("文件不存在(404)"))
                    401 -> Result.failure(Exception("下载失败：认证失败(401)"))
                    403 -> Result.failure(Exception("下载失败：访问拒绝(403)"))
                    else -> Result.failure(Exception("下载失败: HTTP $code"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 列表：PROPFIND（WebDAV）
     */
    suspend fun listFiles(
        config: WebDAVConfig,
        folderName: String = DEFAULT_FOLDER,
        onStatusUpdate: ((SyncStatus) -> Unit)? = null
    ): Result<List<RemoteFile>> = executeWithRetry("获取文件列表", onStatusUpdate) {
        try {
            onStatusUpdate?.invoke(SyncStatus(true, "正在获取列表...", 20))

            val folderUrl = buildFullPath(config, "$folderName/")

            // 建议带最小 XML body，提高兼容性
            val xml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:getlastmodified/>
                    <d:getcontentlength/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()

            val body = xml.toRequestBody("text/xml; charset=utf-8".toMediaType())

            val request = newRequestBuilder(config, folderUrl)
                .method("PROPFIND", body)
                .header("Depth", "1")
                .build()

            logRequest("PROPFIND", folderUrl)

            httpClient.newCall(request).execute().use { resp ->
                val code = resp.code
                val respBody = resp.body?.string()

                logResponse(code, resp.message, respBody?.let { "Length: ${it.length}" })

                return@use when {
                    code == 207 || code in 200..299 -> {
                        val files = parseWebDavXml(respBody.orEmpty(), folderName)
                        onStatusUpdate?.invoke(SyncStatus(true, "获取列表成功", 50))
                        Result.success(files)
                    }
                    code == 404 -> Result.success(emptyList())
                    code == 401 -> Result.failure(Exception("认证失败(401)：请检查用户名/应用密码"))
                    code == 403 -> Result.failure(Exception("访问拒绝(403)：请检查权限或 User-Agent/账户策略"))
                    else -> Result.failure(Exception("获取文件列表失败: HTTP $code, body=${respBody?.take(200)}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- XML 解析：保持你的原逻辑（微调：URLDecoder import、健壮性） ---
    private fun parseWebDavXml(xml: String, folderName: String): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        try {
            val responses = xml.split("<d:response", "<D:response", "<response")

            for (responseFragment in responses) {
                if (responseFragment.isBlank()) continue

                val hrefMatch = "(<d:href>|<D:href>|<href>)(.*?)(</d:href>|</D:href>|</href>)".toRegex()
                    .find(responseFragment)
                var rawHref = hrefMatch?.groupValues?.get(2)?.trim() ?: continue

                rawHref = try {
                    URLDecoder.decode(rawHref, "UTF-8")
                } catch (_: Exception) {
                    rawHref
                }

                // 过滤目录本身（目录一般以 / 结尾）
                if (rawHref.endsWith("/") || rawHref.endsWith(folderName) || rawHref.endsWith("$folderName/")) {
                    continue
                }

                val fileName = rawHref.substringAfterLast("/")
                if (!fileName.endsWith(".json")) continue

                val sizeMatch = "(<d:getcontentlength>|<D:getcontentlength>|<getcontentlength>)(.*?)(</d:getcontentlength>|</D:getcontentlength>|</getcontentlength>)"
                    .toRegex().find(responseFragment)
                val size = sizeMatch?.groupValues?.get(2)?.toLongOrNull() ?: 0L

                val timeMatch = "(<d:getlastmodified>|<D:getlastmodified>|<getlastmodified>)(.*?)(</d:getlastmodified>|</D:getlastmodified>|</getlastmodified>)"
                    .toRegex().find(responseFragment)
                val timeStr = timeMatch?.groupValues?.get(2) ?: ""

                files.add(
                    RemoteFile(
                        name = fileName,
                        path = "$folderName/$fileName",
                        size = size,
                        modifiedTime = if (timeStr.isNotEmpty()) parseDate(timeStr) else Date()
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "XML Parsing error", e)
        }
        return files.sortedByDescending { it.modifiedTime }
    }

    private fun parseDate(dateString: String): Date {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (formatStr in formats) {
            try {
                val sdf = SimpleDateFormat(formatStr, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                return sdf.parse(dateString) ?: continue
            } catch (_: Exception) {
                // try next
            }
        }
        return Date()
    }

    // --- 文件名生成辅助方法（保持不变） ---
    fun generateManualPushFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${DEFAULT_FOLDER}_手动_$timestamp.json"
    }

    fun generateAutoPushFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${DEFAULT_FOLDER}_自动_$timestamp.json"
    }

    fun isAutoPushFile(fileName: String): Boolean {
        return fileName.startsWith("${DEFAULT_FOLDER}_自动_")
    }

    fun isManualPushFile(fileName: String): Boolean {
        return fileName.startsWith("${DEFAULT_FOLDER}_手动_")
    }
}
