package com.example.daxijizhang.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection

object WebDAVUtil {

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

    private fun addPreemptiveAuthHeader(connection: HttpURLConnection, config: WebDAVConfig) {
        val auth = android.util.Base64.encodeToString(
            "${config.username}:${config.password}".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        connection.setRequestProperty("Authorization", "Basic $auth")
    }

    private fun addWebDAVHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("Depth", "0")
        connection.setRequestProperty("Accept", "*/*")
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

    private fun buildPath(basePath: String, relativePath: String): String {
        val normalizedBase = if (basePath.endsWith("/")) basePath else "$basePath/"
        val normalizedRelative = relativePath.removePrefix("/")
        return "$normalizedBase$normalizedRelative"
    }

    suspend fun verifyConnection(config: WebDAVConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val serverPath = normalizeServerUrl(config.serverUrl)
            val url = URL(serverPath)
            val connection = url.openConnection() as HttpURLConnection
            addPreemptiveAuthHeader(connection, config)
            connection.requestMethod = "OPTIONS"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val statusCode = connection.responseCode
            when {
                statusCode in 200..299 -> Result.success(true)
                statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
                statusCode == HttpURLConnection.HTTP_FORBIDDEN -> Result.failure(Exception("访问被拒绝，请检查账户权限"))
                statusCode >= 500 -> Result.failure(Exception("服务器错误，请稍后重试"))
                else -> Result.failure(Exception("连接失败，状态码: $statusCode，请检查服务器地址和网络连接"))
            }
        } catch (e: java.net.UnknownHostException) {
            e.printStackTrace()
            Result.failure(Exception("无法连接到服务器，请检查服务器地址是否正确"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("网络连接失败，请检查网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("连接超时，请检查网络连接或稍后重试"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    suspend fun uploadFile(
        config: WebDAVConfig,
        remotePath: String,
        content: String,
        contentType: String = "application/json"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val serverPath = normalizeServerUrl(config.serverUrl)
            val fullPath = buildPath(serverPath, remotePath)
            val url = URL(fullPath)
            val connection = url.openConnection() as HttpURLConnection
            addPreemptiveAuthHeader(connection, config)
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(content)
            writer.flush()
            writer.close()

            val statusCode = connection.responseCode
            when {
                statusCode in 200..299 -> Result.success(true)
                statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
                statusCode == HttpURLConnection.HTTP_FORBIDDEN -> Result.failure(Exception("访问被拒绝，请检查账户权限"))
                statusCode >= 500 -> Result.failure(Exception("服务器错误，请稍后重试"))
                else -> Result.failure(Exception("上传失败，状态码: $statusCode"))
            }
        } catch (e: java.net.UnknownHostException) {
            e.printStackTrace()
            Result.failure(Exception("无法连接到服务器，请检查服务器地址是否正确"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("网络连接失败，请检查网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("连接超时，请检查网络连接或稍后重试"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("上传失败: ${e.message}"))
        }
    }

    suspend fun downloadFile(
        config: WebDAVConfig,
        remotePath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val serverPath = normalizeServerUrl(config.serverUrl)
            val fullPath = buildPath(serverPath, remotePath)
            val url = URL(fullPath)
            val connection = url.openConnection() as HttpURLConnection
            addPreemptiveAuthHeader(connection, config)
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            val statusCode = connection.responseCode
            when {
                statusCode in 200..299 -> Result.success(response.toString())
                statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
                statusCode == HttpURLConnection.HTTP_FORBIDDEN -> Result.failure(Exception("访问被拒绝，请检查账户权限"))
                statusCode == HttpURLConnection.HTTP_NOT_FOUND -> Result.failure(Exception("文件不存在"))
                statusCode >= 500 -> Result.failure(Exception("服务器错误，请稍后重试"))
                else -> Result.failure(Exception("下载失败，状态码: $statusCode"))
            }
        } catch (e: java.net.UnknownHostException) {
            e.printStackTrace()
            Result.failure(Exception("无法连接到服务器，请检查服务器地址是否正确"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("网络连接失败，请检查网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("连接超时，请检查网络连接或稍后重试"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    suspend fun deleteFile(
        config: WebDAVConfig,
        remotePath: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val serverPath = normalizeServerUrl(config.serverUrl)
            val fullPath = buildPath(serverPath, remotePath)
            val url = URL(fullPath)
            val connection = url.openConnection() as HttpURLConnection
            addPreemptiveAuthHeader(connection, config)
            connection.requestMethod = "DELETE"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val statusCode = connection.responseCode
            when {
                statusCode in 200..299 -> Result.success(true)
                statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
                statusCode == HttpURLConnection.HTTP_FORBIDDEN -> Result.failure(Exception("访问被拒绝，请检查账户权限"))
                statusCode == HttpURLConnection.HTTP_NOT_FOUND -> Result.failure(Exception("文件不存在"))
                statusCode >= 500 -> Result.failure(Exception("服务器错误，请稍后重试"))
                else -> Result.failure(Exception("删除失败，状态码: $statusCode"))
            }
        } catch (e: java.net.UnknownHostException) {
            e.printStackTrace()
            Result.failure(Exception("无法连接到服务器，请检查服务器地址是否正确"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("网络连接失败，请检查网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("连接超时，请检查网络连接或稍后重试"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("删除失败: ${e.message}"))
        }
    }

    suspend fun listFiles(
        config: WebDAVConfig,
        remotePath: String = "大喜记账/"
    ): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val serverPath = normalizeServerUrl(config.serverUrl)
            val fullPath = buildPath(serverPath, remotePath)
            val url = URL(fullPath)
            val connection = url.openConnection() as HttpURLConnection
            addPreemptiveAuthHeader(connection, config)
            addWebDAVHeaders(connection)
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            val statusCode = connection.responseCode
            when {
                statusCode in 200..299 -> {
                    val files = parseWebDAVResponse(response.toString(), remotePath)
                    Result.success(files.sortedByDescending { it.modifiedTime })
                }
                statusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
                statusCode == HttpURLConnection.HTTP_FORBIDDEN -> Result.failure(Exception("访问被拒绝，请检查账户权限"))
                statusCode == HttpURLConnection.HTTP_NOT_FOUND -> Result.success(emptyList())
                statusCode >= 500 -> Result.failure(Exception("服务器错误，请稍后重试"))
                else -> Result.failure(Exception("获取文件列表失败，状态码: $statusCode"))
            }
        } catch (e: java.net.UnknownHostException) {
            e.printStackTrace()
            Result.failure(Exception("无法连接到服务器，请检查服务器地址是否正确"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("网络连接失败，请检查网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("连接超时，请检查网络连接或稍后重试"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("获取文件列表失败: ${e.message}"))
        }
    }

    private fun parseWebDAVResponse(content: String, basePath: String): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        try {
            val json = JSONObject(content)
            val filesArray = json.optJSONArray("files") ?: JSONArray()
            
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                val name = fileObj.optString("name", "")
                if (!name.endsWith("/")) {
                    files.add(
                        RemoteFile(
                            name = name,
                            path = "$basePath$name",
                            size = fileObj.optLong("size", 0),
                            modifiedTime = parseDate(fileObj.optString("modified", ""))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    private fun parseDate(dateString: String): Date {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(dateString) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    suspend fun ensureDirectoryExists(
        config: WebDAVConfig,
        remotePath: String = "大喜记账/"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val serverPath = normalizeServerUrl(config.serverUrl)
            val fullPath = buildPath(serverPath, remotePath)
            val url = URL(fullPath)
            val connection = url.openConnection() as HttpURLConnection
            addPreemptiveAuthHeader(connection, config)
            connection.requestMethod = "OPTIONS"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val statusCode = connection.responseCode
            
            if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                val putConnection = url.openConnection() as HttpURLConnection
                addPreemptiveAuthHeader(putConnection, config)
                putConnection.requestMethod = "PUT"
                putConnection.doOutput = true
                putConnection.setRequestProperty("Content-Type", "text/plain")
                putConnection.connectTimeout = 10000
                putConnection.readTimeout = 10000

                val writer = OutputStreamWriter(putConnection.outputStream)
                writer.write("")
                writer.flush()
                writer.close()

                val putStatusCode = putConnection.responseCode
                when {
                    putStatusCode in 200..299 -> Result.success(true)
                    putStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED -> Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
                    putStatusCode == HttpURLConnection.HTTP_FORBIDDEN -> Result.failure(Exception("访问被拒绝，请检查账户权限"))
                    putStatusCode >= 500 -> Result.failure(Exception("服务器错误，请稍后重试"))
                    else -> Result.failure(Exception("创建目录失败，状态码: $putStatusCode"))
                }
            } else if (statusCode in 200..299) {
                Result.success(true)
            } else if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Result.failure(Exception("认证失败，请检查用户名和密码是否正确"))
            } else if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                Result.failure(Exception("访问被拒绝，请检查账户权限"))
            } else if (statusCode >= 500) {
                Result.failure(Exception("服务器错误，请稍后重试"))
            } else {
                Result.failure(Exception("检查目录失败，状态码: $statusCode"))
            }
        } catch (e: java.net.UnknownHostException) {
            e.printStackTrace()
            Result.failure(Exception("无法连接到服务器，请检查服务器地址是否正确"))
        } catch (e: java.net.ConnectException) {
            e.printStackTrace()
            Result.failure(Exception("网络连接失败，请检查网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            e.printStackTrace()
            Result.failure(Exception("连接超时，请检查网络连接或稍后重试"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("检查目录失败: ${e.message}"))
        }
    }

    fun generateManualPushFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "大喜记账_手动_$timestamp.json"
    }

    fun generateAutoPushFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "大喜记账_自动_$timestamp.json"
    }

    fun isAutoPushFile(fileName: String): Boolean {
        return fileName.startsWith("大喜记账_自动_")
    }

    fun isManualPushFile(fileName: String): Boolean {
        return fileName.startsWith("大喜记账_手动_")
    }
}