package com.example.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ActionType
import com.example.data.AutomationRule
import com.example.data.TriggerType

data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleBuilderDialog(
    initialRule: AutomationRule? = null,
    onDismiss: () -> Unit,
    onSaveRule: (AutomationRule) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialRule?.title ?: "") }
    var triggerType by remember { mutableStateOf(initialRule?.triggerType ?: TriggerType.APP_LAUNCH) }
    var actionType by remember { mutableStateOf(initialRule?.actionType ?: ActionType.SET_ALARM) }
    var actionValue by remember { mutableIntStateOf(initialRule?.actionValue ?: 5) }

    var actionTargetAppLabel by remember { mutableStateOf(initialRule?.actionTargetAppLabel ?: "") }
    var actionTargetPackage by remember { mutableStateOf(initialRule?.actionTargetPackage ?: "") }

    var triggerExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    var actionTargetAppExpanded by remember { mutableStateOf(false) }

    // Start with all app choices UNCHECKED (isSelected = false)
    val defaultAppList = remember {
        listOf(
            AppInfoItem("抖音", "com.ss.android.ugc.aweme", false),
            AppInfoItem("TikTok", "com.zhiliaoapp.musically", false),
            AppInfoItem("小红书", "com.xingin.xhs", false),
            AppInfoItem("哔哩哔哩", "tv.danmaku.bili", false),
            AppInfoItem("微信", "com.tencent.mm", false),
            AppInfoItem("支付宝", "com.eg.android.Alipay", false),
            AppInfoItem("微博", "com.sina.weibo", false),
            AppInfoItem("快手", "com.smile.gifmaker", false),
            AppInfoItem("系统设置", "com.android.settings", false)
        )
    }

    var availableApps by remember { mutableStateOf<List<AppInfoItem>>(defaultAppList) }

    // Load installed launcher applications from device PackageManager
    LaunchedEffect(Unit) {
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            val installedList = resolveInfos.mapNotNull { info ->
                val name = info.loadLabel(pm).toString()
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName) {
                    AppInfoItem(name, pkg, false)
                } else null
            }.distinctBy { it.packageName }

            if (installedList.isNotEmpty()) {
                val initialSelectedPackages = (initialRule?.triggerPackageName ?: "")
                    .split(",").map { it.trim() }

                val merged = (defaultAppList + installedList)
                    .distinctBy { it.packageName }
                    .map { item ->
                        item.copy(isSelected = initialSelectedPackages.contains(item.packageName))
                    }
                availableApps = merged
            }
        } catch (_: Exception) { }
    }

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
                    placeholder = { Text("例如：打开软件自动执行任务") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_rule_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Trigger Selection Section
                Text(
                    text = "触发条件类型",
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
                            TriggerType.APP_LAUNCH -> "应用 (打开所选应用时触发)"
                            TriggerType.SCHEDULED_TIME -> "定时时刻"
                            TriggerType.COUNTDOWN_TIMER -> "倒计时结束"
                            TriggerType.MANUAL -> "手动按钮点击"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择触发条件") },
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
                            text = { Text("应用 (打开所选应用时触发)") },
                            onClick = { triggerType = TriggerType.APP_LAUNCH; triggerExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("定时时刻") },
                            onClick = { triggerType = TriggerType.SCHEDULED_TIME; triggerExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("手动按钮点击") },
                            onClick = { triggerType = TriggerType.MANUAL; triggerExpanded = false }
                        )
                    }
                }

                // App Picker List when triggerType == APP_LAUNCH
                if (triggerType == TriggerType.APP_LAUNCH) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "选择要监测的目标应用 (点击勾选)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableApps.forEachIndexed { index, app ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (app.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    availableApps = availableApps.toMutableList().also {
                                        it[index] = app.copy(isSelected = !app.isSelected)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (app.isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (app.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (app.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Action Selection Section
                Text(
                    text = "执行任务类型",
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

                // Action Specific Controls
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
                        ExposedDropdownMenuBox(
                            expanded = actionTargetAppExpanded,
                            onExpandedChange = { actionTargetAppExpanded = !actionTargetAppExpanded }
                        ) {
                            OutlinedTextField(
                                value = actionTargetAppLabel.ifEmpty { "选择要打开的目标应用" },
                                onValueChange = { actionTargetAppLabel = it },
                                readOnly = true,
                                label = { Text("要打开的目标应用") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionTargetAppExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = actionTargetAppExpanded,
                                onDismissRequest = { actionTargetAppExpanded = false }
                            ) {
                                availableApps.forEach { app ->
                                    DropdownMenuItem(
                                        text = { Text(app.appName) },
                                        onClick = {
                                            actionTargetAppLabel = app.appName
                                            actionTargetPackage = app.packageName
                                            actionTargetAppExpanded = false
                                        }
                                    )
                                }
                            }
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
                            val selectedApps = availableApps.filter { it.isSelected }
                            val triggerAppLabel = if (selectedApps.isNotEmpty()) {
                                selectedApps.joinToString(" / ") { it.appName }
                            } else "任意应用"

                            val triggerPackageName = if (selectedApps.isNotEmpty()) {
                                selectedApps.joinToString(",") { it.packageName }
                            } else ""

                            val actionTextDesc = when (actionType) {
                                ActionType.SET_ALARM -> "开启 ${actionValue} 分钟倒计时响铃"
                                ActionType.LAUNCH_APP -> "打开 ${actionTargetAppLabel.ifEmpty { "目标应用" }}"
                                ActionType.ADJUST_VOLUME -> "调节音量至 ${actionValue}%"
                                ActionType.ADJUST_BRIGHTNESS -> "调节亮度至 ${actionValue}%"
                                ActionType.SEND_NOTIFICATION -> "推送通知提醒"
                                ActionType.PERFORM_HOME_GESTURE -> "自动返回桌面"
                            }

                            val finalTitle = title.ifEmpty {
                                "打开 ${triggerAppLabel} -> $actionTextDesc"
                            }
                            val finalDesc = "触发: 打开 $triggerAppLabel | 执行: $actionTextDesc"

                            val newRule = (initialRule ?: AutomationRule(
                                title = finalTitle,
                                description = finalDesc,
                                triggerType = triggerType,
                                triggerAppLabel = triggerAppLabel,
                                triggerPackageName = triggerPackageName,
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
}
