package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem

/**
 * 文件列表内容区域
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListContent(
    error: String?,
    files: List<FileItem>,
    listState: LazyListState,
    isSearching: Boolean,
    searchResults: List<FileItem>,
    displayMode: DisplayMode,
    itemSize: Float,
    isMultiSelectMode: Boolean,
    selectedFiles: List<FileItem>,
    selectedFile: FileItem?,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onShowBottomActionMenu: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { /* 点击空白区域不做任何操作 */ },
                        onLongClick = {
                            if (isMultiSelectMode && selectedFiles.isNotEmpty()) {
                                onShowBottomActionMenu()
                            }
                        }
                    ),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val displayFiles = if (isSearching) searchResults else files

                when (displayMode) {
                    DisplayMode.SINGLE_COLUMN -> {
                        items(displayFiles) { file ->
                            FileListItem(
                                file = file,
                                isSelected = if (isMultiSelectMode) selectedFiles.contains(file) else selectedFile == file,
                                onItemClick = { onItemClick(file) },
                                onItemLongClick = { onItemLongClick(file) },
                                itemSize = itemSize,
                                displayMode = displayMode
                            )
                        }
                    }
                    DisplayMode.TWO_COLUMNS -> {
                        items(displayFiles.chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                row.forEach { file ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        FileListItem(
                                            file = file,
                                            isSelected = if (isMultiSelectMode) selectedFiles.contains(file) else selectedFile == file,
                                            onItemClick = { onItemClick(file) },
                                            onItemLongClick = { onItemLongClick(file) },
                                            itemSize = itemSize,
                                            displayMode = displayMode
                                        )
                                    }
                                }
                            }
                        }
                    }
                    DisplayMode.THREE_COLUMNS -> {
                        items(displayFiles.chunked(3)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                row.forEach { file ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        FileListItem(
                                            file = file,
                                            isSelected = if (isMultiSelectMode) selectedFiles.contains(file) else selectedFile == file,
                                            onItemClick = { onItemClick(file) },
                                            onItemLongClick = { onItemLongClick(file) },
                                            itemSize = itemSize,
                                            displayMode = displayMode
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 