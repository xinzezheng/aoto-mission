package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AutomationRule
import com.example.ui.viewmodel.AutomationViewModel

enum class NavigationTab(val title: String, val icon: ImageVector) {
    RULES("规则列表", Icons.Default.Schedule),
    PERMISSIONS("权限中心", Icons.Default.Security),
    LOGS("运行日志", Icons.Default.History)
}

@Composable
fun MainNavigationScreen(
    viewModel: AutomationViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showRuleBuilderDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutomationRule?>(null) }

    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionItems.collectAsStateWithLifecycle()
    val activeCountdownState by viewModel.activeCountdownState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (NavigationTab.entries[selectedTab]) {
            NavigationTab.RULES -> {
                RulesListScreen(
                    rules = rules,
                    activeCountdownState = activeCountdownState,
                    onStopCountdown = { viewModel.stopActiveCountdown() },
                    onToggleRule = { viewModel.toggleRuleEnabled(it) },
                    onExecuteRule = { viewModel.executeRuleManually(it) },
                    onDeleteRule = { viewModel.deleteRule(it) },
                    onCreateRuleClick = {
                        editingRule = null
                        showRuleBuilderDialog = true
                    },
                    modifier = screenModifier
                )
            }
            NavigationTab.PERMISSIONS -> {
                PermissionCenterScreen(
                    permissions = permissions,
                    onRefreshPermissions = { viewModel.refreshPermissions() },
                    onOpenPermissionSettings = { viewModel.openPermissionSettings(it) },
                    onOpenNextUngranted = { viewModel.openNextUngrantedPermission() },
                    modifier = screenModifier
                )
            }
            NavigationTab.LOGS -> {
                ExecutionLogsScreen(
                    logs = logs,
                    onClearLogs = { viewModel.clearAllLogs() },
                    modifier = screenModifier
                )
            }
        }

        if (showRuleBuilderDialog) {
            RuleBuilderDialog(
                initialRule = editingRule,
                onDismiss = { showRuleBuilderDialog = false },
                onSaveRule = { rule ->
                    viewModel.saveRule(rule)
                    showRuleBuilderDialog = false
                }
            )
        }
    }
}
