package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: Long,
    val ruleTitle: String,
    val triggerType: TriggerType,
    val actionType: ActionType,
    val timestampMs: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS", "FAILED", "SKIPPED"
    val detailMessage: String
)
