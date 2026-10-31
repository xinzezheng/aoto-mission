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

    const val GITHUB_REPO_URL = "https://github.com/xinzezheng/Aotomission"
    private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/xinzezheng/Aotomission/releases/latest"
    private const val GITHUB_DIRECT_APK_FALLBACK = "https://github.com/xinzezheng/Aotomission/releases/latest/download/app-release.apk"

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
                val url = URL(GITHUB_API_LATEST_RELEASE)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "AutoFlow-Android")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseJson)
                    val tagName = jsonObject.optString("tag_name", "v1.0.0")

                    var apkUrl = ""
                    val assetsArray = jsonObject.optJSONArray("assets")
                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (apkUrl.isEmpty()) {
                        apkUrl = GITHUB_DIRECT_APK_FALLBACK
                    }

                    _updateState.value = _updateState.value.copy(
                        latestTagName = tagName,
                        apkDownloadUrl = apkUrl,
                        isChecking = false
                    )
                } else {
                    _updateState.value = _updateState.value.copy(
                        latestTagName = "最新版",
                        apkDownloadUrl = GITHUB_DIRECT_APK_FALLBACK,
                        isChecking = false
                    )
                }
            } catch (_: Exception) {
                _updateState.value = _updateState.value.copy(
                    isChecking = false,
                    latestTagName = "最新版",
                    apkDownloadUrl = GITHUB_DIRECT_APK_FALLBACK
                )
            }
        }
    }

    suspend fun downloadAndInstallApk(context: Context) {
        // Request未知应用安装权限 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "请先在设置中开启【允许安装未知应用】权限", Toast.LENGTH_LONG).show()
                }
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        var downloadUrl = _updateState.value.apkDownloadUrl
        if (downloadUrl.isEmpty()) {
            downloadUrl = GITHUB_DIRECT_APK_FALLBACK
        }

        _updateState.value = _updateState.value.copy(
            isDownloading = true,
            downloadProgress = 0.05f,
            errorMessage = ""
        )

        withContext(Dispatchers.IO) {
            try {
                val apkFile = File(context.externalCacheDir ?: context.cacheDir, "Aotomission_update.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "AutoFlow-Android")
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                // Handle HTTP redirects (GitHub Releases redirect to S3 storage)
                var redirectConnection = connection
                var status = redirectConnection.responseCode
                var redirectCount = 0
                while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) && redirectCount < 5) {
                    val newUrl = redirectConnection.getHeaderField("Location")
                    redirectConnection.disconnect()
                    val targetUrl = URL(newUrl)
                    redirectConnection = targetUrl.openConnection() as HttpURLConnection
                    redirectConnection.setRequestProperty("User-Agent", "AutoFlow-Android")
                    status = redirectConnection.responseCode
                    redirectCount++
                }

                val totalLength = redirectConnection.contentLength
                val inputStream = redirectConnection.inputStream
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

                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    downloadProgress = 1f
                )

                // Immediately launch package installer for zero-click install
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载完成，正在打开系统安装程序...", Toast.LENGTH_SHORT).show()
                    installApk(context, apkFile)
                }

            } catch (e: Exception) {
                // If direct link fails, open browser release page fallback
                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    errorMessage = "后台下载异常，已准备直接打开下载页面"
                )
                withContext(Dispatchers.Main) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
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
