package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

// 目录条目数据类
data class DirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: String,
        val permissions: String
)

// 打开的文件信息
@Serializable
data class OpenFileInfo(
        val path: String,
        val content: String,
        val lastModified: Long,
        val name: String = File(path).name,
        val mimeType: String = ""
)

// 快速路径条目
data class QuickPathEntry(
        val name: String,
        val path: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/** 文件浏览器组件 - VSCode风格 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(
        initialPath: String,
        environment: String? = null,
        onBindWorkspace: ((String, String?) -> Unit)? = null,
        onCancel: () -> Unit,
        isManageMode: Boolean = false,
        onFileOpen: ((OpenFileInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())
    var currentPath by remember { mutableStateOf(initialPath) }
    var currentEnvironment by remember { mutableStateOf(environment) }
    var fileList by remember { mutableStateOf<List<DirectoryEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var pendingRepoBookmarkUri by remember { mutableStateOf<Uri?>(null) }
    var repoBookmarkNameInput by remember { mutableStateOf("") }
    var showRepoBookmarkNameDialog by remember { mutableStateOf(false) }
    var repoBookmarkNameError by remember { mutableStateOf<String?>(null) }
    // 用于控制长按上下文菜单的状态
    var contextMenuExpandedFor by remember { mutableStateOf<DirectoryEntry?>(null) }
    // 排序方式：0=名称, 1=大小, 2=修改时间
    var sortMode by remember { mutableStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    // 是否显示隐藏文件（以.开头）
    var showHiddenFiles by remember { mutableStateOf(false) }

    LaunchedEffect(environment) { currentEnvironment = environment }

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
                .lowercase(Locale.ROOT)
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

    val isSafEnv = remember(currentEnvironment) { currentEnvironment?.startsWith("repo:", ignoreCase = true) == true }

    fun joinPath(parent: String, child: String): String {
        val p = if (parent.isBlank()) "/" else parent
        return if (p.endsWith("/")) "$p$child" else "$p/$child"
    }

    fun parentPath(path: String): String? {
        val normalized = path.trimEnd('/').ifBlank { "/" }
        if (normalized == "/") return null
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isBlank()) "/" else parent
    }

    fun withEnvParams(base: List<ToolParameter>): List<ToolParameter> {
        if (currentEnvironment.isNullOrBlank()) return base
        return base + ToolParameter("environment", currentEnvironment!!)
    }

    // 快速路径定义
    val quickPaths = remember {
        listOf(
            QuickPathEntry(
                name = "Linux",
                path = "/",
                icon = Icons.Default.Terminal
            ),
            QuickPathEntry(
                name = "SDCard",
                path = Environment.getExternalStorageDirectory().absolutePath,
                icon = Icons.Default.SdCard
            ),
            QuickPathEntry(
                name = "Workspace",
                path = File(context.filesDir, "workspace").absolutePath,
                icon = Icons.Default.Folder
            )
        )
    }

    fun loadDirectory(path: String) {
        if (isLoading) return // 防止并发加载
        coroutineScope.launch {
            isLoading = true
            try {
                val tool = AITool("list_files", withEnvParams(listOf(ToolParameter("path", path))))
                AppLogger.d("WorkspaceFileBrowser", "execute list_files path=$path env=$currentEnvironment")
                val result = toolHandler.executeTool(tool)
                AppLogger.d("WorkspaceFileBrowser", "result list_files success=${result.success} error=${result.error}")
                if (result.success && result.result is DirectoryListingData) {
                    val entries = (result.result as DirectoryListingData).entries
                    fileList =
                            entries.map {
                                DirectoryEntry(
                                        name = it.name,
                                        isDirectory = it.isDirectory,
                                        size = it.size,
                                        lastModified = it.lastModified,
                                        permissions = it.permissions
                                )
                            }
                    currentPath = path // 仅在成功时更新路径
                } else {
                    // 加载失败，不改变任何状态，用户停留在当前页面
                }
            } catch (e: Exception) {
                // 发生异常，同样不改变状态
            } finally {
                isLoading = false
            }
        }
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

                        coroutineScope.launch {
                            apiPreferences.addSafBookmark(uri.toString(), name)
                            currentEnvironment = "repo:$name"
                            loadDirectory("/")
                        }

                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    fun createNewFile(fileName: String, isDirectory: Boolean) {
        coroutineScope.launch {
            isLoading = true
            try {
                val filePath =
                    if (isSafEnv) {
                        joinPath(currentPath, fileName)
                    } else {
                        File(currentPath, fileName).path
                    }
                val tool =
                        if (isDirectory) {
                            AITool("make_directory", withEnvParams(listOf(ToolParameter("path", filePath))))
                        } else {
                            AITool(
                                    "write_file",
                                    withEnvParams(
                                        listOf(
                                            ToolParameter("path", filePath),
                                            ToolParameter("content", "")
                                        )
                                    )
                            )
                        }
                AppLogger.d("WorkspaceFileBrowser", "execute ${tool.name} path=$filePath env=$currentEnvironment")
                toolHandler.executeTool(tool)
                loadDirectory(currentPath) // 刷新目录
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteFile(filePath: String) {
        coroutineScope.launch {
            isLoading = true
            try {
                val tool = AITool(
                    "delete_file",
                    withEnvParams(listOf(ToolParameter("path", filePath), ToolParameter("recursive", "true")))
                )
                AppLogger.d("WorkspaceFileBrowser", "execute delete_file path=$filePath env=$currentEnvironment")
                toolHandler.executeTool(tool)
                loadDirectory(currentPath) // 刷新目录
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }

    fun openFile(filePath: String) {
        coroutineScope.launch {
            isLoading = true
            try {
                val mimeType = workspaceMimeTypeForPath(filePath)
                val lastModified = if (isSafEnv) System.currentTimeMillis() else File(filePath).lastModified()

                if (workspaceShouldOpenAsDirectPreview(filePath)) {
                    onFileOpen?.invoke(
                        OpenFileInfo(
                            path = filePath,
                            content = "",
                            lastModified = lastModified,
                            mimeType = mimeType
                        )
                    )
                    return@launch
                }

                val tool = AITool("read_file_full", withEnvParams(listOf(ToolParameter("path", filePath))))
                AppLogger.d("WorkspaceFileBrowser", "execute read_file_full path=$filePath env=$currentEnvironment")
                val result = toolHandler.executeTool(tool)
                AppLogger.d("WorkspaceFileBrowser", "result read_file_full success=${result.success} error=${result.error}")
                if (result.success && result.result is FileContentData) {
                    val fileContentData = result.result as FileContentData
                    val content = fileContentData.content
                    val openFileInfo = OpenFileInfo(
                        path = filePath,
                        content = content,
                        lastModified = lastModified,
                        mimeType = mimeType
                    )
                    onFileOpen?.invoke(openFileInfo)
                }
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDirectory(currentPath) }

    if (showCreateFileDialog) {
        AlertDialog(
                onDismissRequest = { showCreateFileDialog = false },
                title = { Text(stringResource(R.string.file_manager_create_new_file)) },
                text = {
                    Column {
                        TextField(
                                value = newFileName,
                                onValueChange = { newFileName = it },
                                label = { Text(stringResource(R.string.file_manager_file_name)) },
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor =
                                                        MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor =
                                                        MaterialTheme.colorScheme.surface
                                        )
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                                onClick = {
                                    if (newFileName.isNotEmpty()) {
                                        createNewFile(newFileName, false)
                                        showCreateFileDialog = false
                                        newFileName = ""
                                    }
                                }
                        ) { Text(stringResource(R.string.file_manager_create_file)) }
                        TextButton(
                                onClick = {
                                    if (newFileName.isNotEmpty()) {
                                        createNewFile(newFileName, true)
                                        showCreateFileDialog = false
                                        newFileName = ""
                                    }
                                }
                        ) { Text(stringResource(R.string.file_manager_create_folder)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFileDialog = false }) { Text(stringResource(R.string.file_manager_cancel)) }
                }
        )
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface) // 设置不透明背景
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, // 移除点击时的涟漪效果
                                    enabled = true,
                                    onClick = {}
                            ) // 拦截点击事件，防止穿透
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 路径导航栏 - 移除背景使其更简洁
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // 头部省略的路径显示（使用水平滚动，自动滚动到末尾）
                val scrollState = rememberScrollState()
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                
                LaunchedEffect(currentPath, textLayoutResult) {
                    textLayoutResult?.let {
                        // 滚动到末尾，显示路径的最后部分
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }
                
                Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // 确保在深色模式下路径文字可见
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollState)
                )

                // 显示/隐藏隐藏文件按钮
                IconButton(
                        onClick = { showHiddenFiles = !showHiddenFiles },
                        modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                            if (showHiddenFiles) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showHiddenFiles) stringResource(R.string.file_manager_hide_dot_files) else stringResource(R.string.file_manager_show_dot_files),
                            modifier = Modifier.size(18.dp),
                            tint = if (showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 排序按钮
                Box {
                    IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.file_manager_sort),
                                modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_manager_sort_name) + "${if (sortMode == 0) " ✓" else ""}") },
                                onClick = { sortMode = 0; showSortMenu = false }
                        )
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_manager_sort_size) + "${if (sortMode == 1) " ✓" else ""}") },
                                onClick = { sortMode = 1; showSortMenu = false }
                        )
                        DropdownMenuItem(
                                text = { Text(stringResource(R.string.file_manager_sort_by_modified) + "${if (sortMode == 2) " ✓" else ""}") },
                                onClick = { sortMode = 2; showSortMenu = false }
                        )
                    }
                }

                if (isManageMode) {
                    IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.file_manager_new),
                                modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                            onClick = { loadDirectory(currentPath) },
                            modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.file_manager_refresh),
                                modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // 快速路径栏
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPaths) { quickPath ->
                    QuickPathChip(
                        entry = quickPath,
                        isActive =
                            if (quickPath.name == "Linux") {
                                currentEnvironment == "linux" && currentPath.startsWith(quickPath.path)
                            } else {
                                currentEnvironment == null && currentPath.startsWith(quickPath.path)
                            },
                        onClick = {
                            if (quickPath.name == "Linux") {
                                currentEnvironment = "linux"
                                loadDirectory("/")
                                return@QuickPathChip
                            }

                            val pathFile = File(quickPath.path)
                            if (pathFile.exists() && pathFile.isDirectory) {
                                currentEnvironment = null
                                loadDirectory(quickPath.path)
                            }
                        }
                    )
                }

                items(safBookmarks) { bookmark ->
                    var menuExpanded by remember(bookmark.uri) { mutableStateOf(false) }
                    val repoEnv = remember(bookmark.name) { "repo:${bookmark.name}" }
                    Box {
                        QuickPathChipWithLongPress(
                            entry = QuickPathEntry(name = bookmark.name, path = "/", icon = Icons.Default.Folder),
                            isActive = currentEnvironment == repoEnv,
                            onClick = {
                                currentEnvironment = repoEnv
                                loadDirectory("/")
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
                                    coroutineScope.launch {
                                        apiPreferences.removeSafBookmark(bookmark.uri)
                                        if (currentEnvironment == repoEnv) {
                                            currentEnvironment = null
                                            loadDirectory(initialPath)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    QuickPathChip(
                        entry = QuickPathEntry(name = "+", path = "", icon = Icons.Default.Add),
                        isActive = false,
                        onClick = { addSafLauncher.launch(null) }
                    )
                }
            }

            // 文件列表
            if (isLoading) {
                Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 8.dp)
                ) {
                    // 使用更健壮的方式来判断是否应该显示返回上一级的选项
                    val canGoUp = if (isSafEnv) parentPath(currentPath) != null else File(currentPath).parent != null
                    if (canGoUp) {
                        item {
                            FileListItem(
                                    name = "..",
                                    icon = Icons.Default.FolderOpen,
                                    isDirectory = true,
                                    onClick = {
                                        if (isSafEnv) {
                                            val p = parentPath(currentPath)
                                            if (p != null) loadDirectory(p)
                                        } else {
                                            File(currentPath).parent?.let { parentPath ->
                                                loadDirectory(parentPath)
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // 根据showHiddenFiles过滤文件列表
                    val filteredList = if (showHiddenFiles) {
                        fileList
                    } else {
                        fileList.filter { !it.name.startsWith(".") }
                    }
                    
                    items(getSortedFileList(filteredList, sortMode)) { item ->
                        Box { // 使用Box来定位上下文菜单
                            FileListItem(
                                    name = item.name,
                                    icon =
                                            if (item.isDirectory) Icons.Default.Folder
                                            else getFileIcon(item.name),
                                    isDirectory = item.isDirectory,
                                    onClick = {
                                        if (item.isDirectory) {
                                            val newPath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            loadDirectory(newPath)
                                        } else {
                                            val filePath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            openFile(filePath)
                                        }
                                    },
                                    onLongPress = {
                                        if (isManageMode && !item.name.startsWith(".")) {
                                            contextMenuExpandedFor = item
                                        }
                                    }
                            )

                            // 上下文菜单
                            DropdownMenu(
                                    expanded = contextMenuExpandedFor == item,
                                    onDismissRequest = { contextMenuExpandedFor = null }
                            ) {
                                DropdownMenuItem(
                                        text = { Text(stringResource(R.string.file_manager_delete)) },
                                        onClick = {
                                            val filePath =
                                                if (isSafEnv) joinPath(currentPath, item.name)
                                                else File(currentPath, item.name).path
                                            deleteFile(filePath)
                                            contextMenuExpandedFor = null
                                        },
                                        leadingIcon = {
                                            Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.file_manager_delete),
                                                    tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }

            // 底部操作栏
            if (onBindWorkspace != null) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onCancel) { Text(if (isManageMode) stringResource(R.string.file_manager_return) else stringResource(R.string.file_manager_cancel)) }
                    Button(
                        onClick = {
                            AppLogger.d(
                                "WorkspaceFileBrowser",
                                "bind workspace path=$currentPath env=$currentEnvironment isManageMode=$isManageMode"
                            )
                            onBindWorkspace(currentPath, currentEnvironment)
                        }
                    ) {
                        Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.file_manager_bind_current_folder))
                    }
                }
            }
        }
    }
}

/** 快速路径芯片组件 */
@Composable
private fun QuickPathChip(
    entry: QuickPathEntry,
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
                    imageVector = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                if (entry.name.isNotBlank() && entry.name != "+") {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall
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

@Composable
private fun QuickPathChipWithLongPress(
    entry: QuickPathEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    var suppressClickOnce by remember(entry.name, entry.path) { mutableStateOf(false) }
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
        QuickPathChip(
            entry = entry,
            isActive = isActive,
            onClick = {
                if (suppressClickOnce) {
                    suppressClickOnce = false
                    return@QuickPathChip
                }
                onClick()
            }
        )
    }
}

/** 抽取出的文件列表项，实现紧凑布局和长按手势 */
@Composable
private fun FileListItem(
        name: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isDirectory: Boolean,
        onClick: () -> Unit,
        onLongPress: (() -> Unit)? = null
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            // 统一处理单击和长按手势，并使用 onClick 和 onLongPress 作为 key
                            // 确保当 item 重用时，手势处理器能获取到最新的回调函数
                            .pointerInput(onClick, onLongPress) {
                                detectTapGestures(
                                        onTap = { onClick() },
                                        onLongPress = { onLongPress?.invoke() }
                                )
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp), // 减少垂直边距
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp), // 缩小图标
                tint =
                        if (isDirectory) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(12.dp)) // 减少间距
        Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, // 使用更小的字号
                color = MaterialTheme.colorScheme.onSurface // 确保在深色模式下文字可见
        )
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "%.1f MB".format(size / (1024.0 * 1024.0))
    }
}

@SuppressLint("SimpleDateFormat")
private fun formatLastModified(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

/** 根据文件名获取对应的图标 */
@Composable
fun getFileIcon(fileName: String) =
        when {
            fileName.endsWith(".html", true) || fileName.endsWith(".htm", true) ->
                    Icons.Default.Code
            fileName.endsWith(".css", true) -> Icons.Default.Brush
            fileName.endsWith(".js", true) -> Icons.Default.Code // 使用通用的代码图标替代Javascript
            fileName.endsWith(".json", true) -> Icons.Default.DataObject
            fileName.endsWith(".jpg", true) ||
                    fileName.endsWith(".png", true) ||
                    fileName.endsWith(".gif", true) ||
                    fileName.endsWith(".jpeg", true) -> Icons.Default.Image
            fileName.endsWith(".txt", true) -> Icons.AutoMirrored.Filled.TextSnippet
            fileName.endsWith(".md", true) -> Icons.AutoMirrored.Filled.Article
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }

/**
 * 根据排序模式对文件列表进行排序
 * @param fileList 原始文件列表
 * @param sortMode 0=按名称, 1=按大小, 2=按修改时间
 */
private fun getSortedFileList(fileList: List<DirectoryEntry>, sortMode: Int): List<DirectoryEntry> {
    return when (sortMode) {
        0 -> fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        1 -> fileList.sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
        2 -> fileList.sortedWith(compareBy({ !it.isDirectory }, { -parseLastModified(it.lastModified) }))
        else -> fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
}

/**
 * 解析修改时间字符串为毫秒时间戳
 */
@SuppressLint("SimpleDateFormat")
private fun parseLastModified(dateString: String): Long {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        inputFormat.parse(dateString)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
