package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import kotlinx.coroutines.launch

@Composable
fun FileListPane(
        path: String,
        isActive: Boolean,
        onPathChange: (String) -> Unit,
        onPaneClick: () -> Unit,
        onFileLongClick: (FileItem) -> Unit,
        onFileClick: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<FileItem?>(null) }

    // 加载目录内容
    LaunchedEffect(path) {
        isLoading = true
        error = null

        coroutineScope.launch {
            try {
                val listFilesTool =
                        AITool(
                                name = "list_files",
                                parameters = listOf(ToolParameter("path", path))
                        )
                val result = toolHandler.executeTool(listFilesTool)
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
                    files = listOf(FileItem("..", true, 0, 0)) + fileList
                } else {
                    error = result.error ?: "Unknown error"
                }
            } catch (e: Exception) {
                AppLogger.e("FileListPane", "Error loading directory", e)
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Surface(
            modifier =
                    Modifier.fillMaxHeight()
                            .clickable { onPaneClick() }
                            .then(
                                    if (isActive) {
                                        Modifier.border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(0.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                            ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = if (isActive) 2.dp else 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 窗格标题栏
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color =
                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = path,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                    if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                    )

                    if (path != "/sdcard") {
                        IconButton(
                                onClick = { onPathChange(path.substringBeforeLast("/")) },
                                modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = stringResource(R.string.filelist_go_back),
                                    tint =
                                            if (isActive)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 文件列表
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(files) { file ->
                        FileListItem(
                                file = file,
                                isSelected = selectedFile == file,
                                onItemClick = { onFileClick(file) },
                                onItemLongClick = { onFileLongClick(file) }
                        )
                    }
                }
            }
        }
    }
}
