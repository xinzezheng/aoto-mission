package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.data.AutomationRule
import com.example.receiver.AlarmReceiver
import com.example.service.AutoFlowAccessibilityService
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

    // 10秒防重进强制退出限制机制
    @Volatile
    private var lockdownUntilMs: Long = 0L
    @Volatile
    private var lockedPackages: List<String> = emptyList()

    fun startLockdown(packages: List<String>, durationMs: Long = 10_000L) {
        lockdownUntilMs = System.currentTimeMillis() + durationMs
        lockedPackages = packages
        Log.d("ActiveCountdown", "已开启10秒防重进锁定限制: $packages, 持续至 $lockdownUntilMs")
    }

    fun isPackageLocked(packageName: String): Boolean {
        if (packageName.isEmpty() || System.currentTimeMillis() > lockdownUntilMs) {
            return false
        }
        return lockedPackages.any { target ->
            target.isNotEmpty() && (packageName.equals(target, ignoreCase = true) || packageName.contains(target, ignoreCase = true))
        }
    }

    fun getLockdownRemainingSeconds(): Int {
        val diff = lockdownUntilMs - System.currentTimeMillis()
        return if (diff > 0) ((diff + 999) / 1000).toInt() else 0
    }

    fun handleAppOpened(context: Context, rule: AutomationRule) {
        val currentState = _countdownState.value
        if (currentState.isRunning) {
            // 如果已经在计时中（无论是否在退出等待期），用户打开应用时直接算作返回，取消退出等待期并继续原有倒计时进度！
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
     * 提示用户在10秒内回到APP，10秒内未返回则自动彻底销毁并停止该任务。
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
                // 退出等待期间不扣减倒计时总进度，只扣减10秒缓冲期
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
     * 用户在10秒内成功返回目标应用：取消退出等待，完美无缝继续之前的倒计时进度！
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
     * 启动倒计时任务：保留原有进度或从头开始
     */
    fun startCountdown(
        context: Context,
        rule: AutomationRule,
        overrideMinutes: Int? = null,
        targetPackages: List<String> = emptyList()
    ) {
        val durationMinutes = overrideMinutes ?: rule.actionValue.coerceAtLeast(1)
        val totalSec = durationMinutes * 60

        // 如果当前已经有任务在运行，不重复初始化
        if (_countdownState.value.isRunning && _countdownState.value.ruleId == rule.id) {
            return
        }

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
            while (_countdownState.value.isRunning) {
                delay(1000L)
                val state = _countdownState.value
                if (!state.isRunning) break

                // 只有当不在10秒退出缓冲期时，倒计时秒数才正常向下递减
                if (!state.isInExitGracePeriod && state.remainingSeconds > 0) {
                    val nextRemaining = state.remainingSeconds - 1
                    _countdownState.value = state.copy(remainingSeconds = nextRemaining)
                    showCountdownNotification(context, nextRemaining)

                    if (nextRemaining <= 0) {
                        triggerAlarmRinging(context)
                        break
                    }
                }
            }
        }
    }

    private fun triggerAlarmRinging(context: Context) {
        val currentPkgs = _countdownState.value.targetPackages

        _countdownState.value = _countdownState.value.copy(
            isRunning = false,
            isRinging = true,
            isInExitGracePeriod = false
        )

        // 1. 默认将音量调到最大 (ALARM, MUSIC, RING)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, AudioManager.FLAG_SHOW_UI)

                val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)

                val maxRing = am.getStreamMaxVolume(AudioManager.STREAM_RING)
                am.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)
            }
        } catch (e: Exception) {
            Log.e("ActiveCountdown", "调至最大音量异常", e)
        }

        // 2. 自动退出相对应不能打开的APP（执行返回桌面）
        scope.launch(Dispatchers.Main) {
            AutoFlowAccessibilityService.instance?.performHomeAction()
            delay(250L)
            AutoFlowAccessibilityService.instance?.performHomeAction()
        }

        // 3. 启动退出后10秒防重进锁定期：相对应的APP在退出后的10秒内，如果再次想进入APP的话，强制退出APP（多次点击进入依然强制退出）
        if (currentPkgs.isNotEmpty()) {
            startLockdown(currentPkgs, 10_000L)
        }

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

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("5分钟时间到，请放下手机")
            .setContentText("已自动退出应用并开启10秒禁止重进保护。点击可关闭响铃。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭响铃", dismissPendingIntent)
            .setContentIntent(dismissPendingIntent)
            .build()

        notificationManager.notify(9999, notification)

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = true
                }
                ringtone.play()
                activeRingtone = ringtone
            }
        } catch (_: Exception) { }
    }

    fun stopAlarm(context: Context) {
        stopCountdown(context)
    }

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
        notificationManager.notify(9999, NotificationCompat.Builder(context, "dummy").setSmallIcon(android.R.drawable.ic_menu_recent_history).build())
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
