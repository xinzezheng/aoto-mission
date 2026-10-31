package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TriggerType {
    APP_LAUNCH,      // Triggered when a specific target app opens
    SCHEDULED_TIME,  // Triggered at exact daily time or schedule
    COUNTDOWN_TIMER, // Triggered after a countdown timer expires
    MANUAL           // Triggered manually via quick-run button
}

enum class ActionType {
    LAUNCH_APP,          // Launch another specified app
    SET_ALARM,           // Set alarm or timer
    ADJUST_VOLUME,       // Set media/notification volume
    ADJUST_BRIGHTNESS,   // Adjust screen brightness
    SEND_NOTIFICATION,   // Post system notification
    PERFORM_HOME_GESTURE // Press Home via accessibility
}

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val isEnabled: Boolean = true,
    val triggerType: TriggerType,
    val triggerPackageName: String = "",    // Target app package name for APP_LAUNCH
    val triggerAppLabel: String = "",       // Target app display name
    val triggerTimeMinutes: Int = 0,        // Time of day in minutes (e.g., 8:00 AM -> 480)
    val triggerCountdownSeconds: Int = 0,   // Countdown duration
    val actionType: ActionType,
    val actionTargetPackage: String = "",   // Action target package name
    val actionTargetAppLabel: String = "",  // Action target app label
    val actionValue: Int = 50,              // Generic parameter (volume %, brightness %, timer minutes)
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastExecutedMs: Long = 0L,
    val executionCount: Int = 0
)
