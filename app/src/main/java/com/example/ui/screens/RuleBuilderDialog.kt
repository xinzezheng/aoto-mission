package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ActionType
import com.example.data.AutomationRule
import com.example.data.ExitTriggerType
import com.example.data.TriggerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBuilderDialog(
    initialRule: AutomationRule? = null,
    onDismiss: () -> Unit,
    onSaveRule: (AutomationRule) -> Unit
) {
    var title by remember { mutableStateOf(initialRule?.title ?: "") }

    // 1. 启动触发条件
    var triggerType by remember { mutableStateOf(initialRule?.triggerType ?: TriggerType.APP_LAUNCH) }
    var triggerAppLabel by remember { mutableStateOf(initialRule?.triggerAppLabel ?: "") }
    var triggerPackageName by remember { mutableStateOf(initialRule?.triggerPackageName ?: "") }

    // 2. 退出/结束条件 (在触发条件下方单独选择)
    var exitTriggerType by remember { mutableStateOf(initialRule?.exitTriggerType ?: ExitTriggerType.APP_EXIT) }
    var exitTriggerAppLabel by remember { mutableStateOf(initialRule?.exitTriggerAppLabel ?: "") }
    var exitTriggerPackageName by remember { mutableStateOf(initialRule?.exitTriggerPackageName ?: "") }

    // 3. 执行任务
    var actionType by remember { mutableStateOf(initialRule?.actionType ?: ActionType.SET_ALARM) }
    var actionValue by remember { mutableIntStateOf(initialRule?.actionValue ?: 5) }
    var actionTargetAppLabel by remember { mutableStateOf(initialRule?.actionTargetAppLabel ?: "") }
    var actionTargetPackage by remember { mutableStateOf(initialRule?.actionTargetPackage ?: "") }

    var triggerExpanded by remember { mutableStateOf(false) }
    var exitTriggerExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }

    var showAppPickerForTrigger by remember { mutableStateOf(false) }
    var showAppPickerForExitTrigger by remember { mutableStateOf(false) }
    var showAppPickerForAction by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("rule_builder_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialRule == null) "新增自动化任务" else "编辑自动化规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_rule_builder")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Rule Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("规则名称 (可留空自动生成)") },
                    placeholder = { Text("例如：打开应用启动，退出应用时结束") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. 启动触发条件 Section
                Text(
                    text = "步骤 1: 启动触发条件类型",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = triggerExpanded,
                    onExpandedChange = { triggerExpanded = !triggerExpanded }
                ) {
                    OutlinedTextField(
                        value = when (triggerType) {
                            TriggerType.APP_LAUNCH -> "应用 - 打开所选应用时触发"
                            TriggerType.SCHEDULED_TIME -> "定时时刻触发"
                            TriggerType.COUNTDOWN_TIMER -> "倒计时响铃"
                            TriggerType.MANUAL -> "手动测试按钮"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择启动触发条件") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = triggerExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("select_trigger_type")
                    )
                    ExposedDropdownMenu(
                        expanded = triggerExpanded,
                        onDismissRequest = { triggerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("应用 - 打开所选应用时触发") },
                            onClick = { triggerType = TriggerType.APP_LAUNCH; triggerExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("定时时刻触发") },
                            onClick = { triggerType = TriggerType.SCHEDULED_TIME; triggerExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("手动测试按钮") },
                            onClick = { triggerType = TriggerType.MANUAL; triggerExpanded = false }
                        )
                    }
                }

                if (triggerType == TriggerType.APP_LAUNCH) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAppPickerForTrigger = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Apps, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (triggerAppLabel.isEmpty()) "点击选择启动触发的目标应用" else "已选启动应用: $triggerAppLabel",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. 退出/结束条件 Section (在触发条件下方单独选择)
                Text(
                    text = "步骤 2: 退出/结束条件类型 (单独配置)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = exitTriggerExpanded,
                    onExpandedChange = { exitTriggerExpanded = !exitTriggerExpanded }
                ) {
                    OutlinedTextField(
                        value = when (exitTriggerType) {
                            ExitTriggerType.APP_EXIT -> "退出应用 (离开所选应用时结束)"
                            ExitTriggerType.NONE -> "无退出条件 (运行至完成或手动停止)"
                            ExitTriggerType.TIMEOUT -> "超时自动结束"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择退出/结束条件") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exitTriggerExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("select_exit_trigger_type")
                    )
                    ExposedDropdownMenu(
                        expanded = exitTriggerExpanded,
                        onDismissRequest = { exitTriggerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("退出应用 (离开所选应用时结束)") },
                            onClick = { exitTriggerType = ExitTriggerType.APP_EXIT; exitTriggerExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("无退出条件 (运行至完成或手动停止)") },
                            onClick = { exitTriggerType = ExitTriggerType.NONE; exitTriggerExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("超时自动结束") },
                            onClick = { exitTriggerType = ExitTriggerType.TIMEOUT; exitTriggerExpanded = false }
                        )
                    }
                }

                if (exitTriggerType == ExitTriggerType.APP_EXIT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAppPickerForExitTrigger = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Apps, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (exitTriggerAppLabel.isEmpty()) {
                                if (triggerAppLabel.isNotEmpty()) "默认与启动应用相同 ($triggerAppLabel)，点击修改"
                                else "点击选择退出触发的目标应用"
                            } else "已选退出应用: $exitTriggerAppLabel",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 3. 执行任务 Section
                Text(
                    text = "步骤 3: 执行任务类型",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = actionExpanded,
                    onExpandedChange = { actionExpanded = !actionExpanded }
                ) {
                    OutlinedTextField(
                        value = when (actionType) {
                            ActionType.SET_ALARM -> "倒计时响铃"
                            ActionType.LAUNCH_APP -> "打开指定应用"
                            ActionType.ADJUST_VOLUME -> "调节媒体音量"
                            ActionType.ADJUST_BRIGHTNESS -> "调节屏幕亮度"
                            ActionType.SEND_NOTIFICATION -> "推送通知提醒"
                            ActionType.PERFORM_HOME_GESTURE -> "自动返回桌面"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择执行任务") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("select_action_type")
                    )
                    ExposedDropdownMenu(
                        expanded = actionExpanded,
                        onDismissRequest = { actionExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("倒计时响铃") },
                            onClick = { actionType = ActionType.SET_ALARM; actionExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("打开指定应用") },
                            onClick = { actionType = ActionType.LAUNCH_APP; actionExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("调节媒体音量") },
                            onClick = { actionType = ActionType.ADJUST_VOLUME; actionExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("调节屏幕亮度") },
                            onClick = { actionType = ActionType.ADJUST_BRIGHTNESS; actionExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("推送通知提醒") },
                            onClick = { actionType = ActionType.SEND_NOTIFICATION; actionExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("自动返回桌面") },
                            onClick = { actionType = ActionType.PERFORM_HOME_GESTURE; actionExpanded = false }
                        )
                    }
                }

                // Action Controls
                when (actionType) {
                    ActionType.SET_ALARM -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "倒计时时长: ${actionValue} 分钟", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = actionValue.toFloat(),
                            onValueChange = { actionValue = it.toInt() },
                            valueRange = 1f..60f,
                            steps = 59,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("action_slider")
                        )
                    }
                    ActionType.LAUNCH_APP -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showAppPickerForAction = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Apps, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (actionTargetAppLabel.isEmpty()) "点击选择要打开的目标应用" else "要打开的应用: $actionTargetAppLabel",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    ActionType.ADJUST_VOLUME -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "目标音量: ${actionValue}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = actionValue.toFloat(),
                            onValueChange = { actionValue = it.toInt() },
                            valueRange = 0f..100f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ActionType.ADJUST_BRIGHTNESS -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "目标屏幕亮度: ${actionValue}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = actionValue.toFloat(),
                            onValueChange = { actionValue = it.toInt() },
                            valueRange = 0f..100f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ActionType.PERFORM_HOME_GESTURE -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "提示: 触发后无障碍服务将自动为您模拟按键返回手机主屏幕。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionType.SEND_NOTIFICATION -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "提示: 触发后系统通知栏将自动推送任务提醒状态。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_rule_button")
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val startAppText = if (triggerAppLabel.isNotEmpty()) triggerAppLabel else "应用"
                            val exitAppText = if (exitTriggerAppLabel.isNotEmpty()) exitTriggerAppLabel else startAppText

                            val actionTextDesc = when (actionType) {
                                ActionType.SET_ALARM -> "开启 ${actionValue} 分钟倒计时响铃"
                                ActionType.LAUNCH_APP -> "打开 ${actionTargetAppLabel.ifEmpty { "目标应用" }}"
                                ActionType.ADJUST_VOLUME -> "调节音量至 ${actionValue}%"
                                ActionType.ADJUST_BRIGHTNESS -> "调节亮度至 ${actionValue}%"
                                ActionType.SEND_NOTIFICATION -> "推送通知提醒"
                                ActionType.PERFORM_HOME_GESTURE -> "自动返回桌面"
                            }

                            val finalTitle = title.ifEmpty {
                                "打开 $startAppText 触发 -> $actionTextDesc"
                            }
                            val finalDesc = "启动: 打开 $startAppText | 结束条件: ${if (exitTriggerType == ExitTriggerType.APP_EXIT) "退出 $exitAppText" else "手动/完成"} | 执行: $actionTextDesc"

                            val finalExitPackage = if (exitTriggerType == ExitTriggerType.APP_EXIT) {
                                if (exitTriggerPackageName.isNotEmpty()) exitTriggerPackageName else triggerPackageName
                            } else ""

                            val finalExitLabel = if (exitTriggerType == ExitTriggerType.APP_EXIT) {
                                if (exitTriggerAppLabel.isNotEmpty()) exitTriggerAppLabel else triggerAppLabel
                            } else ""

                            val newRule = (initialRule ?: AutomationRule(
                                title = finalTitle,
                                description = finalDesc,
                                triggerType = triggerType,
                                triggerAppLabel = triggerAppLabel,
                                triggerPackageName = triggerPackageName,
                                exitTriggerType = exitTriggerType,
                                exitTriggerAppLabel = finalExitLabel,
                                exitTriggerPackageName = finalExitPackage,
                                actionType = actionType,
                                actionTargetAppLabel = actionTargetAppLabel,
                                actionTargetPackage = actionTargetPackage,
                                actionValue = actionValue
                            )).copy(
                                title = finalTitle,
                                description = finalDesc,
                                triggerType = triggerType,
                                triggerAppLabel = triggerAppLabel,
                                triggerPackageName = triggerPackageName,
                                exitTriggerType = exitTriggerType,
                                exitTriggerAppLabel = finalExitLabel,
                                exitTriggerPackageName = finalExitPackage,
                                actionType = actionType,
                                actionTargetAppLabel = actionTargetAppLabel,
                                actionTargetPackage = actionTargetPackage,
                                actionValue = actionValue
                            )
                            onSaveRule(newRule)
                        },
                        modifier = Modifier.testTag("save_rule_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("保存规则", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal app picker dialogs
    if (showAppPickerForTrigger) {
        AppSelectorDialog(
            selectedPackages = triggerPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            onDismiss = { showAppPickerForTrigger = false },
            onConfirmSelection = { selectedList ->
                triggerAppLabel = selectedList.joinToString(" / ") { it.appName }
                triggerPackageName = selectedList.joinToString(",") { it.packageName }
                showAppPickerForTrigger = false
            }
        )
    }

    if (showAppPickerForExitTrigger) {
        AppSelectorDialog(
            selectedPackages = exitTriggerPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            onDismiss = { showAppPickerForExitTrigger = false },
            onConfirmSelection = { selectedList ->
                exitTriggerAppLabel = selectedList.joinToString(" / ") { it.appName }
                exitTriggerPackageName = selectedList.joinToString(",") { it.packageName }
                showAppPickerForExitTrigger = false
            }
        )
    }

    if (showAppPickerForAction) {
        AppSelectorDialog(
            selectedPackages = if (actionTargetPackage.isNotEmpty()) listOf(actionTargetPackage) else emptyList(),
            onDismiss = { showAppPickerForAction = false },
            onConfirmSelection = { selectedList ->
                actionTargetAppLabel = selectedList.joinToString(" / ") { it.appName }
                actionTargetPackage = selectedList.map { it.packageName }.firstOrNull() ?: ""
                showAppPickerForAction = false
            }
        )
    }
}
