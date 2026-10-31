package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionType
import com.example.data.AutomationRule
import com.example.data.TriggerType

@Composable
fun PresetLibraryScreen(
    onInstallPreset: (AutomationRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        AutomationRule(
            id = 201,
            title = "打开 YouTube 自动 30 分钟专注计时",
            description = "进入视频应用时自动在后台开启 30 分钟倒计时闹钟，防止沉迷。",
            isEnabled = true,
            triggerType = TriggerType.APP_LAUNCH,
            triggerPackageName = "com.google.android.youtube",
            triggerAppLabel = "YouTube / 视频播放",
            actionType = ActionType.SET_ALARM,
            actionValue = 30
        ),
        AutomationRule(
            id = 202,
            title = "早晨 07:30 音量恢复与唤醒",
            description = "每天早晨 7 点 30 分自动将媒体音量提升至 80%，准备播放早安广播。",
            isEnabled = true,
            triggerType = TriggerType.SCHEDULED_TIME,
            triggerTimeMinutes = 450,
            actionType = ActionType.ADJUST_VOLUME,
            actionValue = 80
        ),
        AutomationRule(
            id = 203,
            title = "夜间看书一键防刺眼亮度",
            description = "一键将屏幕亮度调至 15% 极暗模式，保护夜间用眼安全。",
            isEnabled = true,
            triggerType = TriggerType.MANUAL,
            actionType = ActionType.ADJUST_BRIGHTNESS,
            actionValue = 15
        ),
        AutomationRule(
            id = 204,
            title = "无障碍一键自动返回桌面",
            description = "调用系统无障碍服务能力，快速返回系统主屏幕。",
            isEnabled = true,
            triggerType = TriggerType.MANUAL,
            actionType = ActionType.PERFORM_HOME_GESTURE
        ),
        AutomationRule(
            id = 205,
            title = "定时发送喝水与休息提醒通知",
            description = "倒计时 45 分钟发送健康休息通知，提醒您起身喝水活动身体。",
            isEnabled = true,
            triggerType = TriggerType.COUNTDOWN_TIMER,
            actionType = ActionType.SEND_NOTIFICATION
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preset_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "常用预设流程库 (Preset Templates)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "精选高频自动化组合，一键直接导入至您的规则库中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "系统预设模版列表",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        items(presets, key = { it.id }) { preset ->
            PresetCardItem(
                preset = preset,
                onInstall = { onInstallPreset(preset) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PresetCardItem(
    preset: AutomationRule,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preset_card_${preset.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("install_preset_button_${preset.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "导入规则", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TriggerChip(type = preset.triggerType)
                Spacer(modifier = Modifier.width(6.dp))
                ActionChip(type = preset.actionType)
            }
        }
    }
}
