package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components.DisplayMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.TabItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileManagerViewModel(private val context: Context) : ViewModel() {
    // 路径和导航状态
    var currentPath by mutableStateOf("/sdcard")
        private set
    var currentEnvironment by mutableStateOf<String?>(null)
        private set
    var files = mutableStateListOf<FileItem>()
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // 选择状态
    var selectedFile by mutableStateOf<FileItem?>(null)
    var selectedFiles = mutableStateListOf<FileItem>()
    var isMultiSelectMode by mutableStateOf(false)

    // 剪贴板状态
    var clipboardFiles = mutableStateListOf<FileItem>()
    var isCutOperation by mutableStateOf(false)
    var clipboardSourcePath by mutableStateOf<String?>(null)

    // 显示状态
    var itemSize by mutableStateOf(1f)
    val minItemSize = 0.5f
    val maxItemSize = 1.3f
    val itemSizeStep = 0.1f
    var displayMode by mutableStateOf(DisplayMode.SINGLE_COLUMN)

    // 滚动状态
    val scrollPositions = mutableStateMapOf<String, Int>()
    var pendingScrollPosition by mutableStateOf<Pair<String, Int>?>(null)

    // 标签页状态
    var tabs = mutableStateListOf(TabItem("/sdcard", context.getString(R.string.file_manager_home), null))
    var activeTabIndex by mutableStateOf(0)

    // 上下文菜单状态
    var showBottomActionMenu by mutableStateOf(false)
    var contextMenuFile by mutableStateOf<FileItem?>(null)

    // 对话框状态
    var showNewFolderDialog by mutableStateOf(false)
    var newFolderName by mutableStateOf("")
    var showCompressDialog by mutableStateOf(false)
    var compressFileName by mutableStateOf("")

    // 搜索状态
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults = mutableStateListOf<FileItem>()
    var showSearchDialog by mutableStateOf(false)
    var searchDialogQuery by mutableStateOf("")
    var showSearchResultsDialog by mutableStateOf(false)
    var isCaseSensitive by mutableStateOf(false)
    var useWildcard by mutableStateOf(true)

    private val toolHandler by lazy { AIToolHandler.getInstance(context) }

    private fun withEnvParams(base: List<ToolParameter>, environment: String? = currentEnvironment): List<ToolParameter> {
        if (environment.isNullOrBlank()) return base
        return base + ToolParameter("environment", environment)
    }

    private fun isSafEnv(environment: String?): Boolean {
        return environment?.startsWith("repo:", ignoreCase = true) == true
    }

    // 加载当前目录内容
    fun loadCurrentDirectory(path: String = currentPath, environment: String? = currentEnvironment) {
        viewModelScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    val listFilesTool =
                            AITool(
                                    name = "list_files",
                                    parameters = withEnvParams(listOf(ToolParameter("path", path)), environment)
                            )
                    AppLogger.d("ToolboxFileManager", "execute list_files path=$path env=$environment")
                    val result = toolHandler.executeTool(listFilesTool)
                    AppLogger.d("ToolboxFileManager", "result list_files success=${result.success} error=${result.error}")
                    if (result.success) {
                        val directoryListing = result.result as DirectoryListingData
                        val fileList =
                                directoryListing.entries.map { entry ->
                                    FileItem(
                                            name = entry.name,
                                            isDirectory = entry.isDirectory,
                                            size = entry.size,
                                            lastModified = entry.lastModified.toLongOrNull() ?: 0
                                    )
                                }
                        withContext(Dispatchers.Main) {
                            files.clear()
                            files.add(FileItem("..", true, 0, 0))
                            files.addAll(fileList)
                        }
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: "Unknown error" }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileManagerViewModel", "Error loading directory", e)
                    withContext(Dispatchers.Main) { error = "Error: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    // 导航到目录
    fun navigateToDirectory(dir: FileItem) {
        if (dir.isDirectory) {
            // 保存当前滚动位置
            val newPath =
                    if (dir.name == "..") {
                        navigateUp()
                        return
                    } else {
                        buildPath(currentPath, dir.name)
                    }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = newPath to (scrollPositions[newPath] ?: 0)

            currentPath = newPath
            if (activeTabIndex < tabs.size) {
                val updatedTabs = tabs.toMutableList()
                updatedTabs[activeTabIndex] = updatedTabs[activeTabIndex].copy(path = newPath)
                tabs.clear()
                tabs.addAll(updatedTabs)
            }

            loadCurrentDirectory()
        }
    }

    // 导航到父目录
    fun navigateUp(): Boolean {
        if (currentPath != "/") {
            val parentPath =
                    if (currentPath.count { it == '/' } <= 1) {
                        "/"
                    } else {
                        currentPath.substringBeforeLast("/")
                    }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = parentPath to (scrollPositions[parentPath] ?: 0)

            currentPath = parentPath
            if (activeTabIndex < tabs.size) {
                val updatedTabs = tabs.toMutableList()
                updatedTabs[activeTabIndex] = updatedTabs[activeTabIndex].copy(path = parentPath)
                tabs.clear()
                tabs.addAll(updatedTabs)
            }

            loadCurrentDirectory()
            return true
        }
        return false
    }

    // 直接导航到指定路径
    fun navigateToPath(path: String, environment: String? = currentEnvironment) {
        if (path.isNotEmpty()) {
            val normalizedPath =
                    when {
                        path == "/" -> "/"
                        path.endsWith("/") -> path.substring(0, path.length - 1)
                        else -> path
                    }

            // 设置需要恢复的滚动位置
            pendingScrollPosition = normalizedPath to (scrollPositions[normalizedPath] ?: 0)

            currentEnvironment = environment
            currentPath = normalizedPath
            if (activeTabIndex < tabs.size) {
                val updatedTabs = tabs.toMutableList()
                updatedTabs[activeTabIndex] =
                        updatedTabs[activeTabIndex].copy(path = normalizedPath, environment = environment)
                tabs.clear()
                tabs.addAll(updatedTabs)
            }

            loadCurrentDirectory(normalizedPath, environment)
        }
    }

    // 添加新标签
    fun addTab(path: String = "/sdcard", title: String = context.getString(R.string.file_manager_new_tab)) {
        tabs.add(TabItem(path, title, null))
        activeTabIndex = tabs.size - 1
        currentPath = path
        currentEnvironment = null
        loadCurrentDirectory()
    }

    // 关闭标签
    fun closeTab(index: Int) {
        if (tabs.size > 1) {
            tabs.removeAt(index)
            if (activeTabIndex >= tabs.size) {
                activeTabIndex = tabs.size - 1
            }
            currentPath = tabs[activeTabIndex].path
            currentEnvironment = tabs[activeTabIndex].environment
            loadCurrentDirectory()
        }
    }

    // 切换标签
    fun switchTab(index: Int) {
        if (index < tabs.size) {
            activeTabIndex = index
            currentPath = tabs[index].path
            currentEnvironment = tabs[index].environment
            loadCurrentDirectory()
        }
    }

    // 创建新文件夹
    fun createNewFolder(folderName: String) {
        viewModelScope.launch {
            try {
                // 处理路径，使用统一的buildPath方法
                val fullPath = buildPath(currentPath, folderName)

                val createFolderTool =
                        AITool(
                                name = "make_directory",
                                parameters = withEnvParams(listOf(ToolParameter("path", fullPath)))
                        )

                AppLogger.d("ToolboxFileManager", "execute make_directory path=$fullPath env=$currentEnvironment")
                val result = toolHandler.executeTool(createFolderTool)
                AppLogger.d("ToolboxFileManager", "result make_directory success=${result.success} error=${result.error}")

                if (result.success) {
                    // 刷新当前目录
                    loadCurrentDirectory()
                } else {
                    error = result.error ?: "Unknown error"
                }
            } catch (e: Exception) {
                AppLogger.e("FileManagerViewModel", "Error creating folder", e)
                error = "Error: ${e.message}"
            }
        }
    }

    // 搜索文件
    fun searchFiles(query: String) {
        if (query.isBlank()) {
            isSearching = false
            searchResults.clear()
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            isSearching = true

            withContext(Dispatchers.IO) {
                try {
                    // 处理通配符
                    val searchPattern = if (useWildcard) "*$query*" else query

                    val searchTool =
                            AITool(
                                    name = "find_files",
                                    parameters =
                                            withEnvParams(listOf(
                                                    ToolParameter("path", currentPath),
                                                    ToolParameter("pattern", searchPattern),
                                                    ToolParameter(
                                                            "case_sensitive",
                                                            isCaseSensitive.toString()
                                                    )
                                            ))
                            )

                    AppLogger.d("ToolboxFileManager", "execute find_files path=$currentPath env=$currentEnvironment pattern=$searchPattern")
                    val result = toolHandler.executeTool(searchTool)
                    AppLogger.d("ToolboxFileManager", "result find_files success=${result.success} error=${result.error}")
                    if (result.success) {
                        // 使用正确的FindFilesResultData类型解析结果
                        val findResult = result.result as FindFilesResultData
                        val fileList =
                                findResult.files.map { filePath ->
                                    // 从完整路径中提取文件名
                                    val fileName = filePath.substringAfterLast("/")
                                    // 检查是否为目录
                                    val isDir =
                                            try {
                                                val fileInfoTool =
                                                        AITool(
                                                                name = "file_info",
                                                                parameters =
                                                                        withEnvParams(listOf(
                                                                                ToolParameter(
                                                                                        "path",
                                                                                        filePath
                                                                                )
                                                                        ))
                                                        )
                                                val fileInfoResult =
                                                        toolHandler.executeTool(fileInfoTool)
                                                if (fileInfoResult.success) {
                                                    val fileInfo =
                                                            fileInfoResult.result as FileInfoData
                                                    fileInfo.fileType == "directory"
                                                } else {
                                                    false
                                                }
                                            } catch (e: Exception) {
                                                false
                                            }

                                    // 创建FileItem，保存完整路径
                                    FileItem(
                                            name = fileName,
                                            isDirectory = isDir,
                                            size = 0, // 大小信息暂时不获取
                                            lastModified = 0, // 修改时间暂时不获取
                                            fullPath = filePath // 保存完整路径
                                    )
                                }

                        withContext(Dispatchers.Main) {
                            searchResults.clear()
                            searchResults.addAll(fileList)
                            showSearchResultsDialog = true
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            error = result.error ?: context.getString(R.string.file_manager_search_failed)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileManagerViewModel", "Error searching files", e)
                    withContext(Dispatchers.Main) {
                        error = context.getString(R.string.file_manager_search_error, e.message ?: "Unknown")
                    }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    // 导航到文件所在目录
    fun navigateToFileDirectory(filePath: String) {
        val directoryPath = filePath.substringBeforeLast("/")
        if (directoryPath.isNotEmpty()) {
            // 设置需要恢复的滚动位置
            pendingScrollPosition = directoryPath to (scrollPositions[directoryPath] ?: 0)

            currentPath = directoryPath
            if (activeTabIndex < tabs.size) {
                val updatedTabs = tabs.toMutableList()
                updatedTabs[activeTabIndex] = updatedTabs[activeTabIndex].copy(path = directoryPath)
                tabs.clear()
                tabs.addAll(updatedTabs)
            }

            // 关闭搜索结果对话框
            showSearchResultsDialog = false
            isSearching = false

            loadCurrentDirectory()
        }
    }

    // 复制/剪切/粘贴操作
    fun setClipboard(files: List<FileItem>, isCut: Boolean) {
        clipboardFiles.clear()
        clipboardFiles.addAll(files)
        clipboardSourcePath = currentPath
        isCutOperation = isCut
    }

    // 粘贴文件
    fun pasteFiles() {
        if (clipboardFiles.isEmpty() || clipboardSourcePath == null) return

        viewModelScope.launch {
            try {
                isLoading = true
                error = null

                withContext(Dispatchers.IO) {
                    clipboardFiles.forEach { file ->
                        // 处理源路径和目标路径，使用统一的buildPath方法
                        val fullSourcePath = buildPath(clipboardSourcePath!!, file.name)
                        val fullTargetPath = buildPath(currentPath, file.name)

                        val copyTool =
                                AITool(
                                        name = "copy_file",
                                        parameters =
                                                withEnvParams(listOf(
                                                        ToolParameter("source", fullSourcePath),
                                                        ToolParameter("destination", fullTargetPath)
                                                ))
                                )

                        AppLogger.d(
                            "ToolboxFileManager",
                            "execute copy_file src=$fullSourcePath dst=$fullTargetPath env=$currentEnvironment"
                        )
                        val result = toolHandler.executeTool(copyTool)
                        AppLogger.d("ToolboxFileManager", "result copy_file success=${result.success} error=${result.error}")

                        if (result.success) {
                            if (isCutOperation) {
                                val deleteTool =
                                        AITool(
                                                name = "delete_file",
                                                parameters =
                                                        withEnvParams(listOf(
                                                                ToolParameter(
                                                                        "path",
                                                                        fullSourcePath
                                                                )
                                                        ))
                                        )
                                toolHandler.executeTool(deleteTool)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                error = result.error ?: "Unknown error"
                            }
                        }
                    }
                }

                // 刷新当前目录
                loadCurrentDirectory()
            } catch (e: Exception) {
                AppLogger.e("FileManagerViewModel", "Error performing file operation", e)
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // 初始化
    init {
        loadCurrentDirectory()
    }

    // 辅助方法：构建正确的文件路径，避免出现//的情况
    private fun buildPath(parentPath: String, childName: String): String {
        return if (parentPath == "/") {
            "/$childName"
        } else {
            "$parentPath/$childName"
        }
    }
}
