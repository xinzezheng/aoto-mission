package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.example.data.ActionType
import com.example.data.AppDatabase
import com.example.data.AutomationRepository
import com.example.data.ExitTriggerType
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

    private val cachedInputMethods = mutableSetOf<String>()

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
        refreshInputMethods()
        Log.d("AutoFlowAccessibility", "无障碍服务连接成功")
    }

    private fun refreshInputMethods() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            val list = (imm?.enabledInputMethodList ?: emptyList()) + (imm?.inputMethodList ?: emptyList())
            cachedInputMethods.clear()
            cachedInputMethods.addAll(list.map { it.packageName })
        } catch (_: Exception) { }
    }

    private fun isInputMethodPackage(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        if (cachedInputMethods.contains(packageName)) return true
        val lower = packageName.lowercase()
        return lower.contains("inputmethod") || lower.contains("keyboard") ||
                lower.contains("ime") || lower.contains("pinyin") || lower.contains("shuru") ||
                lower.contains("sogou") || lower.contains("baidu") || lower.contains("gboard") ||
                lower.contains("swiftkey") || lower.contains("xunfei")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // 0. 极速锁定拦截：相对应的APP在退出后的10秒内，如果再次想进入APP的话，强制退出APP（多次点击进入依然强制退出）
            if (ActiveCountdownManager.isPackageLocked(packageName)) {
                Log.d("AutoFlowAccessibility", "目标应用处于10秒锁定冷却期，立即强制退出并返回桌面: $packageName")
                performHomeAction()
                val remaining = ActiveCountdownManager.getLockdownRemainingSeconds()
                serviceScope.launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "时间已到！10秒内禁止重新进入（剩余 ${remaining} 秒）", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // 忽略本应用、系统UI状态栏通知面板、以及所有输入法（输入法打字均视作原应用内操作）
            if (packageName == this.packageName || systemUiPackages.contains(packageName) || isInputMethodPackage(packageName)) {
                return
            }

            val previousPackage = lastPackageName
            if (packageName == previousPackage) {
                return
            }
            lastPackageName = packageName

            // 极速响应：立即在内存中进行活跃倒计时目标应用出入检测，0 毫秒延迟响应
            val activeCountdown = ActiveCountdownManager.countdownState.value
            if (activeCountdown.isRunning) {
                val isNowInTarget = activeCountdown.targetPackages.any { target ->
                    target.isNotEmpty() && (packageName.equals(target, ignoreCase = true) || packageName.contains(target, ignoreCase = true))
                }

                if (isNowInTarget) {
                    // 用户在10秒内返回了监控应用：立即取消退出等待，继续之前的计时进度（绝不重新开始5分钟）
                    if (activeCountdown.isInExitGracePeriod) {
                        ActiveCountdownManager.onTargetAppReturned(applicationContext)
                    }
                } else {
                    // 用户离开了监控应用：立即启动10秒快速返回提醒（10秒内未返回才彻底停止）
                    ActiveCountdownManager.onTargetAppExited(applicationContext)
                }
            }

            // 异步处理规则启动与退出条件
            serviceScope.launch {
                processRuleChanges(previousPackage, packageName)
            }
        }
    }

    private suspend fun processRuleChanges(previousPackage: String?, currentPackage: String) {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = AutomationRepository(database.automationDao())
        val executionManager = TaskExecutionManager(applicationContext, repository)

        val activeRules = repository.getActiveRulesList()
        if (activeRules.isEmpty()) {
            return
        }

        // 1. 检查退出条件：只要离开所选应用（满足退出条件），直接静默销毁并中断该任务
        // 注意：如果是 SET_ALARM 倒计时规则，且倒计时正在运行或在10秒宽限期内，绝不能直接粗暴销毁，由 ActiveCountdownManager 统一管理10秒宽限期与返回恢复！
        if (!previousPackage.isNullOrEmpty()) {
            val exitRules = activeRules.filter { rule ->
                if (rule.exitTriggerType != ExitTriggerType.APP_EXIT) return@filter false
                if (rule.actionType == ActionType.SET_ALARM && ActiveCountdownManager.countdownState.value.isRunning) {
                    return@filter false
                }
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

        // 2. 检查启动条件：打开应用时触发相应任务（静默执行）
        // 注意：如果倒计时已经在运行（或者正在10秒内返回继续计时），绝不要重复执行或重置倒计时！
        val launchRules = activeRules.filter { rule ->
            if (rule.triggerType != TriggerType.APP_LAUNCH) return@filter false
            if (rule.actionType == ActionType.SET_ALARM && ActiveCountdownManager.countdownState.value.isRunning) {
                return@filter false
            }
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
