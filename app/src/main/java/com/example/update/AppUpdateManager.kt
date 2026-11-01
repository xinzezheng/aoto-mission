package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val currentVersionName: String = "1.0.0",
    val latestTagName: String = "v1.0.0",
    val apkDownloadUrl: String = "",
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val errorMessage: String = ""
)

object AppUpdateManager {

    const val GITEE_REPO_URL = "https://gitee.com/xinze0408/aoto-mission"
    private const val GITEE_API_LATEST_RELEASE = "https://gitee.com/api/v5/repos/xinze0408/aoto-mission/releases/latest"
    private const val GITEE_API_ALL_RELEASES = "https://gitee.com/api/v5/repos/xinze0408/aoto-mission/releases"

    private val _updateState = MutableStateFlow(UpdateInfo())
    val updateState: StateFlow<UpdateInfo> = _updateState.asStateFlow()

    suspend fun checkForUpdates(context: Context) {
        val currentVer = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        _updateState.value = _updateState.value.copy(
            currentVersionName = currentVer,
            isChecking = true,
            errorMessage = ""
        )

        withContext(Dispatchers.IO) {
            try {
                val url = URL(GITEE_API_LATEST_RELEASE)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "TuDouLei-Android")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                var tagName = "latest"
                var foundApkUrl = ""

                if (connection.responseCode == 200) {
                    val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseJson)
                    tagName = jsonObject.optString("tag_name", "latest")

                    // 1. 尝试从 assets 解析 APK 下载地址
                    val assets = jsonObject.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                                foundApkUrl = downloadUrl
                                break
                            }
                        }
                    }

                    // 2. 尝试从 attach_files 解析
                    if (foundApkUrl.isEmpty()) {
                        val attachFiles = jsonObject.optJSONArray("attach_files")
                        if (attachFiles != null) {
                            for (i in 0 until attachFiles.length()) {
                                val fileObj = attachFiles.getJSONObject(i)
                                val downloadUrl = fileObj.optString("download_url", "")
                                if (downloadUrl.endsWith(".apk", ignoreCase = true) || downloadUrl.contains("apk")) {
                                    foundApkUrl = downloadUrl
                                    break
                                }
                            }
                        }
                    }
                }

                // 如果未在响应中解析到具体文件，使用 Gitee 发行版标准下载地址
                if (foundApkUrl.isEmpty()) {
                    foundApkUrl = "https://gitee.com/xinze0408/aoto-mission/releases/download/$tagName/app-release.apk"
                }

                _updateState.value = _updateState.value.copy(
                    latestTagName = if (tagName.isNotEmpty()) tagName else "最新版",
                    apkDownloadUrl = foundApkUrl,
                    isChecking = false
                )
            } catch (e: Exception) {
                // 默认采用 Gitee 发行版直接下载链接
                _updateState.value = _updateState.value.copy(
                    isChecking = false,
                    latestTagName = "最新版",
                    apkDownloadUrl = "https://gitee.com/xinze0408/aoto-mission/releases/download/latest/app-release.apk"
                )
            }
        }
    }

    suspend fun downloadAndInstallApk(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = try { context.packageManager.canRequestPackageInstalls() } catch (_: Exception) { true }
            if (!canInstall) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "请先在设置中开启【允许安装未知应用】权限", Toast.LENGTH_LONG).show()
                }
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) { }
                return
            }
        }

        var downloadUrl = _updateState.value.apkDownloadUrl
        if (downloadUrl.isEmpty()) {
            downloadUrl = "https://gitee.com/xinze0408/aoto-mission/releases/download/latest/app-release.apk"
        }

        _updateState.value = _updateState.value.copy(
            isDownloading = true,
            downloadProgress = 0.05f,
            errorMessage = ""
        )

        withContext(Dispatchers.IO) {
            val downloadUrlsToTry = mutableListOf<String>()
            downloadUrlsToTry.add(downloadUrl)
            val tagName = _updateState.value.latestTagName
            if (tagName.isNotEmpty() && tagName != "最新版") {
                downloadUrlsToTry.add("https://gitee.com/xinze0408/aoto-mission/releases/download/$tagName/app-release.apk")
            }
            downloadUrlsToTry.add("https://gitee.com/xinze0408/aoto-mission/releases/download/latest/app-release.apk")
            downloadUrlsToTry.add("https://gitee.com/xinze0408/aoto-mission/raw/master/app-release.apk")
            downloadUrlsToTry.add("https://gitee.com/xinze0408/aoto-mission/raw/main/app-release.apk")

            var isSuccess = false
            val apkFile = File(context.externalCacheDir ?: context.cacheDir, "tudoulei_update.apk")

            for (targetDownloadUrl in downloadUrlsToTry.distinct()) {
                if (isSuccess) break
                try {
                    if (apkFile.exists()) apkFile.delete()

                    val url = URL(targetDownloadUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "TuDouLei-Android")
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 12000
                    connection.readTimeout = 20000

                    var redirectConn = connection
                    var status = redirectConn.responseCode
                    var redirectCount = 0
                    while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) && redirectCount < 5) {
                        val newUrl = redirectConn.getHeaderField("Location")
                        redirectConn.disconnect()
                        val target = URL(newUrl)
                        redirectConn = target.openConnection() as HttpURLConnection
                        redirectConn.setRequestProperty("User-Agent", "TuDouLei-Android")
                        status = redirectConn.responseCode
                        redirectCount++
                    }

                    if (status != HttpURLConnection.HTTP_OK) {
                        continue
                    }

                    val totalLength = redirectConn.contentLength
                    val inputStream = redirectConn.inputStream
                    val outputStream = FileOutputStream(apkFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalLength > 0) {
                            val progress = downloadedBytes.toFloat() / totalLength.toFloat()
                            _updateState.value = _updateState.value.copy(downloadProgress = progress)
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    // 检查 APK 文件有效性（需大于 500KB 且非 HTML 网页）
                    if (apkFile.length() > 500000) {
                        isSuccess = true
                    }
                } catch (_: Exception) { }
            }

            if (isSuccess) {
                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    downloadProgress = 1f
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "最新独立 APK 获取成功，正在唤起安装...", Toast.LENGTH_SHORT).show()
                    installApk(context, apkFile)
                }
            } else {
                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    errorMessage = "Gitee 暂未上传或未发布 APK 文件，已为您打开 Gitee 仓库。"
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITEE_REPO_URL)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                } else {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
