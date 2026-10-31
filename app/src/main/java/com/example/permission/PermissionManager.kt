package com.example.permission

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.example.service.AutoFlowAccessibilityService

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isEssential: Boolean = false,
    val intentAction: String,
    val intentData: Uri? = null
)

class PermissionManager(private val context: Context) {

    fun checkAllPermissions(): List<PermissionItem> {
        return listOf(
            getAccessibilityPermissionItem(),
            getOverlayPermissionItem(),
            getWriteSettingsPermissionItem(),
            getUsageAccessPermissionItem(),
            getExactAlarmPermissionItem(),
            getNotificationPermissionItem()
        )
    }

    fun getNextUngrantedPermission(): PermissionItem? {
        return checkAllPermissions().firstOrNull { !it.isGranted }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "${context.packageName}/${AutoFlowAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedService, ignoreCase = true) ||
                componentName.contains(context.packageName)
            ) {
                return true
            }
        }
        return false
    }

    private fun getAccessibilityPermissionItem(): PermissionItem {
        val granted = isAccessibilityServiceEnabled()
        return PermissionItem(
            id = "ACCESSIBILITY",
            title = "无障碍服务",
            description = "用于实时监测抖音、TikTok、小红书、B站等应用启动，并自动执行模拟按键与定时任务",
            isGranted = granted,
            isEssential = true,
            intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
        )
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun getOverlayPermissionItem(): PermissionItem {
        val granted = canDrawOverlays()
        val intentData = Uri.parse("package:${context.packageName}")
        return PermissionItem(
            id = "OVERLAY",
            title = "悬浮窗与显示在其他应用上",
            description = "用于在自动倒计时和响铃时弹窗提示或显示快捷控制面板",
            isGranted = granted,
            isEssential = true,
            intentAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            intentData = intentData
        )
    }

    fun canWriteSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    private fun getWriteSettingsPermissionItem(): PermissionItem {
        val granted = canWriteSettings()
        val intentData = Uri.parse("package:${context.packageName}")
        return PermissionItem(
            id = "WRITE_SETTINGS",
            title = "修改系统设置",
            description = "用于自动调节屏幕亮度、音量及系统配置",
            isGranted = granted,
            isEssential = false,
            intentAction = Settings.ACTION_MANAGE_WRITE_SETTINGS,
            intentData = intentData
        )
    }

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun getUsageAccessPermissionItem(): PermissionItem {
        val granted = hasUsageAccess()
        return PermissionItem(
            id = "USAGE_ACCESS",
            title = "应用使用情况访问",
            description = "用于精确识别前台运行的应用并触发关联的自动化流程",
            isGranted = granted,
            isEssential = false,
            intentAction = Settings.ACTION_USAGE_ACCESS_SETTINGS
        )
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun getExactAlarmPermissionItem(): PermissionItem {
        val granted = canScheduleExactAlarms()
        val intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        val intentData = Uri.parse("package:${context.packageName}")
        return PermissionItem(
            id = "EXACT_ALARM",
            title = "精确闹钟与定时触发",
            description = "保证 5 分钟倒计时闹钟在指定时刻毫秒级精准响铃",
            isGranted = granted,
            isEssential = true,
            intentAction = intentAction,
            intentData = intentData
        )
    }

    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun getNotificationPermissionItem(): PermissionItem {
        val granted = areNotificationsEnabled()
        val intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return PermissionItem(
            id = "NOTIFICATION",
            title = "通知栏权限",
            description = "用于在倒计时结束时推送响铃通知与状态提醒",
            isGranted = granted,
            isEssential = false,
            intentAction = intentAction
        )
    }

    fun openPermissionSettings(item: PermissionItem) {
        try {
            val intent = Intent(item.intentAction).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                item.intentData?.let { data = it }
                if (item.intentAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) { }
        }
    }
}
