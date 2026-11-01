package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.data.AutomationRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CountdownState(
    val ruleId: Long = 0,
    val ruleTitle: String = "",
    val targetPackages: List<String> = emptyList(),
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isRinging: Boolean = false,
    val isInExitGracePeriod: Boolean = false,
    val exitGraceRemainingSeconds: Int = 10
)

object ActiveCountdownManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null
    private var exitGraceJob: Job? = null
    private var activeRingtone: Ringtone? = null

    private var currentActiveRuleId: Long? = null
    private var currentActiveRule: AutomationRule? = null

    private val _countdownState = MutableStateFlow(CountdownState())
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    fun handleAppOpened(context: Context, rule: AutomationRule) {
        val currentState = _countdownState.value
        if (currentState.isRunning && (currentState.ruleId == rule.id || rule.id == 0L)) {
            // 如果处于退出 10 秒等待期，用户已快速返回应用，直接取消退出计时并继续执行
            if (currentState.isInExitGracePeriod) {
                onTargetAppReturned(context)
            }
            return
        }

        currentActiveRuleId = rule.id
        currentActiveRule = rule

        val targetPkgs = rule.triggerPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        startCountdown(context, rule, rule.actionValue.coerceAtLeast(1), targetPkgs)
    }

    fun handleAppExited(context: Context, reason: String = "用户已退出应用") {
        stopCountdown(context)
    }

    /**
     * 当中途离开目标应用时：
     * 响应急速无延迟，提示用户在10秒内回到APP（纯文字，无任何特殊字符或表情），
     * 10秒内未返回则自动彻底销毁并停止该任务。
     */
    fun onTargetAppExited(context: Context) {
        val currentState = _countdownState.value
        if (!currentState.isRunning || currentState.isInExitGracePeriod || currentState.isRinging) {
            return
        }

        _countdownState.value = currentState.copy(
            isInExitGracePeriod = true,
            exitGraceRemainingSeconds = 10
        )

        // 提示用户在10秒内回到APP（不要添加任何字符或表情）
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, "请在10秒内回到APP", Toast.LENGTH_SHORT).show()
        }
        showExitReminderNotification(context, 10)

        exitGraceJob?.cancel()
        exitGraceJob = scope.launch {
            var graceRemaining = 10
            while (graceRemaining > 0 && _countdownState.value.isInExitGracePeriod && _countdownState.value.isRunning) {
                delay(1000L)
                graceRemaining--
                _countdownState.value = _countdownState.value.copy(exitGraceRemainingSeconds = graceRemaining)
                if (_countdownState.value.isInExitGracePeriod) {
                    showExitReminderNotification(context, graceRemaining)
                }
            }

            // 10秒倒计时结束且用户仍未返回应用：静默销毁并彻底停止任务
            if (_countdownState.value.isInExitGracePeriod && _countdownState.value.isRunning && graceRemaining <= 0) {
                stopCountdown(context)
            }
        }
    }

    /**
     * 用户在10秒内成功返回目标应用：取消退出计时，无缝恢复原任务
     */
    fun onTargetAppReturned(context: Context) {
        val currentState = _countdownState.value
        if (!currentState.isInExitGracePeriod) return

        exitGraceJob?.cancel()
        exitGraceJob = null

        _countdownState.value = currentState.copy(
            isInExitGracePeriod = false,
            exitGraceRemainingSeconds = 10
        )

        showCountdownNotification(context, currentState.remainingSeconds)
    }

    /**
     * 启动倒计时任务：静默执行，无任何多余弹窗提醒
     */
    fun startCountdown(
        context: Context,
        rule: AutomationRule,
        overrideMinutes: Int? = null,
        targetPackages: List<String> = emptyList()
    ) {
        val durationMinutes = overrideMinutes ?: rule.actionValue.coerceAtLeast(1)
        val totalSec = durationMinutes * 60

        stopCountdownInternal(context)

        val packages = if (targetPackages.isNotEmpty()) {
            targetPackages
        } else {
            rule.triggerPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        currentActiveRuleId = rule.id
        currentActiveRule = rule
        _countdownState.value = CountdownState(
            ruleId = rule.id,
            ruleTitle = rule.title,
            targetPackages = packages,
            totalSeconds = totalSec,
            remainingSeconds = totalSec,
            isRunning = true,
            isRinging = false,
            isInExitGracePeriod = false,
            exitGraceRemainingSeconds = 10
        )

        showCountdownNotification(context, totalSec)

        timerJob = scope.launch {
            var currentRemaining = totalSec
            while (currentRemaining > 0 && _countdownState.value.isRunning) {
                delay(1000L)
                // 如果处于退出等待期，总计时暂停或继续递减
                currentRemaining--
                _countdownState.value = _countdownState.value.copy(remainingSeconds = currentRemaining)
                if (!_countdownState.value.isInExitGracePeriod) {
                    showCountdownNotification(context, currentRemaining)
                }
            }

            if (_countdownState.value.isRunning && currentRemaining <= 0) {
                triggerAlarmRinging(context)
            }
        }
    }

    private fun triggerAlarmRinging(context: Context) {
        _countdownState.value = _countdownState.value.copy(
            isRunning = false,
            isRinging = true,
            isInExitGracePeriod = false
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "autoflow_alarm_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "倒计时响铃提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("5分钟时间到，请放下手机")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        notificationManager.notify(9999, notification)

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
            activeRingtone = ringtone
        } catch (_: Exception) { }
    }

    /**
     * 停止或销毁倒计时任务：静默退出，无弹窗打扰
     */
    fun stopCountdown(context: Context) {
        currentActiveRuleId = null
        currentActiveRule = null
        stopCountdownInternal(context)
    }

    private fun stopCountdownInternal(context: Context) {
        timerJob?.cancel()
        timerJob = null
        exitGraceJob?.cancel()
        exitGraceJob = null

        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) { }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(9999)
        notificationManager.cancel(8888)

        _countdownState.value = CountdownState()
    }

    private fun showExitReminderNotification(context: Context, remainingSeconds: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "autoflow_active_timer_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "实时倒计时",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("请在10秒内回到APP")
            .setContentText("离开目标应用中，剩余 ${remainingSeconds} 秒自动停止")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(8888, notification)
    }

    private fun showCountdownNotification(context: Context, remainingSec: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "autoflow_active_timer_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "实时倒计时",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val minutes = remainingSec / 60
        val seconds = remainingSec % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("倒计时运行中: $timeString")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(8888, notification)
    }
}
