package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.data.ActionType
import com.example.data.AutomationRepository
import com.example.data.AutomationRule
import com.example.data.ExecutionLog
import com.example.permission.PermissionManager
import com.example.service.AutoFlowAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskExecutionManager(
    private val context: Context,
    private val repository: AutomationRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val permissionManager = PermissionManager(context)

    fun checkAndPromptPermissionsForExecution(): Boolean {
        if (!permissionManager.areNotificationsEnabled()) {
            Toast.makeText(context, "缺少通知权限，请在设置中开启通知以展示提示与响铃", Toast.LENGTH_LONG).show()
            permissionManager.checkAllPermissions().firstOrNull { it.id == "NOTIFICATION" }?.let {
                permissionManager.openPermissionSettings(it)
            }
            return false
        }

        if (!permissionManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "缺少精确闹钟权限，请在设置中允许设置闹钟", Toast.LENGTH_LONG).show()
            permissionManager.checkAllPermissions().firstOrNull { it.id == "EXACT_ALARM" }?.let {
                permissionManager.openPermissionSettings(it)
            }
            return false
        }

        return true
    }

    /**
     * 执行规则任务：静默执行，不弹出任何多余提示打扰用户
     */
    fun executeRule(rule: AutomationRule, triggerContextInfo: String = "") {
        coroutineScope.launch {
            var isSuccess = false
            var message = ""

            try {
                when (rule.actionType) {
                    ActionType.SET_ALARM -> {
                        ActiveCountdownManager.handleAppOpened(context, rule)
                        isSuccess = true
                        message = "倒计时已启动"
                    }

                    ActionType.LAUNCH_APP -> {
                        val packageName = rule.actionTargetPackage.ifEmpty { "com.android.settings" }
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            isSuccess = true
                            message = "已打开应用: ${rule.actionTargetAppLabel.ifEmpty { packageName }}"
                        } else {
                            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(settingsIntent)
                            isSuccess = true
                            message = "目标应用未找到，已为您打开系统设置"
                        }
                    }

                    ActionType.ADJUST_VOLUME -> {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVolume = (maxVolume * (rule.actionValue.coerceIn(0, 100) / 100f)).toInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
                        isSuccess = true
                        message = "媒体音量调整至 ${rule.actionValue}%"
                    }

                    ActionType.ADJUST_BRIGHTNESS -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
                            val brightnessValue = ((rule.actionValue.coerceIn(0, 100) / 100f) * 255).toInt()
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                            )
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                brightnessValue
                            )
                            isSuccess = true
                            message = "屏幕亮度已调整至 ${rule.actionValue}%"
                        } else {
                            isSuccess = false
                            message = "缺少修改系统设置权限"
                            withContext(Dispatchers.Main) {
                                permissionManager.checkAllPermissions().firstOrNull { it.id == "WRITE_SETTINGS" }?.let {
                                    permissionManager.openPermissionSettings(it)
                                }
                            }
                        }
                    }

                    ActionType.SEND_NOTIFICATION -> {
                        sendCustomNotification("任务触发: ${rule.title}", rule.description)
                        isSuccess = true
                        message = "已成功发送通知"
                    }

                    ActionType.PERFORM_HOME_GESTURE -> {
                        val service = AutoFlowAccessibilityService.instance
                        if (service != null) {
                            service.performHomeAction()
                            isSuccess = true
                            message = "已自动执行返回桌面"
                        } else {
                            isSuccess = false
                            message = "无障碍服务未开启"
                            withContext(Dispatchers.Main) {
                                permissionManager.checkAllPermissions().firstOrNull { it.id == "ACCESSIBILITY" }?.let {
                                    permissionManager.openPermissionSettings(it)
                                }
                            }
                        }
                    }
                }

                // 静默执行：不弹出任何Toast提示

            } catch (e: Exception) {
                isSuccess = false
                message = "执行异常: ${e.localizedMessage ?: "未知错误"}"
            }

            val updatedRule = rule.copy(
                lastExecutedMs = System.currentTimeMillis(),
                executionCount = rule.executionCount + 1
            )
            repository.updateRule(updatedRule)

            val log = ExecutionLog(
                ruleId = rule.id,
                ruleTitle = rule.title,
                triggerType = rule.triggerType,
                actionType = rule.actionType,
                status = if (isSuccess) "成功" else "失败",
                detailMessage = if (triggerContextInfo.isNotEmpty()) "$message ($triggerContextInfo)" else message
            )
            repository.insertLog(log)
        }
    }

    /**
     * 当满足退出条件时，直接中断并彻底销毁该规则关联的任务，停止所有计时与后续动作（静默退出）
     */
    fun destroyOrInterruptRule(rule: AutomationRule, exitReason: String) {
        coroutineScope.launch {
            if (ActiveCountdownManager.countdownState.value.ruleId == rule.id || rule.actionType == ActionType.SET_ALARM) {
                // 如果倒计时正在运行且处于10秒宽限期内，由10秒返回恢复逻辑全权管理，不提前粗暴销毁
                if (ActiveCountdownManager.countdownState.value.isInExitGracePeriod) {
                    return@launch
                }
                ActiveCountdownManager.stopCountdown(context)
            }

            // 静默退出：不弹出任何Toast提示

            val log = ExecutionLog(
                ruleId = rule.id,
                ruleTitle = rule.title,
                triggerType = rule.triggerType,
                actionType = rule.actionType,
                status = "已销毁",
                detailMessage = "满足退出条件（$exitReason），任务已自动中断并销毁，停止计时与执行"
            )
            repository.insertLog(log)
        }
    }

    private fun sendCustomNotification(title: String, text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "autoflow_custom_task_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "自动化任务通知",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
