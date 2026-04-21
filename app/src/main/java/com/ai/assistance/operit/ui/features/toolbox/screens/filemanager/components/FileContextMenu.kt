package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextMenu(
        showMenu: Boolean,
        onDismissRequest: () -> Unit,
        contextMenuFile: FileItem?,
        isMultiSelectMode: Boolean,
        selectedFiles: List<FileItem>,
        currentPath: String,
        currentEnvironment: String?,
        onFilesUpdated: () -> Unit,
        toolHandler: AIToolHandler,
        onPaste: () -> Unit,
        onCopy: (List<FileItem>) -> Unit,
        onCut: (List<FileItem>) -> Unit,
        onOpen: (FileItem) -> Unit,
        onShare: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 对话框状态
    var showRenameDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // 对话框输入状态
    var newFileName by remember { mutableStateOf("") }
    var renamePrefix by remember { mutableStateOf("") }
    var renameSuffix by remember { mutableStateOf("") }
    var renameStartNumber by remember { mutableStateOf("1") }
    var renameUseOriginalName by remember { mutableStateOf(true) }
    var compressFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var showUnzipDialog by remember { mutableStateOf(false) }

    // 复制/剪切状态
    var clipboardFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }
    var clipboardSourcePath by remember { mutableStateOf<String?>(null) }

    fun withEnvParams(base: List<ToolParameter>): List<ToolParameter> {
        if (currentEnvironment.isNullOrBlank()) return base
        return base + ToolParameter("environment", currentEnvironment)
    }

    // 文件操作函数
    fun copyFile(files: List<FileItem>) {
        onCopy(files)
        onDismissRequest()
    }

    fun cutFile(files: List<FileItem>) {
        onCut(files)
        onDismissRequest()
    }

    fun pasteFile() {
        onPaste()
        onDismissRequest()
    }

    fun openFile(file: FileItem) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    val fullPath = "$currentPath/${file.name}"

                    val openTool =
                            AITool(
                                    name = "open_file",
                                    parameters = withEnvParams(listOf(ToolParameter("path", fullPath)))
                            )
                    AppLogger.d("ToolboxFileManager", "execute open_file path=$fullPath env=$currentEnvironment")
                    val result = toolHandler.executeTool(openTool)
                    AppLogger.d("ToolboxFileManager", "result open_file success=${result.success} error=${result.error}")

                    if (result.success) {
                        // 文件已成功打开
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: context.getString(R.string.file_error_open_failed) }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error opening file", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_error_open_failed) + ": ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
        onDismissRequest()
    }

    fun shareFile(file: FileItem) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    val fullPath = "$currentPath/${file.name}"

                    val shareTool =
                            AITool(
                                    name = "share_file",
                                    parameters = withEnvParams(listOf(ToolParameter("path", fullPath)))
                            )
                    AppLogger.d("ToolboxFileManager", "execute share_file path=$fullPath env=$currentEnvironment")
                    val result = toolHandler.executeTool(shareTool)
                    AppLogger.d("ToolboxFileManager", "result share_file success=${result.success} error=${result.error}")

                    if (result.success) {
                        // 文件已成功分享
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: context.getString(R.string.file_error_share_failed) }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error sharing file", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_error_share_failed) + ": ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
        onDismissRequest()
    }

    fun deleteFile(file: FileItem, currentPath: String) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    val fullPath = "$currentPath/${file.name}"

                    val deleteTool =
                            AITool(
                                    name = "delete_file",
                                    parameters =
                                            withEnvParams(listOf(
                                                    ToolParameter("path", fullPath),
                                                    ToolParameter(
                                                            "recursive",
                                                            file.isDirectory.toString()
                                                    )
                                            ))
                            )
                    AppLogger.d("ToolboxFileManager", "execute delete_file path=$fullPath env=$currentEnvironment")
                    val result = toolHandler.executeTool(deleteTool)
                    AppLogger.d("ToolboxFileManager", "result delete_file success=${result.success} error=${result.error}")

                    if (result.success) {
                        withContext(Dispatchers.Main) { onFilesUpdated() }
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: context.getString(R.string.file_manager_operation_failed) }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error deleting file", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_manager_operation_failed) + ": ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun renameFile(file: FileItem, newName: String, currentPath: String) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    val oldPath = "$currentPath/${file.name}"
                    val newPath = "$currentPath/$newName"

                    val moveTool =
                            AITool(
                                    name = "move_file",
                                    parameters =
                                            withEnvParams(listOf(
                                                    ToolParameter("source", oldPath),
                                                    ToolParameter("destination", newPath)
                                            ))
                            )
                    AppLogger.d("ToolboxFileManager", "execute move_file src=$oldPath dst=$newPath env=$currentEnvironment")
                    val result = toolHandler.executeTool(moveTool)
                    AppLogger.d("ToolboxFileManager", "result move_file success=${result.success} error=${result.error}")

                    if (result.success) {
                        withContext(Dispatchers.Main) { onFilesUpdated() }
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: context.getString(R.string.file_manager_operation_failed) }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error renaming file", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_manager_operation_failed) + ": ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun compressFiles(files: List<FileItem>, outputName: String, currentPath: String) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    // 构建文件路径列表
                    val filePaths = files.map { "$currentPath/${it.name}" }
                    val outputPath = "$currentPath/$outputName"

                    val compressTool =
                            AITool(
                                    name = "zip_files",
                                    parameters =
                                            withEnvParams(listOf(
                                                    ToolParameter(
                                                            "source",
                                                            filePaths.joinToString(",")
                                                    ),
                                                    ToolParameter("destination", outputPath)
                                            ))
                            )
                    AppLogger.d("ToolboxFileManager", "execute zip_files dst=$outputPath env=$currentEnvironment")
                    val result = toolHandler.executeTool(compressTool)
                    AppLogger.d("ToolboxFileManager", "result zip_files success=${result.success} error=${result.error}")

                    if (result.success) {
                        withContext(Dispatchers.Main) { onFilesUpdated() }
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: context.getString(R.string.file_error_compress_failed) }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error compressing files", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_error_compress_failed) + ": ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun unzipFile(file: FileItem, currentPath: String) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    val sourcePath = "$currentPath/${file.name}"
                    val destinationPath = currentPath

                    val unzipTool =
                            AITool(
                                    name = "unzip_files",
                                    parameters =
                                            withEnvParams(listOf(
                                                    ToolParameter("source", sourcePath),
                                                    ToolParameter("destination", destinationPath)
                                            ))
                            )
                    AppLogger.d("ToolboxFileManager", "execute unzip_files src=$sourcePath dst=$destinationPath env=$currentEnvironment")
                    val result = toolHandler.executeTool(unzipTool)
                    AppLogger.d("ToolboxFileManager", "result unzip_files success=${result.success} error=${result.error}")

                    if (result.success) {
                        withContext(Dispatchers.Main) { onFilesUpdated() }
                    } else {
                        withContext(Dispatchers.Main) { error = result.error ?: context.getString(R.string.file_error_extract_failed) }
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error unzipping file", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_error_extract_failed) + ": ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun batchRenameFiles(
            files: List<FileItem>,
            prefix: String,
            suffix: String,
            startNumber: Int,
            useOriginalName: Boolean,
            currentPath: String
    ) {
        coroutineScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                try {
                    // 为每个文件生成新名称并执行重命名
                    files.forEachIndexed { index, file ->
                        val originalNameWithoutExt = file.name.substringBeforeLast(".", "")
                        val extension =
                                if (file.name.contains(".")) ".${file.name.substringAfterLast(".")}"
                                else ""

                        val middlePart =
                                if (useOriginalName) originalNameWithoutExt
                                else (startNumber + index).toString()
                        val newName = "$prefix$middlePart$suffix$extension"

                        val oldPath = "$currentPath/${file.name}"
                        val newPath = "$currentPath/$newName"

                        val moveTool =
                                AITool(
                                        name = "move_file",
                                        parameters =
                                                withEnvParams(listOf(
                                                        ToolParameter("source", oldPath),
                                                        ToolParameter("destination", newPath)
                                                ))
                                )
                        AppLogger.d("ToolboxFileManager", "execute move_file src=$oldPath dst=$newPath env=$currentEnvironment")
                        toolHandler.executeTool(moveTool)
                    }

                    withContext(Dispatchers.Main) { onFilesUpdated() }
                } catch (e: Exception) {
                    AppLogger.e("FileContextMenu", "Error batch renaming files", e)
                    withContext(Dispatchers.Main) { error = context.getString(R.string.file_error_rename_failed, e.message ?: "Unknown error") }
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun createNewFolder(folderName: String, currentPath: String) {
        // Implementation of creating a new folder
    }

    if (showMenu && (contextMenuFile != null || isMultiSelectMode)) {
        val bottomSheetState = rememberModalBottomSheetState()

        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = bottomSheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (isMultiSelectMode) {
                    Text(
                            text = stringResource(R.string.files_selected, selectedFiles.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                            text = contextMenuFile!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮
                if (!isMultiSelectMode) {
                    // 打开选项 - 仅在单选模式下显示
                    FileActionButton(
                            icon = Icons.Default.OpenInNew,
                            text = stringResource(R.string.open),
                            onClick = { openFile(contextMenuFile!!) }
                    )
                }

                FileActionButton(
                        icon = Icons.Default.ContentCopy,
                        text = stringResource(R.string.copy),
                        onClick = {
                            if (isMultiSelectMode) {
                                copyFile(selectedFiles)
                            } else {
                                copyFile(listOf(contextMenuFile!!))
                            }
                        }
                )

                FileActionButton(
                        icon = Icons.Default.ContentCut,
                        text = stringResource(R.string.cut),
                        onClick = {
                            if (isMultiSelectMode) {
                                cutFile(selectedFiles)
                            } else {
                                cutFile(listOf(contextMenuFile!!))
                            }
                        }
                )

                FileActionButton(
                        icon = Icons.Default.ContentPaste,
                        text = stringResource(R.string.paste),
                        onClick = { pasteFile() }
                )

                if (!isMultiSelectMode) {
                    // 分享选项 - 仅在单选模式下显示
                    FileActionButton(
                            icon = Icons.Default.Share,
                            text = stringResource(R.string.share),
                            onClick = { shareFile(contextMenuFile!!) }
                    )

                    FileActionButton(
                            icon = Icons.Default.Edit,
                            text = stringResource(R.string.rename),
                            onClick = {
                                newFileName = contextMenuFile!!.name
                                showRenameDialog = true
                                onDismissRequest()
                            }
                    )

                    if (!contextMenuFile!!.isDirectory &&
                                    contextMenuFile!!.name.endsWith(".zip", ignoreCase = true)
                    ) {
                        FileActionButton(
                                icon = Icons.Default.FolderZip,
                                text = stringResource(R.string.file_menu_extract),
                                onClick = {
                                    showUnzipDialog = true
                                    onDismissRequest()
                                }
                        )
                    }
                } else {
                    // 多选模式下添加批量重命名选项
                    FileActionButton(
                            icon = Icons.Default.Edit,
                            text = stringResource(R.string.rename),
                            onClick = {
                                renamePrefix = ""
                                renameSuffix = ""
                                renameStartNumber = "1"
                                renameUseOriginalName = true
                                showBatchRenameDialog = true
                                onDismissRequest()
                            }
                    )

                    // 多选模式下添加压缩选项
                    FileActionButton(
                            icon = Icons.Default.Archive,
                            text = stringResource(R.string.file_compress),
                            onClick = {
                                compressFileName =
                                        "archive_${System.currentTimeMillis() / 1000}.zip"
                                showCompressDialog = true
                                onDismissRequest()
                            }
                    )
                }

                FileActionButton(
                        icon = Icons.Default.Delete,
                        text = stringResource(R.string.delete),
                        onClick = {
                            showDeleteConfirmDialog = true
                            onDismissRequest()
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                ) { Text(stringResource(R.string.cancel)) }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 重命名对话框
    if (showRenameDialog && contextMenuFile != null) {
        AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text(stringResource(R.string.rename)) },
                text = {
                    OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            label = { Text(stringResource(R.string.file_dialog_new_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newFileName.isNotBlank() &&
                                                newFileName != contextMenuFile!!.name
                                ) {
                                    renameFile(contextMenuFile!!, newFileName, currentPath)
                                }
                                showRenameDialog = false
                            }
                    ) { Text(stringResource(R.string.rename)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
        )
    }

    // 批量重命名对话框
    if (showBatchRenameDialog) {
        AlertDialog(
                onDismissRequest = { showBatchRenameDialog = false },
                title = { Text(stringResource(R.string.rename)) },
                text = {
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                stringResource(R.string.files_selected, selectedFiles.size),
                                style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                                value = renamePrefix,
                                onValueChange = { renamePrefix = it },
                                label = { Text(stringResource(R.string.file_dialog_rename_prefix)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                    checked = renameUseOriginalName,
                                    onCheckedChange = { renameUseOriginalName = it }
                            )
                            Text(stringResource(R.string.file_dialog_rename_keep_original))
                        }

                        AnimatedVisibility(visible = !renameUseOriginalName) {
                            OutlinedTextField(
                                    value = renameStartNumber,
                                    onValueChange = {
                                        // 只允许数字输入
                                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                            renameStartNumber = it
                                        }
                                    },
                                    label = { Text(stringResource(R.string.file_dialog_rename_start_number)) },
                                    keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                                value = renameSuffix,
                                onValueChange = { renameSuffix = it },
                                label = { Text(stringResource(R.string.file_dialog_rename_suffix)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 预览示例
                        Text(
                                stringResource(
                                        R.string.file_dialog_rename_preview,
                                        renamePrefix,
                                        if (renameUseOriginalName) stringResource(R.string.file_dialog_rename_original) else renameStartNumber,
                                        renameSuffix,
                                        if (selectedFiles.firstOrNull()?.name?.contains(".") == true)
                                                stringResource(R.string.file_dialog_rename_original) else ""
                                ),
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                val startNum = renameStartNumber.toIntOrNull() ?: 1
                                batchRenameFiles(
                                        selectedFiles,
                                        renamePrefix,
                                        renameSuffix,
                                        startNum,
                                        renameUseOriginalName,
                                        currentPath
                                )
                                showBatchRenameDialog = false
                            },
                            enabled = selectedFiles.isNotEmpty()
                    ) { Text(stringResource(R.string.rename)) }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchRenameDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
        )
    }

    // 压缩文件对话框
    if (showCompressDialog) {
        AlertDialog(
                onDismissRequest = { showCompressDialog = false },
                title = { Text(stringResource(R.string.file_dialog_compress_title)) },
                text = {
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                stringResource(R.string.files_selected, selectedFiles.size),
                                style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                                value = compressFileName,
                                onValueChange = { compressFileName = it },
                                label = { Text(stringResource(R.string.file_dialog_compress_filename)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (compressFileName.isNotBlank()) {
                                    // 确保文件名以.zip结尾
                                    val fileName =
                                            if (compressFileName.endsWith(".zip")) compressFileName
                                            else "$compressFileName.zip"
                                    compressFiles(selectedFiles, fileName, currentPath)
                                    showCompressDialog = false
                                }
                            },
                            enabled = compressFileName.isNotBlank() && selectedFiles.isNotEmpty()
                    ) { Text(stringResource(R.string.file_dialog_compress_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCompressDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(stringResource(R.string.delete)) },
                text = {
                    Text(
                            if (isMultiSelectMode) {
                                stringResource(R.string.file_dialog_delete_multiple, selectedFiles.size)
                            } else {
                                stringResource(R.string.file_dialog_delete_single, contextMenuFile!!.name)
                            }
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (isMultiSelectMode) {
                                    selectedFiles.forEach { file -> deleteFile(file, currentPath) }
                                } else {
                                    deleteFile(contextMenuFile!!, currentPath)
                                }
                                showDeleteConfirmDialog = false
                            }
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
        )
    }

    // 新建文件夹对话框
    if (showNewFolderDialog) {
        AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text(stringResource(R.string.new_folder)) },
                text = {
                    OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text(stringResource(R.string.folder_name)) },
                            singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    createNewFolder(newFolderName, currentPath)
                                    showNewFolderDialog = false
                                    newFolderName = ""
                                }
                            }
                    ) { Text(stringResource(R.string.create_folder)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
        )
    }

    // 解压文件对话框
    if (showUnzipDialog && contextMenuFile != null) {
        AlertDialog(
                onDismissRequest = { showUnzipDialog = false },
                title = { Text(stringResource(R.string.file_dialog_extract_title)) },
                text = { Text(stringResource(R.string.file_dialog_extract_message, contextMenuFile.name)) },
                confirmButton = {
                    TextButton(
                            onClick = {
                                unzipFile(contextMenuFile, currentPath)
                                showUnzipDialog = false
                            }
                    ) { Text(stringResource(R.string.file_dialog_extract_button)) }
                },
                dismissButton = { TextButton(onClick = { showUnzipDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 加载指示器
    if (isLoading) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ),
                contentAlignment = Alignment.Center
        ) {
            Box(
                    modifier =
                            Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(16.dp)
                                    )
                                    .padding(24.dp)
            ) {
                Text(
                        text = stringResource(R.string.loading_files),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 错误提示
    if (error != null) {
        AlertDialog(
                onDismissRequest = { error = null },
                title = { Text(stringResource(R.string.error_title)) },
                text = { Text(error!!) },
                confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.confirm_delete)) } }
        )
    }
}
