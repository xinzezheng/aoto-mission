package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TriggerType {
    APP_LAUNCH,      // 触发：打开应用
    SCHEDULED_TIME,  // 触发：定时时刻
    COUNTDOWN_TIMER, // 触发：倒计时响铃
    MANUAL           // 触发：手动测试
}

enum class ExitTriggerType {
    NONE,       // 无退出条件 (运行至完成/手动停止)
    APP_EXIT,   // 退出应用 (离开所选应用时结束)
    TIMEOUT     // 定时超时结束
}

enum class ActionType {
    LAUNCH_APP,          // 执行：打开指定应用
    SET_ALARM,           // 执行：倒计时响铃
    ADJUST_VOLUME,       // 执行：调节媒体音量
    ADJUST_BRIGHTNESS,   // 执行：调节屏幕亮度
    SEND_NOTIFICATION,   // 执行：推送通知提醒
    PERFORM_HOME_GESTURE // 执行：自动返回桌面
}

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val isEnabled: Boolean = true,

    // 1. 启动触发条件
    val triggerType: TriggerType = TriggerType.APP_LAUNCH,
    val triggerPackageName: String = "",    // 启动目标应用包名
    val triggerAppLabel: String = "",       // 启动目标应用名称

    // 2. 退出/结束条件 (在触发条件下方单独选择)
    val exitTriggerType: ExitTriggerType = ExitTriggerType.NONE,
    val exitTriggerPackageName: String = "",// 退出目标应用包名
    val exitTriggerAppLabel: String = "",   // 退出目标应用名称

    // 3. 执行任务
    val actionType: ActionType = ActionType.SET_ALARM,
    val actionTargetPackage: String = "",   // 执行目标应用包名
    val actionTargetAppLabel: String = "",  // 执行目标应用名称
    val actionValue: Int = 5,               // 参数 (音量%, 亮度%, 倒计时分钟)

    val triggerTimeMinutes: Int = 0,
    val triggerCountdownSeconds: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastExecutedMs: Long = 0L,
    val executionCount: Int = 0
)
