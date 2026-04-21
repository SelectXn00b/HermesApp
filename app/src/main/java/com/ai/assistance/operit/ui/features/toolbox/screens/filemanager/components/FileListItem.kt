package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatDate
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.formatFileSize
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.utils.getFileIcon
import java.text.SimpleDateFormat
import java.util.*

enum class DisplayMode {
    SINGLE_COLUMN,
    TWO_COLUMNS,
    THREE_COLUMNS
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    itemSize: Float = 1f,
    displayMode: DisplayMode = DisplayMode.SINGLE_COLUMN
) {
    val baseHeight = 72.dp  // 所有模式使用相同的高度
    val baseIconSize = when (displayMode) {
        DisplayMode.SINGLE_COLUMN -> 40.dp
        DisplayMode.TWO_COLUMNS -> 36.dp
        DisplayMode.THREE_COLUMNS -> 32.dp
    }
    val basePadding = when (displayMode) {
        DisplayMode.SINGLE_COLUMN -> 12.dp
        DisplayMode.TWO_COLUMNS -> 8.dp
        DisplayMode.THREE_COLUMNS -> 6.dp
    }
    val baseSpacing = when (displayMode) {
        DisplayMode.SINGLE_COLUMN -> 8.dp
        DisplayMode.TWO_COLUMNS -> 6.dp
        DisplayMode.THREE_COLUMNS -> 4.dp
    }
    val baseTextSize = when (displayMode) {
        DisplayMode.SINGLE_COLUMN -> 16.sp
        DisplayMode.TWO_COLUMNS -> 14.sp
        DisplayMode.THREE_COLUMNS -> 12.sp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(baseHeight * itemSize)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        when (displayMode) {
            DisplayMode.SINGLE_COLUMN -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(basePadding * itemSize),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(baseIconSize * itemSize),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getFileIcon(file),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(baseIconSize * 0.6f * itemSize)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(baseSpacing * 1.5f * itemSize))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = baseTextSize * itemSize
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(baseSpacing * 0.5f * itemSize))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (file.isDirectory) stringResource(R.string.file_list_folder) else formatFileSize(file.size),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = baseTextSize * 0.85f * itemSize
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(baseSpacing * itemSize))

                            Text(
                                text = formatDate(file.lastModified),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = baseTextSize * 0.85f * itemSize
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            DisplayMode.TWO_COLUMNS, DisplayMode.THREE_COLUMNS -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(basePadding * itemSize),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(baseIconSize * itemSize),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getFileIcon(file),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(baseIconSize * 0.6f * itemSize)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(baseSpacing * 1.5f * itemSize))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = baseTextSize * itemSize
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(baseSpacing * 0.5f * itemSize))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (file.isDirectory) stringResource(R.string.file_list_folder) else formatFileSize(file.size),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = baseTextSize * 0.85f * itemSize
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(baseSpacing * itemSize))

                            Text(
                                text = formatDate(file.lastModified),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = baseTextSize * 0.85f * itemSize
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(timestamp))
}