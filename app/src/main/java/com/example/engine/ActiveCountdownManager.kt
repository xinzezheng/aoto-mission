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
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isRinging: Boolean = false
)

object ActiveCountdownManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null
    private var gracePeriodJob: Job? = null
    private var activeRingtone: Ringtone? = null

    private var currentActiveRuleId: Long? = null
    private var isTargetAppInForeground: Boolean = false

    private val _countdownState = MutableStateFlow(CountdownState())
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    fun handleAppOpened(context: Context, rule: AutomationRule) {
        gracePeriodJob?.cancel()
        gracePeriodJob = null

        isTargetAppInForeground = true

        val currentState = _countdownState.value
        if (currentState.isRunning && currentActiveRuleId == rule.id && currentState.remainingSeconds > 0) {
            // Re-entered target app within grace period -> keep existing countdown progress!
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "⚡ 5分钟后放下手机", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Fresh countdown start or rule changed
        currentActiveRuleId = rule.id
        startCountdown(context, rule, rule.actionValue.coerceAtLeast(1))

        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, "⚡ 5分钟后放下手机", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleAppExited(context: Context) {
        if (!isTargetAppInForeground) return
        isTargetAppInForeground = false

        // 10-second grace period timer
        gracePeriodJob?.cancel()
        gracePeriodJob = scope.launch {
            delay(10000L) // Wait 10 seconds
            if (!isTargetAppInForeground) {
                // User really exited for more than 10 seconds -> stop countdown
                withContext(Dispatchers.Main) {
                    stopCountdown(context)
                }
            }
        }
    }

    fun startCountdown(context: Context, rule: AutomationRule, overrideMinutes: Int? = null) {
        val durationMinutes = overrideMinutes ?: rule.actionValue.coerceAtLeast(1)
        val totalSec = durationMinutes * 60

        stopCountdownInternal(context)

        currentActiveRuleId = rule.id
        _countdownState.value = CountdownState(
            ruleId = rule.id,
            ruleTitle = rule.title,
            totalSeconds = totalSec,
            remainingSeconds = totalSec,
            isRunning = true,
            isRinging = false
        )

        showCountdownNotification(context, totalSec)

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
        gracePeriodJob?.cancel()
        gracePeriodJob = null
        currentActiveRuleId = null
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
