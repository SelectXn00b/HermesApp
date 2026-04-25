package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import NewFolderDialog
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.DisplayMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel.FileManagerViewModel
import com.ai.assistance.operit.data.preferences.ApiPreferences
import java.io.File
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import kotlinx.coroutines.launch
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.withTimeoutOrNull

/** 文件管理器屏幕 */
@Composable
fun FileManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel = remember { FileManagerViewModel(context) }
    val toolHandler = AIToolHandler.getInstance(context)

    val scope = rememberCoroutineScope()

    var pendingRepoBookmarkUri by remember { mutableStateOf<Uri?>(null) }
    var repoBookmarkNameInput by remember { mutableStateOf("") }
    var showRepoBookmarkNameDialog by remember { mutableStateOf(false) }
    var repoBookmarkNameError by remember { mutableStateOf<String?>(null) }

    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())

    fun querySafBookmarkDisplayName(uri: Uri): String {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0 && !cursor.isNull(idx)) {
                    cursor.getString(idx)
                } else {
                    null
                }
            } ?: uri.toString()
        } catch (_: Exception) {
            uri.toString()
        }
    }

    fun queryRepoBookmarkName(uri: Uri): String {
        fun normalizeName(raw: String): String {
            return raw.trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(Regex("\\s+"), "_")
                .ifBlank { "repo" }
        }

        val providerLabel =
            runCatching {
                val authority = uri.authority ?: return@runCatching null
                val provider = context.packageManager.resolveContentProvider(authority, 0)
                provider?.applicationInfo?.loadLabel(context.packageManager)?.toString()?.trim()
            }.getOrNull()

        val raw = providerLabel?.takeIf { it.isNotBlank() } ?: uri.authority ?: "repo"
        return normalizeName(raw)
    }

    val addSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            pendingRepoBookmarkUri = uri
            repoBookmarkNameInput = queryRepoBookmarkName(uri)
            showRepoBookmarkNameDialog = true
        }
    }

    if (showRepoBookmarkNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRepoBookmarkNameDialog = false
                pendingRepoBookmarkUri = null
                repoBookmarkNameError = null
            },
            title = { Text(stringResource(R.string.repo_bookmark_name)) },
            text = {
                TextField(
                    value = repoBookmarkNameInput,
                    onValueChange = {
                        repoBookmarkNameInput = it
                        repoBookmarkNameError = null
                    },
                    label = { Text(stringResource(R.string.repo_bookmark_name_label)) },
                    singleLine = true,
                    isError = repoBookmarkNameError != null,
                    supportingText = {
                        repoBookmarkNameError?.let { Text(it) }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRepoBookmarkUri
                        val name = repoBookmarkNameInput.trim()
                        if (uri == null) {
                            showRepoBookmarkNameDialog = false
                            pendingRepoBookmarkUri = null
                            repoBookmarkNameError = null
                            return@TextButton
                        }

                        if (name.isEmpty()) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_empty)
                            return@TextButton
                        }

                        val nameExists = safBookmarks.any {
                            it.uri != uri.toString() && it.name.equals(name, ignoreCase = true)
                        }
                        if (nameExists) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_exists)
                            return@TextButton
                        }

                        scope.launch {
                            apiPreferences.addSafBookmark(uri.toString(), name)
                        }

                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(context.getString(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(context.getString(android.R.string.cancel)) }
            }
        )
    }

    // 为当前目录创建LazyListState
    val listState = rememberLazyListState()

    // 当前可见的第一个项目的索引
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 文件列表项处理函数
    val onItemClick: (FileItem) -> Unit = { file ->
        if (viewModel.isMultiSelectMode) {
            // 多选模式下，切换文件选择状态
            if (viewModel.selectedFiles.contains(file)) {
                viewModel.selectedFiles.remove(file)
            } else {
                viewModel.selectedFiles.add(file)
            }
        } else {
            // 单选模式下，如果是目录则导航到该目录，否则选中文件
            if (file.isDirectory) {
                viewModel.navigateToDirectory(file)
            } else {
                viewModel.selectedFile = file
            }
        }
    }

    val onItemLongClick: (FileItem) -> Unit = { file ->
        if (viewModel.isMultiSelectMode) {
            if (viewModel.selectedFiles.contains(file)) {
                viewModel.showBottomActionMenu = true
            } else {
                viewModel.selectedFiles.add(file)
            }
        } else {
            viewModel.contextMenuFile = file
            viewModel.showBottomActionMenu = true
        }
    }

    // 监听滚动位置变化，保存到scrollPositions
    LaunchedEffect(firstVisibleItemIndex) {
        if (viewModel.files.isNotEmpty() && viewModel.pendingScrollPosition == null) {
            viewModel.scrollPositions[viewModel.currentPath] = firstVisibleItemIndex
        }
    }

    // 加载当前目录内容
    LaunchedEffect(viewModel.currentPath) {
        val currentPath = viewModel.currentPath
        viewModel.loadCurrentDirectory(currentPath)
    }

    // 主界面
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部工具栏
            FileManagerToolbar(
                    currentPath = viewModel.currentPath,
                    onNavigateUp = { viewModel.navigateUp() },
                    onRefresh = { viewModel.loadCurrentDirectory() },
                    onZoomIn = { canZoom: Boolean ->
                        if (canZoom && viewModel.itemSize < viewModel.maxItemSize) {
                            viewModel.itemSize += viewModel.itemSizeStep
                            true
                        } else false
                    },
                    onZoomOut = { canZoom: Boolean ->
                        if (canZoom && viewModel.itemSize > viewModel.minItemSize) {
                            viewModel.itemSize -= viewModel.itemSizeStep
                            true
                        } else false
                    },
                    onToggleMultiSelect = {
                        if (viewModel.isMultiSelectMode) {
                            viewModel.isMultiSelectMode = false
                            viewModel.selectedFiles.clear()
                        } else {
                            viewModel.isMultiSelectMode = true
                            viewModel.selectedFiles.clear()
                        }
                    },
                    onPaste = { viewModel.pasteFiles() },
                    clipboardEmpty = viewModel.clipboardFiles.isEmpty(),
                    displayMode = viewModel.displayMode,
                    onChangeDisplayMode = {
                        viewModel.displayMode =
                                when (viewModel.displayMode) {
                                    DisplayMode.SINGLE_COLUMN -> DisplayMode.TWO_COLUMNS
                                    DisplayMode.TWO_COLUMNS -> DisplayMode.THREE_COLUMNS
                                    DisplayMode.THREE_COLUMNS -> DisplayMode.SINGLE_COLUMN
                                }
                    },
                    onShowSearchDialog = {
                        viewModel.searchDialogQuery = ""
                        viewModel.showSearchDialog = true
                    },
                    isSearching = viewModel.isSearching,
                    onExitSearch = {
                        viewModel.searchQuery = ""
                        viewModel.isSearching = false
                        viewModel.searchResults.clear()
                    },
                    onNewFolder = {
                        viewModel.newFolderName = ""
                        viewModel.showNewFolderDialog = true
                    },
                    isMultiSelectMode = viewModel.isMultiSelectMode
            )

            // 标签栏
            FileManagerTabRow(
                    tabs = viewModel.tabs,
                    activeTabIndex = viewModel.activeTabIndex,
                    onSwitchTab = { viewModel.switchTab(it) },
                    onCloseTab = { viewModel.closeTab(it) },
                    onAddTab = { viewModel.addTab() }
            )

            // 路径导航栏 - 添加点击事件处理
            PathNavigationBar(
                    currentPath = viewModel.currentPath,
                    onNavigateToPath = { path -> viewModel.navigateToPath(path) }
            )
            
            // 快速访问栏
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    QuickAccessChip(
                        name = "Linux",
                        icon = Icons.Default.Terminal,
                        isActive = viewModel.currentEnvironment == "linux" && viewModel.currentPath.startsWith("/"),
                        onClick = {
                            viewModel.navigateToPath("/", "linux")
                        }
                    )
                }
                item {
                    QuickAccessChip(
                        name = "SDCard",
                        icon = Icons.Default.SdCard,
                        isActive = viewModel.currentEnvironment == null && viewModel.currentPath.startsWith(Environment.getExternalStorageDirectory().absolutePath),
                        onClick = {
                            val sdcardPath = Environment.getExternalStorageDirectory().absolutePath
                            if (File(sdcardPath).exists()) {
                                viewModel.navigateToPath(sdcardPath, null)
                            }
                        }
                    )
                }
                item {
                    QuickAccessChip(
                        name = "Workspace",
                        icon = Icons.Default.Folder,
                        isActive = viewModel.currentEnvironment == null && viewModel.currentPath.startsWith(File(context.filesDir, "workspace").absolutePath),
                        onClick = {
                            val workspacePath = File(context.filesDir, "workspace").absolutePath
                            if (File(workspacePath).exists()) {
                                viewModel.navigateToPath(workspacePath, null)
                            }
                        }
                    )
                }

                items(safBookmarks) { bookmark ->
                    var menuExpanded by remember(bookmark.uri) { mutableStateOf(false) }
                    val repoEnv = remember(bookmark.name) { "repo:${bookmark.name}" }
                    Box {
                        QuickAccessChipWithLongPress(
                            name = bookmark.name,
                            icon = Icons.Default.Folder,
                            isActive = viewModel.currentEnvironment == repoEnv,
                            onClick = {
                                AppLogger.d("ToolboxFileManager", "switch to repository name=${bookmark.name} env=$repoEnv")
                                viewModel.navigateToPath("/", repoEnv)
                            },
                            onLongPress = { menuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.repo_bookmark_delete)) },
                                onClick = {
                                    menuExpanded = false
                                    val uri = runCatching { Uri.parse(bookmark.uri) }.getOrNull()
                                    if (uri != null) {
                                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                                    }
                                    scope.launch {
                                        apiPreferences.removeSafBookmark(bookmark.uri)
                                        if (viewModel.currentEnvironment == repoEnv) {
                                            val fallbackPath = File(context.filesDir, "workspace").absolutePath
                                            viewModel.navigateToPath(fallbackPath, null)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    QuickAccessChip(
                        name = "+",
                        icon = Icons.Default.Add,
                        isActive = false,
                        onClick = { addSafLauncher.launch(null) }
                    )
                }
            }

            // 主内容区域
            Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
            ) {
                FileListContent(
                        error = viewModel.error,
                        files = viewModel.files,
                        listState = listState,
                        isSearching = viewModel.isSearching,
                        searchResults = viewModel.searchResults,
                        displayMode = viewModel.displayMode,
                        itemSize = viewModel.itemSize,
                        isMultiSelectMode = viewModel.isMultiSelectMode,
                        selectedFiles = viewModel.selectedFiles,
                        selectedFile = viewModel.selectedFile,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onShowBottomActionMenu = { viewModel.showBottomActionMenu = true }
                )
            }

            // 状态栏
            StatusBar(
                    fileCount = viewModel.files.size,
                    selectedFiles = viewModel.selectedFiles,
                    selectedFile = viewModel.selectedFile,
                    isMultiSelectMode = viewModel.isMultiSelectMode,
                    onExitMultiSelect = {
                        viewModel.isMultiSelectMode = false
                        viewModel.selectedFiles.clear()
                    }
            )
        }

        // 加载中覆盖层
        LoadingOverlay(isLoading = viewModel.isLoading)
    }

    // 对话框
    // 搜索对话框
    SearchDialog(
            showDialog = viewModel.showSearchDialog,
            searchQuery = viewModel.searchDialogQuery,
            onQueryChange = { viewModel.searchDialogQuery = it },
            isCaseSensitive = viewModel.isCaseSensitive,
            onCaseSensitiveChange = { viewModel.isCaseSensitive = it },
            useWildcard = viewModel.useWildcard,
            onWildcardChange = { viewModel.useWildcard = it },
            onSearch = {
                viewModel.searchQuery = viewModel.searchDialogQuery
                viewModel.showSearchDialog = false
                if (viewModel.searchDialogQuery.isNotBlank()) {
                    viewModel.searchFiles(viewModel.searchDialogQuery)
                }
            },
            onDismiss = { viewModel.showSearchDialog = false }
    )

    // 搜索结果对话框
    SearchResultsDialog(
            showDialog = viewModel.showSearchResultsDialog,
            searchResults = viewModel.searchResults,
            onNavigateToFileDirectory = { path -> viewModel.navigateToFileDirectory(path) },
            onDismiss = { viewModel.showSearchResultsDialog = false }
    )

    // 新建文件夹对话框
    NewFolderDialog(
            showDialog = viewModel.showNewFolderDialog,
            folderName = viewModel.newFolderName,
            onFolderNameChange = { name -> viewModel.newFolderName = name },
            onCreateFolder = {
                if (viewModel.newFolderName.isNotBlank()) {
                    viewModel.createNewFolder(viewModel.newFolderName)
                    viewModel.showNewFolderDialog = false
                }
            },
            onDismiss = { viewModel.showNewFolderDialog = false }
    )

    // 文件上下文菜单
    FileContextMenu(
            showMenu = viewModel.showBottomActionMenu,
            onDismissRequest = { viewModel.showBottomActionMenu = false },
            contextMenuFile = viewModel.contextMenuFile,
            isMultiSelectMode = viewModel.isMultiSelectMode,
            selectedFiles = viewModel.selectedFiles,
            currentPath = viewModel.currentPath,
            currentEnvironment = viewModel.currentEnvironment,
            onFilesUpdated = { viewModel.loadCurrentDirectory() },
            toolHandler = toolHandler,
            onPaste = { viewModel.pasteFiles() },
            onCopy = { files -> viewModel.setClipboard(files, false) },
            onCut = { files -> viewModel.setClipboard(files, true) },
            onOpen = { file ->
                val fullPath = "${viewModel.currentPath}/${file.name}"
                val openTool =
                        AITool(
                                name = "open_file",
                                parameters = listOf(ToolParameter("path", fullPath)) +
                                        (viewModel.currentEnvironment?.let { listOf(ToolParameter("environment", it)) }
                                                ?: emptyList())
                        )
                toolHandler.executeTool(openTool)
            },
            onShare = { file ->
                val fullPath = "${viewModel.currentPath}/${file.name}"
                val shareTool =
                        AITool(
                                name = "share_file",
                                parameters = listOf(ToolParameter("path", fullPath)) +
                                        (viewModel.currentEnvironment?.let { listOf(ToolParameter("environment", it)) }
                                                ?: emptyList())
                        )
                toolHandler.executeTool(shareTool)
            }
    )
}

@Composable
private fun QuickAccessChipWithLongPress(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var suppressClickOnce by remember(name) { mutableStateOf(false) }
    Box(
        modifier = Modifier.pointerInput(onLongPress) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
                    waitForUpOrCancellation()
                    false
                } ?: true

                if (longPressed) {
                    suppressClickOnce = true
                    onLongPress()
                    waitForUpOrCancellation()
                }
            }
        }
    ) {
        QuickAccessChip(
            name = name,
            icon = icon,
            isActive = isActive,
            onClick = {
                if (suppressClickOnce) {
                    suppressClickOnce = false
                    return@QuickAccessChip
                }
                onClick()
            }
        )
    }
}

/**
 * 快速访问芯片组件
 */
@Composable
private fun QuickAccessChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                if (name.isNotBlank() && name != "+") {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isActive,
            borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}
