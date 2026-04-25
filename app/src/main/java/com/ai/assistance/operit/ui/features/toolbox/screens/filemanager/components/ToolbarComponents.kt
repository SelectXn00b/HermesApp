package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

/**
 * 顶部工具栏
 */
@Composable
fun FileManagerToolbar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onZoomIn: (Boolean) -> Boolean,
    onZoomOut: (Boolean) -> Boolean,
    onToggleMultiSelect: () -> Unit,
    onPaste: () -> Unit,
    clipboardEmpty: Boolean,
    displayMode: DisplayMode,
    onChangeDisplayMode: () -> Unit,
    onShowSearchDialog: () -> Unit,
    isSearching: Boolean,
    onExitSearch: () -> Unit,
    onNewFolder: () -> Unit,
    isMultiSelectMode: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column {
            // 导航按钮行 - 可水平滚动
            Row(modifier = Modifier.fillMaxWidth()) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 前进按钮
                    IconButton(onClick = { /* 前进功能 */ }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 向上按钮
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 刷新按钮
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 分隔线
                    HorizontalDivider(
                        modifier = Modifier.height(24.dp).width(1.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 缩小按钮
                    IconButton(
                        onClick = { onZoomOut(true) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 放大按钮
                    IconButton(
                        onClick = { onZoomIn(true) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 分隔线
                    HorizontalDivider(
                        modifier = Modifier.height(24.dp).width(1.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 多选模式切换按钮
                    IconButton(
                        onClick = onToggleMultiSelect,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 粘贴按钮
                    IconButton(
                        onClick = onPaste,
                        modifier = Modifier.size(36.dp),
                        enabled = !clipboardEmpty
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = if (!clipboardEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 分隔线
                    HorizontalDivider(
                        modifier = Modifier.height(24.dp).width(1.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 显示模式选择按钮
                    IconButton(
                        onClick = onChangeDisplayMode,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = when (displayMode) {
                                DisplayMode.SINGLE_COLUMN -> Icons.AutoMirrored.Filled.ViewList
                                DisplayMode.TWO_COLUMNS -> Icons.Default.ViewModule
                                DisplayMode.THREE_COLUMNS -> Icons.Default.ViewComfy
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 分隔线
                    HorizontalDivider(
                        modifier = Modifier.height(24.dp).width(1.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 搜索按钮
                    IconButton(
                        onClick = onShowSearchDialog,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 返回按钮 - 在搜索模式下显示
                    if (isSearching) {
                        IconButton(
                            onClick = onExitSearch,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 新建文件夹按钮
                    IconButton(
                        onClick = onNewFolder,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 路径导航栏
 */
@Composable
fun PathNavigationBar(
    currentPath: String,
    onNavigateToPath: (String) -> Unit = {}
) {
    var isEditing by remember { mutableStateOf(false) }
    var editablePath by remember(currentPath) { mutableStateOf(currentPath) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isEditing) {
                // 编辑模式下显示TextField
                TextField(
                    value = editablePath,
                    onValueChange = { editablePath = it },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            isEditing = false
                            if (editablePath.isNotEmpty()) {
                                onNavigateToPath(editablePath)
                            }
                        }
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            } else {
                // 非编辑模式下显示可点击的Text
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isEditing = true
                            editablePath = currentPath
                        }
                )
            }

            // 添加确认按钮，仅在编辑模式下显示
            if (isEditing) {
                IconButton(
                    onClick = {
                        isEditing = false
                        if (editablePath.isNotEmpty()) {
                            onNavigateToPath(editablePath)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 标签栏
 */
@Composable
fun FileManagerTabRow(
    tabs: List<com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.TabItem>,
    activeTabIndex: Int,
    onSwitchTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onAddTab: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollableTabRow(
                selectedTabIndex = activeTabIndex,
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f),
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty() && activeTabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabIndex]),
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == activeTabIndex,
                        onClick = { onSwitchTab(index) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (index == activeTabIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )

                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (index == activeTabIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (tabs.size > 1) {
                                    IconButton(
                                        onClick = { onCloseTab(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (index == activeTabIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // 添加新标签按钮
            IconButton(onClick = onAddTab, modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 状态栏
 */
@Composable
fun StatusBar(
    fileCount: Int,
    selectedFiles: List<com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem>,
    selectedFile: com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem?,
    isMultiSelectMode: Boolean,
    onExitMultiSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Text(
                    text = stringResource(R.string.files_selected, selectedFiles.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onExitMultiSelect) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.exit_multi_select),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.file_count, fileCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                selectedFile?.let { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIcon(file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
} 