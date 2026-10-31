package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.AutomationRepository
import com.example.engine.TaskExecutionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        val ruleTitle = intent.getStringExtra(EXTRA_RULE_TITLE) ?: "定时响铃任务"

        Log.d("AlarmReceiver", "收到定时闹钟触发广播，规则名称: $ruleTitle")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context.applicationContext)
                val repository = AutomationRepository(database.automationDao())
                val executionManager = TaskExecutionManager(context.applicationContext, repository)

                if (ruleId != -1L) {
                    val rule = repository.getRuleById(ruleId)
                    if (rule != null && rule.isEnabled) {
                        executionManager.executeRule(rule, "定时闹钟到期响铃")
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "处理定时闹钟响应异常", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
        const val EXTRA_RULE_TITLE = "extra_rule_title"
    }
}
