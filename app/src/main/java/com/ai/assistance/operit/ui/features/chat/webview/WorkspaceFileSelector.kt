package com.ai.assistance.operit.ui.features.chat.webview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.GitIgnoreFilter
import com.ai.assistance.operit.util.FileUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class WorkspaceFileSuggestion(
    val file: File,
    val relativePath: String,
)

@Composable
fun WorkspaceFileSelector(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel,
    onFileSelected: (String) -> Unit,
    onShouldHide: () -> Unit, // 新增回调，当列表为空时通知父组件
    backgroundColor: Color? = null
) {
    val context = LocalContext.current
    val chatHistories by viewModel.chatHistories.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val searchQuery by viewModel.workspaceFileSearchQuery.collectAsState()
    val currentChat = chatHistories.find { it.id == currentChatId }
    val workspacePath = currentChat?.workspace

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = backgroundColor ?: MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        if (workspacePath != null) {
            val workspaceDir = File(workspacePath)
            if (workspaceDir.exists() && workspaceDir.isDirectory) {
                val filesState =
                    produceState<List<WorkspaceFileSuggestion>?>(
                        initialValue = null,
                        key1 = workspacePath,
                        key2 = searchQuery,
                    ) {
                        value =
                            withContext(Dispatchers.IO) {
                                loadWorkspaceFileSuggestions(workspaceDir, searchQuery)
                            }
                    }
                val files = filesState.value

                // 当筛选结果为空时，通知父组件隐藏
                LaunchedEffect(files, searchQuery) {
                    if (files != null && files.isEmpty() && searchQuery.isNotEmpty()) {
                        onShouldHide()
                    }
                }

                if (files == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (files.isNotEmpty()) {
                    Column {
                        // Header
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = context.getString(R.string.select_file_to_reference),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        // File List
                        LazyColumn(
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            items(files) { file ->
                                Column(modifier = Modifier.clickable { onFileSelected(file.file.absolutePath) }) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = "File",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = file.relativePath,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = Color.Gray.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(context.getString(R.string.workspace_directory_invalid))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(context.getString(R.string.chat_not_bound_to_workspace))
            }
        }
    }
}

private fun loadWorkspaceFileSuggestions(
    workspaceDir: File,
    searchQuery: String,
): List<WorkspaceFileSuggestion> {
    val normalizedQuery = searchQuery.trim()
    if (normalizedQuery.isEmpty()) return emptyList()

    val gitignoreRules = GitIgnoreFilter.loadRules(workspaceDir)
    return workspaceDir
        .walkTopDown()
        .onEnter { directory ->
            GitIgnoreFilter.shouldEnterDirectory(directory, workspaceDir, gitignoreRules)
        }
        .filter(File::isFile)
        .mapNotNull { file ->
            if (GitIgnoreFilter.shouldIgnore(file, workspaceDir, gitignoreRules)) {
                return@mapNotNull null
            }
            if (!FileUtils.isTextBasedFile(file)) {
                return@mapNotNull null
            }

            val relativePath = file.relativeTo(workspaceDir).path.replace(File.separatorChar, '/')
            if (!file.name.startsWith(normalizedQuery, ignoreCase = true)) {
                return@mapNotNull null
            }

            WorkspaceFileSuggestion(file = file, relativePath = relativePath)
        }
        .sortedBy { it.relativePath.lowercase() }
        .toList()
}
