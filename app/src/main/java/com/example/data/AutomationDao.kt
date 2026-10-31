package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<AutomationRule>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getActiveRulesList(): List<AutomationRule>

    @Query("SELECT * FROM automation_rules WHERE triggerType = :triggerType AND isEnabled = 1")
    suspend fun getActiveRulesForTrigger(triggerType: TriggerType): List<AutomationRule>

    @Query("SELECT * FROM automation_rules WHERE id = :ruleId")
    suspend fun getRuleById(ruleId: Long): AutomationRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRule): Long

    @Update
    suspend fun updateRule(rule: AutomationRule)

    @Delete
    suspend fun deleteRule(rule: AutomationRule)

    @Query("DELETE FROM automation_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long)

    @Query("SELECT * FROM execution_logs ORDER BY timestampMs DESC LIMIT 100")
    fun getAllLogs(): Flow<List<ExecutionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLog)

    @Query("DELETE FROM execution_logs")
    suspend fun clearAllLogs()
}
