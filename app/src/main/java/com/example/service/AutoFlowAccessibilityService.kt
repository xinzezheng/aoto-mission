package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.data.AppDatabase
import com.example.data.AutomationRepository
import com.example.data.TriggerType
import com.example.engine.ActiveCountdownManager
import com.example.engine.TaskExecutionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoFlowAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPackageName: String? = null

    // System packages that should be ignored (e.g. Status bar, notification shade, system dialogues)
    private val systemUiPackages = setOf(
        "com.android.systemui",
        "com.miui.notification",
        "com.miui.securitycenter",
        "com.huawei.systemui",
        "com.oppo.systemui",
        "com.vivo.systemui",
        "android"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutoFlowAccessibility", "无障碍服务连接成功")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Ignore self and system UI shade events (e.g., pulling down status bar / notifications)
            if (packageName == this.packageName || systemUiPackages.contains(packageName)) {
                return
            }

            if (packageName == lastPackageName) {
                return
            }

            lastPackageName = packageName

            serviceScope.launch {
                processPackageChange(packageName)
            }
        }
    }

    private suspend fun processPackageChange(packageName: String) {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = AutomationRepository(database.automationDao())

        val activeRules = repository.getActiveRulesList()
        val matchingRule = activeRules.firstOrNull { rule ->
            if (rule.triggerType != TriggerType.APP_LAUNCH) return@firstOrNull false

            val targetPackages = rule.triggerPackageName.split(",").map { it.trim() }
            val isPackageMatch = targetPackages.any { target ->
                target.isNotEmpty() && (packageName.equals(target, ignoreCase = true) || packageName.contains(target, ignoreCase = true))
            }

            val isKeywordMatch = when {
                packageName.contains("aweme", ignoreCase = true) -> true
                packageName.contains("musically", ignoreCase = true) -> true
                packageName.contains("xhs", ignoreCase = true) || packageName.contains("xingin", ignoreCase = true) -> true
                packageName.contains("bili", ignoreCase = true) -> true
                else -> false
            }

            rule.triggerPackageName.isEmpty() || isPackageMatch || isKeywordMatch
        }

        if (matchingRule != null) {
            Log.d("AutoFlowAccessibility", "进入目标应用: $packageName")
            ActiveCountdownManager.handleAppOpened(applicationContext, matchingRule)
        } else {
            Log.d("AutoFlowAccessibility", "离开目标应用: $packageName")
            ActiveCountdownManager.handleAppExited(applicationContext)
        }
    }

    override fun onInterrupt() {
        Log.d("AutoFlowAccessibility", "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun performHomeAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    companion object {
        var instance: AutoFlowAccessibilityService? = null
            private set
    }
}
