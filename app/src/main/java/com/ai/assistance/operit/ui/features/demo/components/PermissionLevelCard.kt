package com.ai.assistance.operit.ui.features.demo.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PermissionLevelCard(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasAccessibilityServiceEnabled: Boolean,
        hasLocationPermission: Boolean,
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        isOperitTerminalInstalled: Boolean,
        isDeviceRooted: Boolean,
        hasRootAccess: Boolean,
        isAccessibilityProviderInstalled: Boolean, // 新增：提供者App是否已安装
        isAccessibilityUpdateNeeded: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onInstallAccessibilityProviderClick: () -> Unit, // 新增：安装提供者App的回调
        onLocationPermissionClick: () -> Unit,
        onShizukuClick: () -> Unit,
        onOperitTerminalClick: () -> Unit,
        onRootClick: () -> Unit,
        isRefreshing: Boolean = false,
        onRefresh: () -> Unit,
        onPermissionLevelChange: (AndroidPermissionLevel) -> Unit = {},
        onPermissionLevelSet: (AndroidPermissionLevel) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 获取当前权限级别
    val preferredPermissionLevel =
            androidPermissionPreferences.preferredPermissionLevelFlow.collectAsState(
                    initial = AndroidPermissionLevel.STANDARD
            )

    // 当前显示的权限级别（可能与实际使用的不同）
    var displayedPermissionLevel by remember {
        mutableStateOf(preferredPermissionLevel.value ?: AndroidPermissionLevel.STANDARD)
    }

    // 当显示的权限级别变化时，通知父组件
    LaunchedEffect(displayedPermissionLevel) { onPermissionLevelChange(displayedPermissionLevel) }

    // 动画状态
    val elevation by
            animateDpAsState(
                    targetValue =
                            if (displayedPermissionLevel == preferredPermissionLevel.value) 6.dp
                            else 2.dp,
                    label = "Card Elevation"
            )

    // 当组件首次加载时，同步显示级别和实际级别
    LaunchedEffect(Unit) {
        displayedPermissionLevel = preferredPermissionLevel.value ?: AndroidPermissionLevel.STANDARD
        onPermissionLevelChange(displayedPermissionLevel)
    }

    // 添加一个简单的刷新动画
    var refreshRotation by remember { mutableStateOf(0f) }
    val rotationAngle by
            animateFloatAsState(
                    targetValue = refreshRotation,
                    animationSpec = tween(500),
                    label = "Refresh Rotation"
            )

    // 刷新时触发旋转动画
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshRotation += 360f
        }
    }

    Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                    )
    ) {
        Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row with icon and title - modify to left-aligned
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                val icon =
                        when (displayedPermissionLevel) {
                            AndroidPermissionLevel.STANDARD -> Icons.Default.Shield
                            AndroidPermissionLevel.ACCESSIBILITY -> Icons.Default.Shield
                            AndroidPermissionLevel.ADMIN -> Icons.Default.Shield
                            AndroidPermissionLevel.DEBUGGER -> Icons.Default.Shield
                            AndroidPermissionLevel.ROOT -> Icons.Default.Lock
                            null -> Icons.Default.Shield // 默认使用标准图标
                        }

                Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.permission_level_icon),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                        text = stringResource(R.string.permission_level),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }

            // 添加分割线
            HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // 权限级别选择器 - 更紧凑的选项卡
            PermissionLevelSelector(
                    currentLevel = displayedPermissionLevel,
                    activeLevel = preferredPermissionLevel.value,
                    onLevelSelected = { level -> displayedPermissionLevel = level }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 权限级别描述 - 更紧凑的描述区域
            AnimatedContent(
                    targetState = displayedPermissionLevel,
                    transitionSpec = {
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> -width } + fadeOut())
                    },
                    label = "Permission Description Animation"
            ) { level ->
                PermissionLevelVisualDescription(level ?: AndroidPermissionLevel.STANDARD)
            }

            // 显示状态指示条 - 更紧凑的状态条
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                // 当显示的权限级别与当前活动级别不同时显示设置按钮
                if (displayedPermissionLevel != preferredPermissionLevel.value) {
                    Button(
                            onClick = {
                                // 将当前选择的权限级别设置为激活状态
                                coroutineScope.launch {
                                    androidPermissionPreferences.savePreferredPermissionLevel(
                                            displayedPermissionLevel
                                    )
                                    Toast.makeText(
                                                    context,
                                                    context.getString(R.string.permission_level_set_active, displayedPermissionLevel.name),
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()

                                    // 调用权限级别设置回调，传递刚刚设置的权限级别
                                    onPermissionLevelSet(displayedPermissionLevel)
                                }
                            },
                            modifier =
                                    Modifier.align(Alignment.Center)
                                            .widthIn(max = 120.dp)
                                            .heightIn(max = 32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                    )
                    ) {
                        Text(
                            text = stringResource(R.string.set_as_current_level), 
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) 
                     }
                } else {
                    Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.current_level_in_use),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                stringResource(R.string.current_level_in_use),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 刷新按钮 - 更紧凑
                IconButton(
                        onClick = {
                            refreshRotation += 360f
                            onRefresh()
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription =
                                    if (isRefreshing) stringResource(R.string.refreshing)
                                    else stringResource(R.string.refresh_permission_status),
                            modifier =
                                    Modifier.graphicsLayer(rotationZ = rotationAngle).size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 权限内容区域 - 使用动画过渡
            AnimatedContent(
                    targetState = displayedPermissionLevel,
                    transitionSpec = {
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> -width } + fadeOut())
                    },
                    label = "Permission Content Animation"
            ) { level ->
                when (level) {
                    AndroidPermissionLevel.STANDARD -> {
                        PermissionSectionContainer(
                                isActive =
                                        preferredPermissionLevel.value ==
                                                AndroidPermissionLevel.STANDARD,
                                isCurrentlyDisplayed = true,
                                content = {
                                    StandardPermissionSection(
                                            hasStoragePermission = hasStoragePermission,
                                            hasOverlayPermission = hasOverlayPermission,
                                            hasBatteryOptimizationExemption =
                                                    hasBatteryOptimizationExemption,
                                            hasLocationPermission = hasLocationPermission,
                                            isOperitTerminalInstalled = isOperitTerminalInstalled,
                                            onStoragePermissionClick = onStoragePermissionClick,
                                            onOverlayPermissionClick = onOverlayPermissionClick,
                                            onBatteryOptimizationClick = onBatteryOptimizationClick,
                                            onLocationPermissionClick = onLocationPermissionClick,
                                            onOperitTerminalClick = onOperitTerminalClick
                                    )
                                }
                        )
                    }
                    AndroidPermissionLevel.ACCESSIBILITY -> {
                        PermissionSectionContainer(
                                isActive =
                                        preferredPermissionLevel.value ==
                                                AndroidPermissionLevel.ACCESSIBILITY,
                                isCurrentlyDisplayed = true,
                                content = {
                                    AccessibilityPermissionSection(
                                            hasStoragePermission = hasStoragePermission,
                                            hasOverlayPermission = hasOverlayPermission,
                                            hasBatteryOptimizationExemption =
                                                    hasBatteryOptimizationExemption,
                                            hasLocationPermission = hasLocationPermission,
                                            isAccessibilityProviderInstalled = isAccessibilityProviderInstalled,
                                            hasAccessibilityServiceEnabled =
                                                    hasAccessibilityServiceEnabled,
                                            isAccessibilityUpdateNeeded = isAccessibilityUpdateNeeded,
                                            isOperitTerminalInstalled = isOperitTerminalInstalled,
                                            onStoragePermissionClick = onStoragePermissionClick,
                                            onOverlayPermissionClick = onOverlayPermissionClick,
                                            onBatteryOptimizationClick = onBatteryOptimizationClick,
                                            onLocationPermissionClick = onLocationPermissionClick,
                                            onAccessibilityClick = onAccessibilityClick,
                                            onInstallAccessibilityProviderClick = onInstallAccessibilityProviderClick,
                                            onOperitTerminalClick = onOperitTerminalClick
                                    )
                                }
                        )
                    }
                    AndroidPermissionLevel.ADMIN -> {
                        PermissionSectionContainer(
                                isActive =
                                        preferredPermissionLevel.value ==
                                                AndroidPermissionLevel.ADMIN,
                                isCurrentlyDisplayed = true,
                                content = {
                                    AdminPermissionSection(
                                            hasStoragePermission = hasStoragePermission,
                                            hasOverlayPermission = hasOverlayPermission,
                                            hasBatteryOptimizationExemption =
                                                    hasBatteryOptimizationExemption,
                                            hasLocationPermission = hasLocationPermission,
                                            isOperitTerminalInstalled = isOperitTerminalInstalled,
                                            onStoragePermissionClick = onStoragePermissionClick,
                                            onOverlayPermissionClick = onOverlayPermissionClick,
                                            onBatteryOptimizationClick = onBatteryOptimizationClick,
                                            onLocationPermissionClick = onLocationPermissionClick,
                                            onOperitTerminalClick = onOperitTerminalClick
                                    )
                                }
                        )
                    }
                    AndroidPermissionLevel.DEBUGGER -> {
                        PermissionSectionContainer(
                                isActive =
                                        preferredPermissionLevel.value ==
                                                AndroidPermissionLevel.DEBUGGER,
                                isCurrentlyDisplayed = true,
                                content = {
                                    DebuggerPermissionSection(
                                            hasStoragePermission = hasStoragePermission,
                                            hasOverlayPermission = hasOverlayPermission,
                                            hasBatteryOptimizationExemption =
                                                    hasBatteryOptimizationExemption,
                                            hasLocationPermission = hasLocationPermission,
                                            isOperitTerminalInstalled = isOperitTerminalInstalled,
                                            isShizukuInstalled = isShizukuInstalled,
                                            isShizukuRunning = isShizukuRunning,
                                            hasShizukuPermission = hasShizukuPermission,
                                            onStoragePermissionClick = onStoragePermissionClick,
                                            onOverlayPermissionClick = onOverlayPermissionClick,
                                            onBatteryOptimizationClick = onBatteryOptimizationClick,
                                            onLocationPermissionClick = onLocationPermissionClick,
                                            onOperitTerminalClick = onOperitTerminalClick,
                                            onShizukuClick = onShizukuClick
                                    )
                                }
                        )
                    }
                    AndroidPermissionLevel.ROOT -> {
                        PermissionSectionContainer(
                                isActive =
                                        preferredPermissionLevel.value ==
                                                AndroidPermissionLevel.ROOT,
                                isCurrentlyDisplayed = true,
                                content = {
                                    RootPermissionSection(
                                            hasStoragePermission = hasStoragePermission,
                                            hasOverlayPermission = hasOverlayPermission,
                                            hasBatteryOptimizationExemption =
                                                    hasBatteryOptimizationExemption,
                                            hasLocationPermission = hasLocationPermission,
                                            isOperitTerminalInstalled = isOperitTerminalInstalled,
                                            isDeviceRooted = isDeviceRooted,
                                            hasRootAccess = hasRootAccess,
                                            onStoragePermissionClick = onStoragePermissionClick,
                                            onOverlayPermissionClick = onOverlayPermissionClick,
                                            onBatteryOptimizationClick = onBatteryOptimizationClick,
                                            onLocationPermissionClick = onLocationPermissionClick,
                                            onOperitTerminalClick = onOperitTerminalClick,
                                            onRootClick = onRootClick
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionLevelSelector(
        currentLevel: AndroidPermissionLevel,
        activeLevel: AndroidPermissionLevel?,
        onLevelSelected: (AndroidPermissionLevel) -> Unit
) {
    val levels = AndroidPermissionLevel.values()

    ScrollableTabRow(
            selectedTabIndex = currentLevel.ordinal,
            edgePadding = 0.dp,
            divider = {},
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = Color.Transparent,
            indicator = { tabPositions ->
                // Draw indicator under the selected tab
                if (tabPositions.isNotEmpty() && currentLevel.ordinal < tabPositions.size) {
                    Box(
                            modifier =
                                    Modifier.tabIndicatorOffset(tabPositions[currentLevel.ordinal])
                                            .height(2.dp)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clip(
                                                    RoundedCornerShape(
                                                            topStart = 1.dp,
                                                            topEnd = 1.dp
                                                    )
                                            )
                    )
                }
            }
    ) {
        levels.forEach { level ->
            val isSelected = level == currentLevel
            val isActive = level == activeLevel

            Tab(
                    selected = isSelected,
                    onClick = { onLevelSelected(level) },
                    text = {
                        Text(
                                text = level.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isActive ->
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.7f
                                                    )
                                            else ->
                                                    MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.7f
                                                    )
                                        }
                        )
                    },
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PermissionSectionContainer(
        isActive: Boolean,
        isCurrentlyDisplayed: Boolean,
        content: @Composable () -> Unit
) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .let {
                                when {
                                    // 当前活动权限级别用实线边框
                                    isActive ->
                                            it.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(8.dp)
                                            )
                                    // 仅查看的权限级别没有特殊边框
                                    else -> it
                                }
                            }
                            .padding(8.dp)
    ) { content() }
}

// 重新设计权限项，使其更现代和直观
@Composable
fun PermissionStatusItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
    val contentColor =
            if (isGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

    val statusText =
            if (isGranted) stringResource(R.string.status_granted)
            else stringResource(R.string.status_not_granted)

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态指示点
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(contentColor))

            Spacer(modifier = Modifier.width(8.dp))

            // 权限名称
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 状态文本
        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = contentColor)
    }
}

@Composable
private fun StandardPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isOperitTerminalInstalled: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onOperitTerminalClick: () -> Unit
) {
    Column {
        Text(
                text = stringResource(R.string.basic_permissions),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        // 使用Surface提供一个轻微的背景色，而不是独立的卡片
        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.storage_permission),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.overlay_permission),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.battery_optimization),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.location_permission),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.operit_terminal),
                        isGranted = isOperitTerminalInstalled,
                        onClick = onOperitTerminalClick
                )
            }
        }
    }
}

@Composable
private fun AccessibilityPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isAccessibilityProviderInstalled: Boolean, // 新增
        hasAccessibilityServiceEnabled: Boolean,
        isAccessibilityUpdateNeeded: Boolean,
        isOperitTerminalInstalled: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onAccessibilityClick: () -> Unit,
        onInstallAccessibilityProviderClick: () -> Unit, // 新增
        onOperitTerminalClick: () -> Unit
) {
    Column {
        Text(
                text = stringResource(R.string.basic_permissions),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.storage_permission),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.overlay_permission),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.battery_optimization),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.location_permission),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.operit_terminal),
                        isGranted = isOperitTerminalInstalled,
                        onClick = onOperitTerminalClick
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = stringResource(R.string.accessibility_permission),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                val isFullyEnabled =
                        isAccessibilityProviderInstalled && hasAccessibilityServiceEnabled
                val onClickAction =
                        when {
                            // 需要更新时，触发向导展开/收起
                            isAccessibilityUpdateNeeded -> onAccessibilityClick
                            // 未安装时，点击无效
                            !isAccessibilityProviderInstalled -> onInstallAccessibilityProviderClick
                            // 其他情况（已安装且无需更新），跳转到系统设置
                            else -> onAccessibilityClick
                        }

                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable(onClick = onClickAction)
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                                modifier =
                                        Modifier.size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (isFullyEnabled)
                                                                MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.error
                                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = stringResource(R.string.accessibility_service),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val statusText =
                            when {
                                !isAccessibilityProviderInstalled ->
                                        stringResource(R.string.status_not_installed)
                                !hasAccessibilityServiceEnabled ->
                                        stringResource(R.string.status_not_granted)
                                isAccessibilityUpdateNeeded ->
                                        stringResource(R.string.status_update_needed)
                                else -> stringResource(R.string.status_granted)
                            }

                    Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                    when {
                                        !isAccessibilityProviderInstalled ||
                                                !hasAccessibilityServiceEnabled ->
                                                MaterialTheme.colorScheme.error
                                        isAccessibilityUpdateNeeded -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isOperitTerminalInstalled: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onOperitTerminalClick: () -> Unit
) {
    Column {
        Text(
                text = stringResource(R.string.basic_permissions),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        // 添加不支持使用的提示卡片 - 移至顶部
        Surface(
                color = Color(0xFFFFF8E1), // 浅琥珀色背景
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB74D)) // 琥珀色边框
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFF9800), // 琥珀色图标
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                        text = stringResource(R.string.version_not_supported),
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                ),
                        color = Color(0xFFE65100) // 深琥珀色文字
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.storage_permission),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.overlay_permission),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.battery_optimization),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.location_permission),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.operit_terminal),
                        isGranted = isOperitTerminalInstalled,
                        onClick = onOperitTerminalClick
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = stringResource(R.string.admin_permission),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun DebuggerPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isOperitTerminalInstalled: Boolean,
        isShizukuInstalled: Boolean,
        isShizukuRunning: Boolean,
        hasShizukuPermission: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onOperitTerminalClick: () -> Unit,
        onShizukuClick: () -> Unit
) {
    // 获取当前上下文
    val context = LocalContext.current

    // 检查Shizuku是否需要更新
    val isShizukuUpdateNeeded = remember {
        try {
            com.ai.assistance.operit.core.tools.system.ShizukuInstaller.isShizukuUpdateNeeded(
                    context
            )
        } catch (e: Exception) {
            false
        }
    }

    Column {
        Text(
                text = stringResource(R.string.basic_permissions),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.storage_permission),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.overlay_permission),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.battery_optimization),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.location_permission),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.operit_terminal),
                        isGranted = isOperitTerminalInstalled,
                        onClick = onOperitTerminalClick
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = stringResource(R.string.debug_permission),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 自定义Shizuku状态项
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable(onClick = onShizukuClick)
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 状态指示点
                        Box(
                                modifier =
                                        Modifier.size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (isShizukuInstalled &&
                                                                        isShizukuRunning &&
                                                                        hasShizukuPermission
                                                        )
                                                                MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.error
                                                )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 权限名称
                        Text(
                                text = stringResource(R.string.shizuku_service),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 状态文本
                    val statusText =
                            when {
                                !isShizukuInstalled -> stringResource(R.string.status_not_installed)
                                !isShizukuRunning -> stringResource(R.string.status_not_running)
                                !hasShizukuPermission -> stringResource(R.string.status_not_granted)
                                isShizukuUpdateNeeded ->
                                        stringResource(R.string.status_update_needed)
                                else -> stringResource(R.string.status_granted)
                            }

                    Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                    when {
                                        !isShizukuInstalled ||
                                                !isShizukuRunning ||
                                                !hasShizukuPermission ->
                                                MaterialTheme.colorScheme.error
                                        isShizukuUpdateNeeded -> Color(0xFFFF9800) // 琥珀色表示待更新
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                    )
                }
            }
        }

    }
}

@Composable
private fun RootPermissionSection(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        isOperitTerminalInstalled: Boolean,
        isDeviceRooted: Boolean,
        hasRootAccess: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onOperitTerminalClick: () -> Unit,
        onRootClick: () -> Unit
) {
    Column {
        Text(
                text = stringResource(R.string.root_permission),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.storage_permission),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.overlay_permission),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.battery_optimization),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.location_permission),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )

                HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                PermissionStatusItem(
                        title = stringResource(R.string.operit_terminal),
                        isGranted = isOperitTerminalInstalled,
                        onClick = onOperitTerminalClick
                )
            }
        }

        // Root权限额外说明
        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = stringResource(R.string.root_access_permission),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
        )

        // Root权限状态
        Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PermissionStatusItem(
                        title = stringResource(R.string.root_access_permission),
                        isGranted = hasRootAccess,
                        onClick = onRootClick
                )
            }
        }

        // Root权限额外信息
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                        text =
                                if (hasRootAccess) stringResource(R.string.root_access_granted)
                                else if (isDeviceRooted)
                                        stringResource(R.string.device_rooted_request_permission)
                                else stringResource(R.string.device_not_rooted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// 获取权限级别的描述
@Composable
private fun getPermissionLevelDescription(level: AndroidPermissionLevel): String {
    return when (level) {
        AndroidPermissionLevel.STANDARD ->
                stringResource(id = R.string.permission_level_standard_full_desc)
        AndroidPermissionLevel.ACCESSIBILITY ->
                stringResource(id = R.string.permission_level_accessibility_full_desc)
        AndroidPermissionLevel.ADMIN ->
                stringResource(id = R.string.permission_level_admin_full_desc)
        AndroidPermissionLevel.DEBUGGER ->
                stringResource(id = R.string.permission_level_debugger_full_desc)
        AndroidPermissionLevel.ROOT -> stringResource(id = R.string.permission_level_root_full_desc)
    }
}

@Composable
private fun PermissionLevelVisualDescription(level: AndroidPermissionLevel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 根据不同权限级别显示不同的标题
        val title =
                when (level) {
                    AndroidPermissionLevel.STANDARD ->
                            stringResource(R.string.permission_level_standard)
                    AndroidPermissionLevel.ACCESSIBILITY ->
                            stringResource(R.string.permission_level_accessibility)
                    AndroidPermissionLevel.ADMIN -> stringResource(R.string.permission_level_admin)
                    AndroidPermissionLevel.DEBUGGER ->
                            stringResource(R.string.permission_level_debugger)
                    AndroidPermissionLevel.ROOT -> stringResource(R.string.permission_level_root)
                    null -> stringResource(R.string.permission_level_standard) // 默认标题
                }

        Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
        )

        // 权限描述文本
        val description =
                when (level) {
                    AndroidPermissionLevel.STANDARD ->
                            stringResource(R.string.permission_level_standard_desc)
                    AndroidPermissionLevel.ACCESSIBILITY ->
                            stringResource(R.string.permission_level_accessibility_desc)
                    AndroidPermissionLevel.ADMIN ->
                            stringResource(R.string.permission_level_admin_desc)
                    AndroidPermissionLevel.DEBUGGER ->
                            stringResource(R.string.permission_level_debugger_desc)
                    AndroidPermissionLevel.ROOT ->
                            stringResource(R.string.permission_level_root_desc)
                    null -> stringResource(R.string.permission_level_standard_desc) // 默认描述
                }

        Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
        )

        // 功能项网格
        FeatureGrid(level)
    }
}

@Composable
private fun FeatureGrid(level: AndroidPermissionLevel) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 在这里定义不同权限级别支持的功能
        val features =
                listOf(
                        context.getString(R.string.feature_overlay_window) to isFeatureSupported(level, true, true, true, true, true),
                        context.getString(R.string.feature_file_operations) to isFeatureSupported(level, true, true, true, true, true),
                        "Android/data" to isFeatureSupported(level, false, false, true, true, true),
                        "data/data" to isFeatureSupported(level, false, false, false, false, true),
                        context.getString(R.string.feature_screen_auto_click) to isFeatureSupported(level, false, true, true, true, true),
                        context.getString(R.string.feature_system_permission_modification) to isFeatureSupported(level, false, false, false, true, true),
                        context.getString(R.string.feature_termux_support) to isFeatureSupported(level, true, true, true, true, true),
                        context.getString(R.string.feature_run_js) to
                                (level == AndroidPermissionLevel.DEBUGGER ||
                                        level == AndroidPermissionLevel.ROOT),
                        context.getString(R.string.feature_plugin_market_mcp) to isFeatureSupported(level, true, true, true, true, true)
                )

        // 每行3个功能项
        val rows = features.chunked(3)
        rows.forEach { rowFeatures ->
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowFeatures.forEach { (feature, supported) ->
                    FeatureItem(
                            name = feature,
                            isSupported = supported,
                            modifier = Modifier.weight(1f)
                    )
                }
                // 如果一行不满3个，添加空白占位
                repeat(3 - rowFeatures.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 判断特定功能在给定权限级别下是否支持
 *
 * @param level 当前权限级别
 * @param inStandard 在标准权限下是否支持
 * @param inAccessibility 在无障碍权限下是否支持
 * @param inAdmin 在管理员权限下是否支持
 * @param inDebugger 在调试权限下是否支持
 * @param inRoot 在Root权限下是否支持
 * @return 是否支持该功能
 */
private fun isFeatureSupported(
        level: AndroidPermissionLevel?,
        inStandard: Boolean,
        inAccessibility: Boolean,
        inAdmin: Boolean,
        inDebugger: Boolean,
        inRoot: Boolean
): Boolean {
    return when (level) {
        AndroidPermissionLevel.STANDARD -> inStandard
        AndroidPermissionLevel.ACCESSIBILITY -> inAccessibility
        AndroidPermissionLevel.ADMIN -> inAdmin
        AndroidPermissionLevel.DEBUGGER -> inDebugger
        AndroidPermissionLevel.ROOT -> inRoot
        null -> inStandard
    }
}

@Composable
private fun FeatureItem(name: String, isSupported: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 图标背景
        Box(
                modifier =
                        Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(
                                        if (isSupported)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                                .border(
                                        width = 1.dp,
                                        color =
                                                if (isSupported)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.3f
                                                        ),
                                        shape = CircleShape
                                ),
                contentAlignment = Alignment.Center
        ) {
            // 根据功能名称显示不同的图标
            val context = LocalContext.current
            val icon =
                    when (name) {
                        context.getString(R.string.feature_overlay_window) -> Icons.Default.Web
                        context.getString(R.string.feature_file_operations) -> Icons.Default.Folder
                        "Android/data" -> Icons.Default.Storage
                        "data/data" -> Icons.Default.Storage
                        context.getString(R.string.feature_screen_auto_click) -> Icons.Default.TouchApp
                        context.getString(R.string.feature_system_permission_modification) -> Icons.Default.Settings
                        context.getString(R.string.feature_termux_support) -> Icons.Default.Terminal
                        context.getString(R.string.feature_run_js) -> Icons.Default.Code
                        context.getString(R.string.feature_plugin_market_mcp) -> Icons.Default.Store
                        else -> Icons.Default.CheckCircle
                    }

            Icon(
                    imageVector = if (isSupported) icon else Icons.Default.Lock,
                    contentDescription = if (isSupported) context.getString(R.string.supported) else context.getString(R.string.not_supported),
                    tint =
                            if (isSupported) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 功能名称
        Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color =
                        if (isSupported) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                maxLines = 1
        )
    }
}
