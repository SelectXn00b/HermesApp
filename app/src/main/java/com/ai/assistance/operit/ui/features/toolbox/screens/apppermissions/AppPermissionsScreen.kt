package com.ai.assistance.operit.ui.features.toolbox.screens.apppermissions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?,
        val installTime: Long,
        val isSystemApp: Boolean
)

data class PermissionInfo(
        val name: String,
        val description: String,
        val granted: Boolean,
        val dangerous: Boolean,
        val group: String,
        val rawName: String // 用于执行命令
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppPermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedAppPermissions by remember { mutableStateOf<List<PermissionInfo>>(emptyList()) }
    var isPermissionLoading by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    // 分组权限，按组显示
    val groupedPermissions =
            remember(selectedAppPermissions) { selectedAppPermissions.groupBy { it.group } }

    // 权限组颜色和图标
    val groupColors = remember {
        mapOf(
                "ACTIVITY_RECOGNITION" to Color(0xFF8D6E63),
                "CALENDAR" to Color(0xFF7986CB),
                "CALL_LOG" to Color(0xFFE57373),
                "CAMERA" to Color(0xFFBA68C8),
                "CONTACTS" to Color(0xFF4DB6AC),
                "LOCATION" to Color(0xFFFFB74D),
                "MICROPHONE" to Color(0xFF4FC3F7),
                "PHONE" to Color(0xFFFF8A65),
                "SENSORS" to Color(0xFF9CCC65),
                "SMS" to Color(0xFFFF8A65),
                "STORAGE" to Color(0xFF7E57C2),
                "OTHER_GRANTED" to Color(0xFF66BB6A),
                "OTHER_DENIED" to Color(0xFF78909C),
                "undefined" to Color(0xFF9E9E9E)
        )
    }

    val groupIcons = remember {
        mapOf(
                "ACTIVITY_RECOGNITION" to Icons.Default.DirectionsRun,
                "CALENDAR" to Icons.Default.DateRange,
                "CALL_LOG" to Icons.Default.Call,
                "CAMERA" to Icons.Default.PhotoCamera,
                "CONTACTS" to Icons.Default.Contacts,
                "LOCATION" to Icons.Default.LocationOn,
                "MICROPHONE" to Icons.Default.Mic,
                "PHONE" to Icons.Default.Phone,
                "SENSORS" to Icons.Default.Sensors,
                "SMS" to Icons.Default.Sms,
                "STORAGE" to Icons.Default.Folder,
                "OTHER_GRANTED" to Icons.Default.Check,
                "OTHER_DENIED" to Icons.Default.Block,
                "undefined" to Icons.Default.Info
        )
    }

    // 加载应用列表
    LaunchedEffect(Unit) {
        isLoading = true
        coroutineScope.launch {
            val apps = loadInstalledApps(packageManager)
            installedApps = apps
            isLoading = false
        }
    }

    // 过滤应用列表
    val filteredApps =
            remember(installedApps, searchQuery, showSystemApps) {
                installedApps
                        .filter {
                            (searchQuery.isEmpty() ||
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)) &&
                                    (showSystemApps || !it.isSystemApp)
                        }
                        .sortedBy { it.name }
            }

    // 查询应用权限函数
    fun loadAppPermissions(packageName: String) {
        isPermissionLoading = true
        coroutineScope.launch {
            try {
                val permissions = getAppPermissions(packageName, context)
                selectedAppPermissions = permissions
            } catch (e: Exception) {
                                            errorMessage = context.getString(R.string.toolbox_permissions_get_error, e.message ?: "")
                showError = true
            } finally {
                isPermissionLoading = false
            }
        }
    }

    // 修改应用权限函数
    fun togglePermission(permission: PermissionInfo) {
        val packageName = selectedApp?.packageName ?: return
        val action = if (permission.granted) "revoke" else "grant"

        coroutineScope.launch {
            try {
                val result =
                        withContext(Dispatchers.IO) {
                            AndroidShellExecutor.executeShellCommand(
                                    "pm $action $packageName ${permission.rawName}"
                            )
                        }

                if (result.success) {
                    // 刷新权限列表
                    loadAppPermissions(packageName)
                } else {
                                                errorMessage = context.getString(R.string.toolbox_permissions_modify_error, result.stderr)
                    showError = true
                }
            } catch (e: Exception) {
                                            errorMessage = context.getString(R.string.toolbox_permissions_modify_error, e.message ?: "")
                showError = true
            }
        }
    }

    // 重置应用权限
    fun resetAppPermissions() {
        val packageName = selectedApp?.packageName ?: return

        coroutineScope.launch {
            try {
                val result =
                        withContext(Dispatchers.IO) {
                            AndroidShellExecutor.executeShellCommand(
                                    "pm reset-permissions $packageName"
                            )
                        }

                if (result.success) {
                    // 刷新权限列表
                    loadAppPermissions(packageName)
                } else {
                                                errorMessage = context.getString(R.string.toolbox_permissions_reset_error, result.stderr)
                    showError = true
                }
            } catch (e: Exception) {
                                        errorMessage = context.getString(R.string.toolbox_permissions_reset_error, e.message ?: "")
                showError = true
            }
        }
    }

    CustomScaffold(
            // 移除顶部应用栏
            ) { paddingValues ->
        AnimatedContent(
                targetState = selectedApp,
                transitionSpec = {
                    if (targetState == null) {
                        slideInHorizontally { -it } + fadeIn() with
                                slideOutHorizontally { it } + fadeOut()
                    } else {
                        slideInHorizontally { it } + fadeIn() with
                                slideOutHorizontally { -it } + fadeOut()
                    }
                }
        ) { targetApp ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if (targetApp == null) {
                    // 应用列表视图
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 搜索栏和过滤器
                        Surface(
                                color = colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 12.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            placeholder = { Text(context.getString(R.string.file_manager_search_hint)) },
                                            leadingIcon = {
                                                Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = context.getString(R.string.search)
                                                )
                                            },
                                            trailingIcon = {
                                                if (searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchQuery = "" }) {
                                                        Icon(
                                                                Icons.Default.Clear,
                                                                contentDescription = context.getString(R.string.clear)
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            maxLines = 1,
                                            shape = RoundedCornerShape(24.dp),
                                            colors =
                                                    OutlinedTextFieldDefaults.colors(
                                                            focusedContainerColor =
                                                                    colorScheme.surfaceVariant.copy(
                                                                            alpha = 0.3f
                                                                    ),
                                                            unfocusedContainerColor =
                                                                    colorScheme.surfaceVariant.copy(
                                                                            alpha = 0.3f
                                                                    ),
                                                            unfocusedBorderColor =
                                                                    colorScheme.surfaceVariant.copy(
                                                                            alpha = 0.5f
                                                                    )
                                                    )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // 系统应用过滤开关，改进视觉样式
                                    Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier =
                                                    Modifier.clip(RoundedCornerShape(20.dp))
                                                            .clickable {
                                                                showSystemApps = !showSystemApps
                                                            }
                                                            .padding(
                                                                    horizontal = 8.dp,
                                                                    vertical = 4.dp
                                                            )
                                    ) {
                                        Checkbox(
                                                checked = showSystemApps,
                                                onCheckedChange = { showSystemApps = it },
                                                colors =
                                                        CheckboxDefaults.colors(
                                                                checkedColor = colorScheme.primary,
                                                                uncheckedColor = colorScheme.outline
                                                        )
                                        )
                                        Text(
                                                context.getString(R.string.file_manager_show_hidden),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colorScheme.onSurface
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                        visible = filteredApps.isNotEmpty() && !isLoading
                                ) {
                                    // 应用计数信息
                                    Text(
                                            text = context.getString(R.string.file_count, filteredApps.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.onSurfaceVariant,
                                            modifier =
                                                    Modifier.padding(start = 20.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }

                        if (isLoading) {
                            // 加载状态，增加加载动画和提示文字
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                            context.getString(R.string.file_manager_loading),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (filteredApps.isEmpty()) {
                            // 无搜索结果展示，改进空状态UI
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(72.dp),
                                            tint = colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                            text = context.getString(R.string.no_apps_found_anywhere),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                            text =
                                                    if (searchQuery.isNotEmpty()) context.getString(R.string.try_different_keywords)
                                                    else if (!showSystemApps) context.getString(R.string.try_show_system_apps)
                                                    else context.getString(R.string.no_apps_found_anywhere),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // 应用列表，添加动画效果
                            LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding =
                                            PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = filteredApps, key = { it.packageName }) { app ->
                                    AppItem(
                                            app = app,
                                            onClick = {
                                                selectedApp = app
                                                loadAppPermissions(app.packageName)
                                            }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 应用权限详情视图
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 添加顶部导航区域，包含返回按钮和应用信息
                        Surface(
                                color = colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 返回按钮
                                IconButton(
                                        onClick = { selectedApp = null },
                                        modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = context.getString(R.string.back),
                                            tint = colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 应用图标
                                selectedApp?.icon?.let { appIcon ->
                                    val bitmap = appIcon.toBitmap()
                                    Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = selectedApp?.name,
                                            modifier =
                                                    Modifier.size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                    colorScheme.surfaceVariant.copy(
                                                                            alpha = 0.3f
                                                                    )
                                                            )
                                                            .padding(4.dp)
                                    )
                                }
                                        ?: Icon(
                                                imageVector = Icons.Default.Android,
                                                contentDescription = selectedApp?.name,
                                                modifier = Modifier.size(40.dp),
                                                tint = colorScheme.primary
                                        )

                                Spacer(modifier = Modifier.width(12.dp))

                                // 应用名称和包名
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                            text = selectedApp?.name ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                            text = selectedApp?.packageName ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 重置权限按钮
                                FilledTonalIconButton(
                                        onClick = { resetAppPermissions() },
                                        modifier = Modifier.size(40.dp),
                                        colors =
                                                IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor =
                                                                colorScheme.secondaryContainer
                                                )
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = context.getString(R.string.toolbox_permissions_reset),
                                            tint = colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // 权限概览区域
                        AnimatedVisibility(
                                visible =
                                        !isPermissionLoading && selectedAppPermissions.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                        ) {
                            val totalPerms = selectedAppPermissions.size
                            val grantedPerms = selectedAppPermissions.count { it.granted }
                            val dangerousPerms = selectedAppPermissions.count { it.dangerous }

                            Card(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                            CardDefaults.cardColors(
                                                    containerColor =
                                                            colorScheme.primaryContainer.copy(
                                                                    alpha = 0.7f
                                                            )
                                            )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                            context.getString(R.string.toolbox_permissions_overview),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colorScheme.onPrimaryContainer
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // 总权限数
                                        PermissionStat(
                                                count = totalPerms,
                                                label = context.getString(R.string.toolbox_permissions_total),
                                                icon = Icons.Default.List,
                                                iconTint = colorScheme.onPrimaryContainer
                                        )

                                        // 已授权数
                                        PermissionStat(
                                                count = grantedPerms,
                                                label = context.getString(R.string.toolbox_permissions_granted),
                                                icon = Icons.Default.Check,
                                                iconTint = Color(0xFF4CAF50)
                                        )

                                        // 危险权限数
                                        PermissionStat(
                                                count = dangerousPerms,
                                                label = context.getString(R.string.toolbox_permissions_dangerous),
                                                icon = Icons.Default.Warning,
                                                iconTint = Color(0xFFFF9800)
                                        )
                                    }
                                }
                            }
                        }

                        // 权限列表
                        if (isPermissionLoading) {
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                            context.getString(R.string.toolbox_permissions_loading_info),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (selectedAppPermissions.isEmpty()) {
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(96.dp).padding(8.dp),
                                            tint = colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                            text = context.getString(R.string.toolbox_permissions_no_special),
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                            text = context.getString(R.string.toolbox_permissions_no_special_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding =
                                            PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // 按组显示权限
                                groupedPermissions.forEach { (group, permissions) ->
                                    item {
                                        // 权限组标题
                                        val groupName =
                                                when (group) {
                                                                    "ACTIVITY_RECOGNITION" -> context.getString(R.string.permission_group_activity)
                "CALENDAR" -> context.getString(R.string.permission_group_calendar)
                "CALL_LOG" -> context.getString(R.string.permission_group_call_log)
                "CAMERA" -> context.getString(R.string.permission_group_camera)
                "CONTACTS" -> context.getString(R.string.permission_group_contacts)
                "LOCATION" -> context.getString(R.string.permission_group_location)
                "MICROPHONE" -> context.getString(R.string.permission_group_microphone)
                "PHONE" -> context.getString(R.string.permission_group_phone)
                "SENSORS" -> context.getString(R.string.permission_group_sensors)
                "SMS" -> context.getString(R.string.permission_group_sms)
                "STORAGE" -> context.getString(R.string.permission_group_storage)
                "OTHER_GRANTED" -> context.getString(R.string.permission_group_other_granted)
                "OTHER_DENIED" -> context.getString(R.string.permission_group_other_denied)
                else -> context.getString(R.string.permission_group_other)
                                                }

                                        Surface(
                                                color = colorScheme.surface,
                                                contentColor = colorScheme.onSurface,
                                                tonalElevation = 1.dp,
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(top = 16.dp, bottom = 8.dp)
                                        ) {
                                            Row(
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .padding(
                                                                            vertical = 12.dp,
                                                                            horizontal = 16.dp
                                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 权限组图标
                                                Box(
                                                        modifier =
                                                                Modifier.size(36.dp)
                                                                        .background(
                                                                                groupColors[group]
                                                                                        ?.copy(
                                                                                                alpha =
                                                                                                        0.2f
                                                                                        )
                                                                                        ?: Color.Gray
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.2f
                                                                                                ),
                                                                                CircleShape
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                            imageVector = groupIcons[group]
                                                                            ?: Icons.Default
                                                                                    .Extension,
                                                            contentDescription = groupName,
                                                            tint = groupColors[group] ?: Color.Gray,
                                                            modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column {
                                                    Text(
                                                            text = groupName,
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                    )

                                                    Text(
                                                            text =
                                                                    "${permissions.size} ${context.getString(R.string.toolbox_permissions_count_suffix)}",
                                                            style =
                                                                    MaterialTheme.typography
                                                                            .bodySmall,
                                                            color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 该组的权限列表
                                    itemsIndexed(
                                            items = permissions,
                                            key = { _, permission -> permission.rawName }
                                    ) { index, permission ->
                                        PermissionItem(
                                                permission = permission,
                                                onToggle = {
                                                    // 修改权限切换函数，避免刷新整个列表
                                                    coroutineScope.launch {
                                                        val packageName =
                                                                selectedApp?.packageName
                                                                        ?: return@launch
                                                        val action =
                                                                if (permission.granted) "revoke"
                                                                else "grant"

                                                        try {
                                                            val result =
                                                                    withContext(Dispatchers.IO) {
                                                                        AndroidShellExecutor
                                                                                .executeShellCommand(
                                                                                        "pm $action $packageName ${permission.rawName}"
                                                                                )
                                                                    }

                                                            if (result.success) {
                                                                // 更新当前权限状态而不是整个列表
                                                                val updatedPermissions =
                                                                        selectedAppPermissions
                                                                                .toMutableList()
                                                                val permIndex =
                                                                        updatedPermissions
                                                                                .indexOfFirst {
                                                                                    it.rawName ==
                                                                                            permission
                                                                                                    .rawName
                                                                                }

                                                                if (permIndex != -1) {
                                                                    val updatedPerm =
                                                                            permission.copy(
                                                                                    granted =
                                                                                            !permission
                                                                                                    .granted
                                                                            )
                                                                    updatedPermissions[permIndex] =
                                                                            updatedPerm
                                                                    selectedAppPermissions =
                                                                            updatedPermissions
                                                                }
                                                            } else {
                                                                errorMessage =
                                                                        context.getString(R.string.toolbox_permissions_modify_error, result.stderr)
                                                                showError = true
                                                            }
                                                        } catch (e: Exception) {
                                                            errorMessage = context.getString(R.string.toolbox_permissions_modify_error, e.message ?: "")
                                                            showError = true
                                                        }
                                                    }
                                                },
                                                groupColor = groupColors[group] ?: Color.Gray
                                        )
                                    }
                                }

                                // 底部空间，确保最后一项可以完全滚动
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // 错误弹窗，改进UI
    if (showError && errorMessage != null) {
        AlertDialog(
                onDismissRequest = { showError = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.operation_failed))
                    }
                },
                text = { Text(errorMessage!!, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(
                            onClick = { showError = false },
                            colors =
                                    ButtonDefaults.textButtonColors(
                                            contentColor = colorScheme.primary
                                    )
                    ) { Text(context.getString(R.string.confirm)) }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = colorScheme.surface,
                iconContentColor = colorScheme.error
        )
    }
}

// 添加权限统计组件
@Composable
fun PermissionStat(count: Int, label: String, icon: ImageVector, iconTint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
                modifier =
                        Modifier.size(48.dp).background(iconTint.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
        )

        Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppItem(app: AppInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = colorScheme.surface,
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标，添加圆形背景和阴影效果
            Box(
                    modifier =
                            Modifier.size(52.dp)
                                    .padding(end = 8.dp)
                                    .background(
                                            color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = CircleShape
                                    )
                                    .padding(4.dp),
                    contentAlignment = Alignment.Center
            ) {
                val appIcon = app.icon
                if (appIcon != null) {
                    val bitmap = appIcon.toBitmap()
                    Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                } else {
                    Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = app.name,
                            modifier = Modifier.size(32.dp),
                            tint = colorScheme.primary
                    )
                }
            }

            // 应用信息，改进布局和文字样式
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                if (app.isSystemApp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                                modifier =
                                        Modifier.size(8.dp)
                                                .background(colorScheme.error, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                                                    text = context.getString(R.string.toolbox_permissions_system_apps),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // 查看权限按钮，使用更现代的图标按钮样式
            FilledIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(40.dp),
                    colors =
                            IconButtonDefaults.filledIconButtonColors(
                                    containerColor = colorScheme.primaryContainer
                            )
            ) {
                Icon(
                        imageVector = Icons.Default.Security,
                                                                    contentDescription = context.getString(R.string.toolbox_permissions_view),
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionItem(permission: PermissionInfo, onToggle: () -> Unit, groupColor: Color) {
    val context = LocalContext.current
    val animatedElevation by
            animateDpAsState(
                    targetValue = if (permission.granted) 2.dp else 0.dp,
                    label = "elevation animation"
            )

    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .shadow(
                                    elevation = animatedElevation,
                                    shape = RoundedCornerShape(12.dp)
                            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (permission.granted) colorScheme.surface
                                    else colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
    ) {
        Column {
            // 添加一个彩色条来指示权限组
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(4.dp)
                                    .background(
                                            color =
                                                    groupColor.copy(
                                                            alpha =
                                                                    if (permission.granted) 0.9f
                                                                    else 0.4f
                                                    )
                                    )
            )

            Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧显示权限状态图标
                Box(
                        modifier =
                                Modifier.size(40.dp)
                                        .background(
                                                color =
                                                        if (permission.granted)
                                                                groupColor.copy(alpha = 0.2f)
                                                        else colorScheme.surfaceVariant,
                                                shape = CircleShape
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    if (permission.dangerous) {
                        Icon(
                                imageVector = Icons.Default.Warning,
                                                                            contentDescription = context.getString(R.string.dangerous_permission),
                                tint =
                                        if (permission.granted) Color(0xFFFF9800)
                                        else Color(0xFFFF9800).copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                                imageVector =
                                        if (permission.granted) Icons.Default.Check
                                        else Icons.Default.Lock,
                                contentDescription = if (permission.granted) context.getString(R.string.granted) else context.getString(R.string.not_granted),
                                tint =
                                        if (permission.granted) colorScheme.primary
                                        else colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 权限信息
                Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                            text = permission.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color =
                                    if (permission.granted) colorScheme.onSurface
                                    else colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                            text = permission.description,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                    if (permission.granted) colorScheme.onSurfaceVariant
                                    else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                    )
                }

                // 权限开关
                Switch(
                        checked = permission.granted,
                        onCheckedChange = { onToggle() },
                        thumbContent =
                                if (permission.granted) {
                                    {
                                        Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                } else null,
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = colorScheme.primary,
                                        checkedTrackColor = colorScheme.primaryContainer,
                                        checkedBorderColor = colorScheme.primary,
                                        uncheckedThumbColor = colorScheme.surfaceVariant,
                                        uncheckedTrackColor =
                                                colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        uncheckedBorderColor = colorScheme.outline
                                )
                )
            }
        }
    }
}

// 获取已安装应用列表
private suspend fun loadInstalledApps(packageManager: PackageManager): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val apps = mutableListOf<AppInfo>()

            try {
                val flags = PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES
                val installedApps = packageManager.getInstalledApplications(flags)

                for (appInfo in installedApps) {
                    try {
                        val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        apps.add(
                                AppInfo(
                                        name =
                                                packageManager
                                                        .getApplicationLabel(appInfo)
                                                        .toString(),
                                        packageName = appInfo.packageName,
                                        icon =
                                                packageManager.getApplicationIcon(
                                                        appInfo.packageName
                                                ),
                                        installTime = packageInfo.firstInstallTime,
                                        isSystemApp = isSystemApp
                                )
                        )
                    } catch (e: Exception) {
                        // 忽略异常应用
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext apps
        }

// 获取应用权限列表
private suspend fun getAppPermissions(packageName: String, context: Context): List<PermissionInfo> =
        withContext(Dispatchers.IO) {
            val permissions = mutableListOf<PermissionInfo>()

            try {
                // 获取应用请求的权限 - 无需预过滤，直接获取完整输出
                val packageInfoResult =
                        AndroidShellExecutor.executeShellCommand("dumpsys package $packageName")

                // 获取所有已授予的权限 - 单独提取
                val grantedPermsResult =
                        AndroidShellExecutor.executeShellCommand(
                                "dumpsys package $packageName | grep -E \"granted=true|:granted=true\""
                        )

                // 解析请求的权限（应用清单中声明的权限）
                val requestedPerms = mutableSetOf<String>()
                val output = packageInfoResult.stdout

                // 提取所有已申请的权限
                // 先尝试找到"requested permissions:"部分
                val requestedSection = extractSectionContent(output, "requested permissions:")
                if (requestedSection.isNotEmpty()) {
                    // 从请求权限部分中提取权限
                    extractPermissionsFromSection(requestedSection, requestedPerms)
                }

                // 然后尝试找到"install permissions:"部分
                val installSection = extractSectionContent(output, "install permissions:")
                if (installSection.isNotEmpty()) {
                    // 从安装权限部分中提取权限
                    extractPermissionsFromSection(installSection, requestedPerms)
                }

                // 最后尝试找到"runtime permissions:"部分
                val runtimeSection = extractSectionContent(output, "runtime permissions:")
                if (runtimeSection.isNotEmpty()) {
                    // 从运行时权限部分中提取权限
                    extractPermissionsFromSection(runtimeSection, requestedPerms)
                }

                // 如果依然没找到权限，使用正则表达式匹配
                if (requestedPerms.isEmpty()) {
                    val permRegex =
                            "(android\\.permission\\.[\\w\\.]+|permission\\.[\\w\\.]+)".toRegex()
                    val allMatches = permRegex.findAll(output)
                    allMatches.forEach { match ->
                        // 排除包含 "uses-permission:" 的行，这些通常是其他应用的引用
                        if (!match.groups[0]!!.value.contains("uses-permission:")) {
                            requestedPerms.add(match.groups[0]!!.value)
                        }
                    }
                }

                // 解析已授予的权限
                val grantedPerms = mutableSetOf<String>()
                val grantedLines = grantedPermsResult.stdout.split("\n")

                // 使用新的正则表达式提取已授权权限
                for (line in grantedLines) {
                    // 处理 " permission.XXX: granted=true" 格式
                    val permMatch =
                            "(android\\.permission\\.[\\w\\.]+|permission\\.[\\w\\.]+)"
                                    .toRegex()
                                    .find(line)
                    permMatch?.value?.let { grantedPerms.add(it) }
                }

                // 检查 grantedPermissions 部分 - 这是授权权限的更可靠指示
                val grantedPermissionsSection = extractSectionContent(output, "grantedPermissions:")
                if (grantedPermissionsSection.isNotEmpty()) {
                    val lines = grantedPermissionsSection.split("\n")
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("android.permission.") ||
                                        trimmed.startsWith("permission.")
                        ) {
                            grantedPerms.add(trimmed)
                        }
                    }
                }

                // 如果在请求权限中没找到，但在授予权限中找到，添加到请求权限中
                if (requestedPerms.isEmpty() && grantedPerms.isNotEmpty()) {
                    requestedPerms.addAll(grantedPerms)
                }

                // 处理重要权限
                val importantPermGroups =
                        mapOf(
                                "android.permission.CAMERA" to "CAMERA",
                                "android.permission.READ_CONTACTS" to "CONTACTS",
                                "android.permission.WRITE_CONTACTS" to "CONTACTS",
                                "android.permission.GET_ACCOUNTS" to "CONTACTS",
                                "android.permission.ACCESS_FINE_LOCATION" to "LOCATION",
                                "android.permission.ACCESS_COARSE_LOCATION" to "LOCATION",
                                "android.permission.ACCESS_BACKGROUND_LOCATION" to "LOCATION",
                                "android.permission.READ_CALL_LOG" to "CALL_LOG",
                                "android.permission.WRITE_CALL_LOG" to "CALL_LOG",
                                "android.permission.PROCESS_OUTGOING_CALLS" to "CALL_LOG",
                                "android.permission.READ_PHONE_STATE" to "PHONE",
                                "android.permission.READ_PHONE_NUMBERS" to "PHONE",
                                "android.permission.CALL_PHONE" to "PHONE",
                                "android.permission.ANSWER_PHONE_CALLS" to "PHONE",
                                "android.permission.ADD_VOICEMAIL" to "PHONE",
                                "android.permission.USE_SIP" to "PHONE",
                                "android.permission.ACCEPT_HANDOVER" to "PHONE",
                                "android.permission.BODY_SENSORS" to "SENSORS",
                                "android.permission.BODY_SENSORS_BACKGROUND" to "SENSORS",
                                "android.permission.ACTIVITY_RECOGNITION" to "ACTIVITY_RECOGNITION",
                                "android.permission.READ_CALENDAR" to "CALENDAR",
                                "android.permission.WRITE_CALENDAR" to "CALENDAR",
                                "android.permission.READ_EXTERNAL_STORAGE" to "STORAGE",
                                "android.permission.WRITE_EXTERNAL_STORAGE" to "STORAGE",
                                "android.permission.MANAGE_EXTERNAL_STORAGE" to "STORAGE",
                                "android.permission.READ_MEDIA_IMAGES" to "STORAGE",
                                "android.permission.READ_MEDIA_VIDEO" to "STORAGE",
                                "android.permission.READ_MEDIA_AUDIO" to "STORAGE",
                                "android.permission.RECORD_AUDIO" to "MICROPHONE",
                                "android.permission.SEND_SMS" to "SMS",
                                "android.permission.RECEIVE_SMS" to "SMS",
                                "android.permission.READ_SMS" to "SMS",
                                "android.permission.RECEIVE_WAP_PUSH" to "SMS",
                                "android.permission.RECEIVE_MMS" to "SMS"
                        )

                // 权限名称友好显示
                val permissionDisplayNames: Map<String, String> =
                        mapOf(
                                "android.permission.CAMERA" to context.getString(R.string.perm_name_camera),
                                "android.permission.READ_CONTACTS" to context.getString(R.string.perm_name_read_contacts),
                                "android.permission.WRITE_CONTACTS" to context.getString(R.string.perm_name_write_contacts),
                                "android.permission.GET_ACCOUNTS" to context.getString(R.string.perm_name_get_accounts),
                                "android.permission.ACCESS_FINE_LOCATION" to context.getString(R.string.perm_name_access_fine_location),
                                "android.permission.ACCESS_COARSE_LOCATION" to context.getString(R.string.perm_name_access_coarse_location),
                                "android.permission.ACCESS_BACKGROUND_LOCATION" to context.getString(R.string.perm_name_access_background_location),
                                "android.permission.READ_CALL_LOG" to context.getString(R.string.perm_name_read_call_log),
                                "android.permission.WRITE_CALL_LOG" to context.getString(R.string.perm_name_write_call_log),
                                "android.permission.PROCESS_OUTGOING_CALLS" to context.getString(R.string.perm_name_process_outgoing_calls),
                                "android.permission.READ_PHONE_STATE" to context.getString(R.string.perm_name_read_phone_state),
                                "android.permission.READ_PHONE_NUMBERS" to context.getString(R.string.perm_name_read_phone_numbers),
                                "android.permission.CALL_PHONE" to context.getString(R.string.perm_name_call_phone),
                                "android.permission.ANSWER_PHONE_CALLS" to context.getString(R.string.perm_name_answer_phone_calls),
                                "android.permission.ADD_VOICEMAIL" to context.getString(R.string.perm_name_add_voicemail),
                                "android.permission.USE_SIP" to context.getString(R.string.perm_name_use_sip),
                                "android.permission.ACCEPT_HANDOVER" to context.getString(R.string.perm_name_accept_handover),
                                "android.permission.BODY_SENSORS" to context.getString(R.string.perm_name_body_sensors),
                                "android.permission.BODY_SENSORS_BACKGROUND" to context.getString(R.string.perm_name_body_sensors_background),
                                "android.permission.ACTIVITY_RECOGNITION" to context.getString(R.string.perm_name_activity_recognition),
                                "android.permission.READ_CALENDAR" to context.getString(R.string.perm_name_read_calendar),
                                "android.permission.WRITE_CALENDAR" to context.getString(R.string.perm_name_write_calendar),
                                "android.permission.READ_EXTERNAL_STORAGE" to context.getString(R.string.perm_name_read_external_storage),
                                "android.permission.WRITE_EXTERNAL_STORAGE" to context.getString(R.string.perm_name_write_external_storage),
                                "android.permission.MANAGE_EXTERNAL_STORAGE" to context.getString(R.string.perm_name_manage_external_storage),
                                "android.permission.READ_MEDIA_IMAGES" to context.getString(R.string.perm_name_read_media_images),
                                "android.permission.READ_MEDIA_VIDEO" to context.getString(R.string.perm_name_read_media_video),
                                "android.permission.READ_MEDIA_AUDIO" to context.getString(R.string.perm_name_read_media_audio),
                                "android.permission.RECORD_AUDIO" to context.getString(R.string.perm_name_record_audio),
                                "android.permission.SEND_SMS" to context.getString(R.string.perm_name_send_sms),
                                "android.permission.RECEIVE_SMS" to context.getString(R.string.perm_name_receive_sms),
                                "android.permission.READ_SMS" to context.getString(R.string.perm_name_read_sms),
                                "android.permission.RECEIVE_WAP_PUSH" to context.getString(R.string.perm_name_receive_wap_push),
                                "android.permission.RECEIVE_MMS" to context.getString(R.string.perm_name_receive_mms)
                        )

                // 权限描述
                val permissionDescriptions: Map<String, String> =
                        mapOf(
                                "android.permission.CAMERA" to context.getString(R.string.perm_desc_camera),
                                "android.permission.READ_CONTACTS" to context.getString(R.string.perm_desc_read_contacts),
                                "android.permission.WRITE_CONTACTS" to context.getString(R.string.perm_desc_write_contacts),
                                "android.permission.GET_ACCOUNTS" to context.getString(R.string.perm_desc_get_accounts),
                                "android.permission.ACCESS_FINE_LOCATION" to context.getString(R.string.perm_desc_access_fine_location),
                                "android.permission.ACCESS_COARSE_LOCATION" to context.getString(R.string.perm_desc_access_coarse_location),
                                "android.permission.ACCESS_BACKGROUND_LOCATION" to context.getString(R.string.perm_desc_access_background_location),
                                "android.permission.READ_CALL_LOG" to context.getString(R.string.perm_desc_read_call_log),
                                "android.permission.WRITE_CALL_LOG" to context.getString(R.string.perm_desc_write_call_log),
                                "android.permission.PROCESS_OUTGOING_CALLS" to context.getString(R.string.perm_desc_process_outgoing_calls),
                                "android.permission.READ_PHONE_STATE" to context.getString(R.string.perm_desc_read_phone_state),
                                "android.permission.READ_PHONE_NUMBERS" to context.getString(R.string.perm_desc_read_phone_numbers),
                                "android.permission.CALL_PHONE" to context.getString(R.string.perm_desc_call_phone),
                                "android.permission.ANSWER_PHONE_CALLS" to context.getString(R.string.perm_desc_answer_phone_calls),
                                "android.permission.ADD_VOICEMAIL" to context.getString(R.string.perm_desc_add_voicemail),
                                "android.permission.USE_SIP" to context.getString(R.string.perm_desc_use_sip),
                                "android.permission.ACCEPT_HANDOVER" to context.getString(R.string.perm_desc_accept_handover),
                                "android.permission.BODY_SENSORS" to context.getString(R.string.perm_desc_body_sensors),
                                "android.permission.BODY_SENSORS_BACKGROUND" to context.getString(R.string.perm_desc_body_sensors_background),
                                "android.permission.ACTIVITY_RECOGNITION" to context.getString(R.string.perm_desc_activity_recognition),
                                "android.permission.READ_CALENDAR" to context.getString(R.string.perm_desc_read_calendar),
                                "android.permission.WRITE_CALENDAR" to context.getString(R.string.perm_desc_write_calendar),
                                "android.permission.READ_EXTERNAL_STORAGE" to context.getString(R.string.perm_desc_read_external_storage),
                                "android.permission.WRITE_EXTERNAL_STORAGE" to context.getString(R.string.perm_desc_write_external_storage),
                                "android.permission.MANAGE_EXTERNAL_STORAGE" to context.getString(R.string.perm_desc_manage_external_storage),
                                "android.permission.READ_MEDIA_IMAGES" to context.getString(R.string.perm_desc_read_media_images),
                                "android.permission.READ_MEDIA_VIDEO" to context.getString(R.string.perm_desc_read_media_video),
                                "android.permission.READ_MEDIA_AUDIO" to context.getString(R.string.perm_desc_read_media_audio),
                                "android.permission.RECORD_AUDIO" to context.getString(R.string.perm_desc_record_audio),
                                "android.permission.SEND_SMS" to context.getString(R.string.perm_desc_send_sms),
                                "android.permission.RECEIVE_SMS" to context.getString(R.string.perm_desc_receive_sms),
                                "android.permission.READ_SMS" to context.getString(R.string.perm_desc_read_sms),
                                "android.permission.RECEIVE_WAP_PUSH" to context.getString(R.string.perm_desc_receive_wap_push),
                                "android.permission.RECEIVE_MMS" to context.getString(R.string.perm_desc_receive_mms)
                        )

                // 处理所有重要权限 - 首先添加在importantPermGroups中且被请求的权限
                for ((permName, group) in importantPermGroups) {
                    if (requestedPerms.contains(permName)) {
                        val displayName =
                                permissionDisplayNames[permName] ?: permName.substringAfterLast(".")
                        val description = permissionDescriptions[permName] ?: context.getString(R.string.perm_default_desc)
                        val isGranted = grantedPerms.contains(permName)

                        permissions.add(
                                PermissionInfo(
                                        name = displayName,
                                        description = description,
                                        granted = isGranted,
                                        dangerous = true,
                                        group = group,
                                        rawName = permName
                                )
                        )
                    }
                }

                // 添加其他请求的权限 - 这些将显示在"其他权限"类别中
                val processedPerms = permissions.map { it.rawName }.toSet()

                // 首先添加已授予但不在importantPermGroups中的权限
                for (permName in grantedPerms) {
                    if (permName !in processedPerms &&
                                    (permName.startsWith("android.permission.") ||
                                            permName.startsWith("permission.")) &&
                                    !importantPermGroups.containsKey(permName)
                    ) {

                        val displayName = permName.substringAfterLast(".")

                        permissions.add(
                                PermissionInfo(
                                        name = displayName,
                                        description = context.getString(R.string.perm_default_desc),
                                        granted = true,
                                        dangerous = false,
                                        group = "OTHER_GRANTED",
                                        rawName = permName
                                )
                        )
                    }
                }

                // 然后添加已请求但未授予且不在importantPermGroups中的权限
                val allProcessedPerms = permissions.map { it.rawName }.toSet()
                for (permName in requestedPerms) {
                    if (permName !in allProcessedPerms &&
                                    (permName.startsWith("android.permission.") ||
                                            permName.startsWith("permission.")) &&
                                    !importantPermGroups.containsKey(permName) &&
                                    !grantedPerms.contains(permName)
                    ) {

                        val displayName = permName.substringAfterLast(".")

                        permissions.add(
                                PermissionInfo(
                                        name = displayName,
                                        description = context.getString(R.string.perm_default_desc),
                                        granted = false,
                                        dangerous = false,
                                        group = "OTHER_DENIED",
                                        rawName = permName
                                )
                        )
                    }
                }

                // 添加调试信息，如果没找到任何权限
                if (permissions.isEmpty()) {
                    // 添加一个特殊权限来显示调试信息
                    permissions.add(
                            PermissionInfo(
                                    name = context.getString(R.string.debug_info),
                                    description =
                                            context.getString(
                                                    R.string.requested_permissions_count,
                                                    requestedPerms.size,
                                                    grantedPerms.size
                                            ) +
                                                    "\n" +
                                                    context.getString(
                                                            R.string.requested_permissions_list,
                                                            requestedPerms.joinToString(", ")
                                                    ) +
                                                    "\n" +
                                                    context.getString(
                                                            R.string.granted_permissions_list,
                                                            grantedPerms.joinToString(", ")
                                                    ),
                                    granted = false,
                                    dangerous = false,
                                    group = "undefined",
                                    rawName = "debug.info"
                            )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 添加错误信息作为权限显示
                permissions.add(
                        PermissionInfo(
                                name = context.getString(R.string.error_info),
                                description = context.getString(
                                        R.string.permission_error_desc,
                                        e.message ?: ""
                                ),
                                granted = false,
                                dangerous = false,
                                group = "undefined",
                                rawName = "error.info"
                        )
                )
            }

            // 按组排序，已授权的权限在前
            return@withContext permissions.sortedWith(
                    compareBy(
                            { if (it.group == "undefined") 1 else 0 }, // 确定的权限组在前
                            { !it.granted }, // 已授权在前
                            { it.group }, // 按权限组排序
                            { it.name } // 最后按名称排序
                    )
            )
        }

// 从完整输出中提取特定段落的内容
private fun extractSectionContent(output: String, sectionHeader: String): String {
    val startIndex = output.indexOf(sectionHeader)
    if (startIndex == -1) return ""

    // 找到段落的开始
    val sectionStart = startIndex + sectionHeader.length

    // 找到下一个可能的段落开始
    val possibleNextSections =
            listOf(
                    "grantedPermissions:",
                    "runtime permissions:",
                    "install permissions:",
                    "requested permissions:",
                    "User 0:",
                    "Package ["
            )

    // 找到最近的下一个部分
    var endIndex = output.length
    for (nextSection in possibleNextSections) {
        val nextIndex = output.indexOf(nextSection, sectionStart)
        if (nextIndex != -1 && nextIndex < endIndex) {
            endIndex = nextIndex
        }
    }

    return output.substring(sectionStart, endIndex).trim()
}

// 从段落中提取权限
private fun extractPermissionsFromSection(section: String, permissions: MutableSet<String>) {
    val lines = section.split("\n")
    for (line in lines) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) continue

        if (trimmedLine.startsWith("android.permission.") || trimmedLine.startsWith("permission.")
        ) {
            // 清理权限名称，删除可能的额外部分，如": granted=true"
            val permEnd = trimmedLine.indexOf(":")
            val permName = if (permEnd > 0) trimmedLine.substring(0, permEnd) else trimmedLine
            permissions.add(permName.trim())
        }
    }
}
