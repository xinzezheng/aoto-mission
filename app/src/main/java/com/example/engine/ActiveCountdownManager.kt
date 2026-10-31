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
import kotlinx.coroutines.withContext

data class CountdownState(
    val ruleId: Long = 0,
    val ruleTitle: String = "",
    val targetPackages: List<String> = emptyList(),
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isRinging: Boolean = false
)

object ActiveCountdownManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null
    private var activeRingtone: Ringtone? = null

    private var currentActiveRuleId: Long? = null
    private var currentActiveRule: AutomationRule? = null
    private var isTargetAppInForeground: Boolean = false

    private val _countdownState = MutableStateFlow(CountdownState())
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    fun handleAppOpened(context: Context, rule: AutomationRule) {
        isTargetAppInForeground = true
        currentActiveRuleId = rule.id
        currentActiveRule = rule

        val targetPkgs = rule.triggerPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        startCountdown(context, rule, rule.actionValue.coerceAtLeast(1), targetPkgs)
    }

    /**
     * 当检测到用户退出应用（满足退出条件）时：
     * 立即中断并完全销毁该任务，彻底停止计时与后续执行，不进行任何等待或后台继续计时。
     */
    fun handleAppExited(context: Context, reason: String = "用户已退出应用") {
        val currentState = _countdownState.value
        if (!currentState.isRunning && !currentState.isRinging) {
            return
        }

        // 立即完全销毁并停止任务
        stopCountdown(context)

        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "🛑 检测到退出应用，任务已自动销毁停止", Toast.LENGTH_SHORT).show()
        }
    }

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
            isRinging = false
        )

        showCountdownNotification(context, totalSec)

        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "⚡ 5分钟后放下手机", Toast.LENGTH_SHORT).show()
        }

        timerJob = scope.launch {
            var currentRemaining = totalSec
            while (currentRemaining > 0 && _countdownState.value.isRunning) {
                delay(1000L)
                currentRemaining--
                _countdownState.value = _countdownState.value.copy(remainingSeconds = currentRemaining)
                showCountdownNotification(context, currentRemaining)
            }

            if (_countdownState.value.isRunning && currentRemaining <= 0) {
                triggerAlarmRinging(context)
            }
        }
    }

    private fun triggerAlarmRinging(context: Context) {
        _countdownState.value = _countdownState.value.copy(
            isRunning = false,
            isRinging = true
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "autoflow_alarm_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "倒计时响铃提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 5分钟时间到，请放下手机！")
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

        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "⏰ 5分钟时间到，请放下手机！", Toast.LENGTH_LONG).show()
        }
    }

    fun stopCountdown(context: Context) {
        currentActiveRuleId = null
        currentActiveRule = null
        isTargetAppInForeground = false
        stopCountdownInternal(context)
    }

    private fun stopCountdownInternal(context: Context) {
        timerJob?.cancel()
        timerJob = null

        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) { }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(9999)
        notificationManager.cancel(8888)

        _countdownState.value = CountdownState()
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
            .setContentTitle("⏳ 倒计时运行中: $timeString")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(8888, notification)
    }
}
