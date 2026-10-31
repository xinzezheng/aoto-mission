package com.example.data

import kotlinx.coroutines.flow.Flow

class AutomationRepository(private val dao: AutomationDao) {

    val allRules: Flow<List<AutomationRule>> = dao.getAllRules()
    val allLogs: Flow<List<ExecutionLog>> = dao.getAllLogs()

    suspend fun getActiveRulesList(): List<AutomationRule> = dao.getActiveRulesList()

    suspend fun getRuleById(id: Long): AutomationRule? = dao.getRuleById(id)

    suspend fun insertRule(rule: AutomationRule): Long = dao.insertRule(rule)

    suspend fun updateRule(rule: AutomationRule) = dao.updateRule(rule)

    suspend fun deleteRule(rule: AutomationRule) = dao.deleteRule(rule)

    suspend fun deleteRuleById(id: Long) = dao.deleteRuleById(id)

    suspend fun insertLog(log: ExecutionLog) = dao.insertLog(log)

    suspend fun clearAllLogs() = dao.clearAllLogs()
}
