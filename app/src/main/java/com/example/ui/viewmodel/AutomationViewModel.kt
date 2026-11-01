package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AutomationRepository
import com.example.data.AutomationRule
import com.example.data.ExecutionLog
import com.example.engine.ActiveCountdownManager
import com.example.engine.CountdownState
import com.example.engine.TaskExecutionManager
import com.example.permission.PermissionItem
import com.example.permission.PermissionManager
import com.example.update.AppUpdateManager
import com.example.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AutomationViewModel(
    private val repository: AutomationRepository,
    private val context: Context
) : ViewModel() {

    private val permissionManager = PermissionManager(context)
    private val executionManager = TaskExecutionManager(context, repository)

    val activeCountdownState: StateFlow<CountdownState> = ActiveCountdownManager.countdownState
    val updateState: StateFlow<UpdateInfo> = AppUpdateManager.updateState

    val rules: StateFlow<List<AutomationRule>> = repository.allRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<ExecutionLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _permissionItems = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissionItems: StateFlow<List<PermissionItem>> = _permissionItems.asStateFlow()

    init {
        refreshPermissions()
        initializeDefaultRuleIfEmpty()
    }

    private fun initializeDefaultRuleIfEmpty() {
        viewModelScope.launch {
            val rules = repository.getActiveRulesList()
            if (rules.isEmpty()) {
                val defaultRule = AutomationRule(
                    title = "短视频与社区应用 5分钟定时响铃",
                    description = "打开抖音、TikTok、小红书或哔哩哔哩时自动开启5分钟倒计时，离开应用10秒后自动停止",
                    isEnabled = true,
                    triggerType = com.example.data.TriggerType.APP_LAUNCH,
                    triggerPackageName = "com.ss.android.ugc.aweme,com.zhiliaoapp.musically,com.xingin.xhs,tv.danmaku.bili",
                    triggerAppLabel = "抖音 / TikTok / 小红书 / 哔哩哔哩",
                    exitTriggerType = com.example.data.ExitTriggerType.APP_EXIT,
                    exitTriggerPackageName = "com.ss.android.ugc.aweme,com.zhiliaoapp.musically,com.xingin.xhs,tv.danmaku.bili",
                    exitTriggerAppLabel = "抖音 / TikTok / 小红书 / 哔哩哔哩",
                    actionType = com.example.data.ActionType.SET_ALARM,
                    actionValue = 5
                )
                repository.insertRule(defaultRule)
            }
        }
    }

    fun refreshPermissions() {
        _permissionItems.value = permissionManager.checkAllPermissions()
    }

    fun openPermissionSettings(item: PermissionItem) {
        permissionManager.openPermissionSettings(item)
    }

    fun openNextUngrantedPermission() {
        val next = permissionManager.getNextUngrantedPermission()
        if (next != null) {
            permissionManager.openPermissionSettings(next)
        }
    }

    fun toggleRuleEnabled(rule: AutomationRule) {
        viewModelScope.launch {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            repository.updateRule(updated)
        }
    }

    fun saveRule(rule: AutomationRule) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                repository.insertRule(rule)
            } else {
                repository.updateRule(rule)
            }
        }
    }

    fun deleteRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }

    fun executeRuleManually(rule: AutomationRule) {
        if (executionManager.checkAndPromptPermissionsForExecution()) {
            executionManager.executeRule(rule, "手动点击测试运行")
        }
    }

    fun stopActiveCountdown() {
        ActiveCountdownManager.stopCountdown(context)
    }

    fun checkForSoftwareUpdates() {
        viewModelScope.launch {
            AppUpdateManager.checkForUpdates(context)
        }
    }

    fun downloadAndInstallSoftwareUpdate() {
        viewModelScope.launch {
            AppUpdateManager.downloadAndInstallApk(context)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }
}

class AutomationViewModelFactory(
    private val repository: AutomationRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AutomationViewModel::class.java)) {
            return AutomationViewModel(repository, context) as T
        }
        throw IllegalArgumentException("未知 ViewModel 类型")
    }
}
