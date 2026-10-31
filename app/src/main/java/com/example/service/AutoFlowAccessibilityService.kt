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

    private fun isInputMethodPackage(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        val lower = packageName.lowercase()
        if (lower.contains("inputmethod") || lower.contains("keyboard") || lower.contains("ime") || lower.contains("pinyin") || lower.contains("shuru")) {
            return true
        }
        return try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val enabledImes = imm?.enabledInputMethodList ?: emptyList()
            val allImes = imm?.inputMethodList ?: emptyList()
            enabledImes.any { it.packageName.equals(packageName, ignoreCase = true) } ||
                    allImes.any { it.packageName.equals(packageName, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName == this.packageName || systemUiPackages.contains(packageName) || isInputMethodPackage(packageName)) {
                return
            }

            val previousPackage = lastPackageName
            if (packageName == previousPackage) {
                return
            }

            lastPackageName = packageName

            serviceScope.launch {
                processPackageChange(previousPackage, packageName)
            }
        }
    }

    private suspend fun processPackageChange(previousPackage: String?, currentPackage: String) {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = AutomationRepository(database.automationDao())
        val executionManager = TaskExecutionManager(applicationContext, repository)

        val activeRules = repository.getActiveRulesList()

        // 1. 检查退出条件：只要用户退出了所选应用（满足退出条件），直接销毁并中断该任务，停止所有计时
        if (!previousPackage.isNullOrEmpty()) {
            val exitRules = activeRules.filter { rule ->
                if (rule.exitTriggerType != com.example.data.ExitTriggerType.APP_EXIT) return@filter false
                val targetPackages = rule.exitTriggerPackageName.ifEmpty { rule.triggerPackageName }
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                targetPackages.any { target ->
                    previousPackage.equals(target, ignoreCase = true) || previousPackage.contains(target, ignoreCase = true)
                }
            }

            for (rule in exitRules) {
                Log.d("AutoFlowAccessibility", "触发【退出应用条件-立即销毁中断任务】: ${rule.title} ($previousPackage)")
                executionManager.destroyOrInterruptRule(rule, "退出应用: $previousPackage")
            }
        }

        // 2. 检查实时倒计时：如果倒计时正在运行且当前不在目标应用内，立即停止并销毁倒计时
        val activeCountdown = ActiveCountdownManager.countdownState.value
        if (activeCountdown.isRunning) {
            val targetPkgs = activeCountdown.targetPackages
            val isStillInTarget = targetPkgs.any { target ->
                target.isNotEmpty() && (currentPackage.equals(target, ignoreCase = true) || currentPackage.contains(target, ignoreCase = true))
            }
            if (!isStillInTarget) {
                Log.d("AutoFlowAccessibility", "离开监控应用，立即停止并销毁倒计时任务 ($previousPackage -> $currentPackage)")
                ActiveCountdownManager.handleAppExited(applicationContext, "退出应用: ${previousPackage ?: ""}")
            }
        }

        // 3. 检查启动条件：打开应用时触发相应任务
        val launchRules = activeRules.filter { rule ->
            if (rule.triggerType != TriggerType.APP_LAUNCH) return@filter false
            val targetPackages = rule.triggerPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            targetPackages.any { target ->
                currentPackage.equals(target, ignoreCase = true) || currentPackage.contains(target, ignoreCase = true)
            }
        }

        for (rule in launchRules) {
            Log.d("AutoFlowAccessibility", "触发【打开应用】规则: ${rule.title} ($currentPackage)")
            executionManager.executeRule(rule, "打开应用: $currentPackage")
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
